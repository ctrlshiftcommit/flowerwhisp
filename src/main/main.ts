import { randomUUID } from 'node:crypto'
import { execFile, spawn, type ChildProcessByStdio } from 'node:child_process'
import { mkdir, readFile, unlink, writeFile } from 'node:fs/promises'
import path from 'node:path'
import type { Readable } from 'node:stream'
import { promisify } from 'node:util'
import { fileURLToPath } from 'node:url'
import ffmpegStaticPath from 'ffmpeg-static'

import {
  app,
  BrowserWindow,
  clipboard,
  dialog,
  globalShortcut,
  ipcMain,
  Menu,
  nativeImage,
  nativeTheme,
  screen,
  session,
  shell,
  Tray,
} from 'electron'

import type {
  AppEventChannel,
  BootstrapPayload,
  CommandResult,
  DictationMode,
  DictationPhase,
  OverlayState,
  PublicSettings,
} from '../shared/ipc'
import type { DictationRecord, DictionaryEntry, Snippet, TransformProfile } from '../shared/ipc'
import { isValidHoldShortcut, isValidShortcut } from '../shared/shortcuts'
import { DictationPipeline } from './services/pipeline'
import { captureInsertionTarget, copyForManualPaste, insertAtTarget, type InsertionTarget } from './services/insertion'
import { SecretStore } from './services/secrets'
import { JsonStateStore, type AppSnapshot } from './services/store'
import { countWords } from './domain'

const execFileAsync = promisify(execFile)

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rendererPath = path.join(__dirname, '../renderer/index.html')
const preloadPath = path.join(__dirname, '../preload/preload.cjs')
const productName = 'FlowerWhisp'
const appUserModelId = 'com.flowerwhisp.desktop'
const appIconPath = path.join(__dirname, '../../assets/flowerwhisp.png')
const isSmoke = process.env.FLOWERWHISP_SMOKE === '1'
const devUrl = process.env.FLOWERWHISP_DEV_URL

if (isSmoke) {
  app.setPath('userData', path.join(process.cwd(), 'artifacts', 'runtime-data'))
  // Keep this scoped to the smoke harness. The managed host's Chromium GPU
  // process is unavailable, so renderer evidence must use software rendering
  // without changing the normal app launch path.
  app.commandLine.appendSwitch('disable-gpu')
  app.disableHardwareAcceleration()
}
app.setName(productName)
app.setAppUserModelId(appUserModelId)

let mainWindow: BrowserWindow | null = null
let overlayWindow: BrowserWindow | null = null
let tray: Tray | null = null
let store: JsonStateStore
let secrets: SecretStore
let pipeline: DictationPipeline
let registeredShortcut = ''
let shortcutRegistered = false
let registeredHoldShortcut = ''
let holdShortcutRegistered = false
let shortcutRecording = false
let lastShortcutTriggerAt = 0
let shortcutStartInFlight = false
let shortcutStartMode: DictationMode | null = null
let stopRequestedWhileShortcutStarts = false
let promoteHoldToToggleWhileStarting = false
let pendingHoldReleaseTimer: NodeJS.Timeout | null = null
type ShortcutHookProcess = ChildProcessByStdio<null, Readable, Readable>

let shortcutHookProcess: ShortcutHookProcess | null = null
let shortcutHookShortcut = ''
let holdShortcutHookProcess: ShortcutHookProcess | null = null
let holdShortcutHookShortcut = ''
let shortcutRecordHookProcess: ShortcutHookProcess | null = null
let shortcutRegistrationError = 'That shortcut is unavailable or already claimed by another app.'
let holdShortcutRegistrationError = 'The hold shortcut could not be installed.'
let lastShortcutRegistrationError = ''
let shortcutSettingsBeforeRecording: Pick<PublicSettings, 'holdShortcut' | 'toggleShortcut'> | null = null
let shortcutInitialization: Promise<boolean> | null = null
let allowQuit = false
let pillEnabled = true
let recordingsDirectory = ''

type ActiveSession = {
  id: string
  mode: DictationMode
  startedAt: number
  phase: DictationPhase
  result: string
  recordId: string | null
  fallbackInsertionTarget: InsertionTarget | null
}

let activeSession: ActiveSession | null = null

const defaultOverlay = (): OverlayState => ({
  phase: 'idle',
  sessionId: null,
  mode: 'toggle',
  level: 0,
  elapsedMs: 0,
  message: 'Ready when you are.',
  transcript: '',
  result: '',
  error: null,
  provider: 'groq',
  cleanupLevel: 'light',
  copyAvailable: false,
})

let overlayState = defaultOverlay()
let elapsedTicker: NodeJS.Timeout | null = null

const makeTrayImage = () => {
  return nativeImage.createFromPath(appIconPath).resize({ width: 16, height: 16 })
}

const audioExtension = (mimeType: string): string => mimeType.includes('ogg') ? 'ogg' : 'webm'

const audioPathFor = (fileName: string): string => path.join(recordingsDirectory, path.basename(fileName))

const persistRecording = async (recordId: string, bytes: Uint8Array, mimeType: string, retention: PublicSettings['retention']): Promise<void> => {
  if (retention === 'never') return
  const fileName = `${recordId}.${audioExtension(mimeType)}`
  await mkdir(recordingsDirectory, { recursive: true })
  await writeFile(audioPathFor(fileName), bytes)
  await store.update((snapshot) => {
    const record = snapshot.records.find((candidate) => candidate.id === recordId)
    if (!record) return
    record.audioAvailable = true
    record.audioFileName = fileName
    record.audioMimeType = mimeType
  })
}

const ffmpegExecutable = (): string => {
  if (app.isPackaged) return path.join(path.dirname(app.getAppPath()), 'app.asar.unpacked', 'node_modules', 'ffmpeg-static', process.platform === 'win32' ? 'ffmpeg.exe' : 'ffmpeg')
  return ffmpegStaticPath || 'ffmpeg'
}

const isTrustedSender = (event: Electron.IpcMainInvokeEvent | Electron.IpcMainEvent): boolean => {
  const senderId = event.sender.id
  return senderId === mainWindow?.webContents.id || senderId === overlayWindow?.webContents.id
}

const result = (ok: boolean, message?: string, error?: string): CommandResult => ({ ok, message, error })

const send = (channel: AppEventChannel, payload: unknown): void => {
  if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send(channel, payload)
  if (overlayWindow && !overlayWindow.isDestroyed()) overlayWindow.webContents.send(channel, payload)
}

const stopElapsedTicker = (): void => {
  if (!elapsedTicker) return
  clearInterval(elapsedTicker)
  elapsedTicker = null
}

const startElapsedTicker = (): void => {
  stopElapsedTicker()
  elapsedTicker = setInterval(() => {
    const session = activeSession
    if (!session) {
      stopElapsedTicker()
      return
    }
    const elapsedMs = Date.now() - session.startedAt
    overlayState = { ...overlayState, elapsedMs }
    send('overlay:level', { sessionId: session.id, level: overlayState.level, elapsedMs })
  }, 100)
}

const publishOverlay = (patch: Partial<OverlayState>): void => {
  overlayState = { ...overlayState, ...patch }
  send('dictation:state', overlayState)
  send('overlay:state', overlayState)
  if (tray) tray.setToolTip(`FlowerWhisp — ${overlayState.phase === 'idle' ? 'Ready' : overlayState.phase}`)
}

const advance = (phase: DictationPhase, patch: Partial<OverlayState> = {}): void => {
  if (activeSession) activeSession.phase = phase
  publishOverlay({ phase, ...patch })
}

const showOverlay = (): void => {
  if (!overlayWindow || overlayWindow.isDestroyed()) return
  const display = screen.getDisplayNearestPoint(screen.getCursorScreenPoint())
  const bounds = display.workArea
  const { width, height } = overlayWindow.getBounds()
  overlayWindow.setPosition(
    Math.round(bounds.x + (bounds.width - width) / 2),
    Math.max(bounds.y + 12, bounds.y + bounds.height - height - 12),
  )
  overlayWindow.setAlwaysOnTop(true, 'screen-saver')
  overlayWindow.showInactive()
  overlayWindow.moveTop()
}

const hideOverlay = (delayMs = 0): void => {
  if (!overlayWindow || overlayWindow.isDestroyed()) return
  const reset = (): void => {
    const previous = overlayState
    publishOverlay({
      ...defaultOverlay(),
      provider: previous.provider,
      cleanupLevel: previous.cleanupLevel,
    })
    if (pillEnabled) showOverlay()
    else overlayWindow?.hide()
  }
  if (delayMs <= 0) reset()
  else setTimeout(reset, delayMs)
}

const windowBackgroundColor = (): string => nativeTheme.shouldUseDarkColors ? '#000000' : '#f4f0e9'

nativeTheme.on('updated', () => {
  if (nativeTheme.themeSource === 'system' && mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.setBackgroundColor(windowBackgroundColor())
  }
})

const loadRenderer = async (window: BrowserWindow, kind: 'main' | 'overlay'): Promise<void> => {
  if (devUrl) {
    await window.loadURL(`${devUrl}?window=${kind}${isSmoke ? '&smoke=1' : ''}`)
  } else {
    await window.loadFile(rendererPath, { query: { window: kind, ...(isSmoke ? { smoke: '1' } : {}) } })
  }
}

const createWindows = (): void => {
  mainWindow = new BrowserWindow({
    width: 1240,
    height: 820,
    minWidth: 1040,
    minHeight: 680,
    show: !isSmoke,
    title: 'Flow',
    icon: appIconPath,
    frame: false,
    backgroundColor: windowBackgroundColor(),
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })
  mainWindow.on('close', (event) => {
    if (!allowQuit && process.platform === 'win32') {
      event.preventDefault()
      mainWindow?.hide()
    }
  })
  mainWindow.on('closed', () => {
    mainWindow = null
  })
  mainWindow.webContents.on('before-input-event', (event, input) => {
    if (!shortcutRecording || !['keyDown', 'keyUp'].includes(input.type) || shortcutRecordHookProcess) return
    // Keep Windows and Copilot key presses out of the page and capture them
    // before Chromium turns Win into a shell action or drops the key name.
    event.preventDefault()
    if (input.isAutoRepeat) return
    send('shortcut:record', {
      type: input.type === 'keyUp' ? 'keyup' : 'keydown',
      key: input.key,
      code: input.code,
      ctrlKey: input.control,
      altKey: input.alt,
      shiftKey: input.shift,
      metaKey: input.meta,
    })
  })

  overlayWindow = new BrowserWindow({
    width: 150,
    height: 64,
    minWidth: 150,
    minHeight: 64,
    frame: false,
    transparent: true,
    resizable: false,
    show: false,
    skipTaskbar: true,
    alwaysOnTop: true,
    hasShadow: false,
    focusable: false,
    icon: appIconPath,
    backgroundColor: '#00000000',
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })
  overlayWindow.on('closed', () => {
    overlayWindow = null
  })
  overlayWindow.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true })

  // The real Flow indicator is mounted even while idle. Its renderer decides
  // whether that mount is a nearly invisible marker or an active control. Wait
  // for the native surface before showing a transparent always-on-top window.
  overlayWindow.once('ready-to-show', () => {
    if (pillEnabled) showOverlay()
  })

  void loadRenderer(mainWindow, 'main')
  void loadRenderer(overlayWindow, 'overlay')
}

const getSnapshot = async (): Promise<AppSnapshot> => store.load()

const retainedAudioPath = async (id: string): Promise<{ record: DictationRecord; filePath: string } | null> => {
  const snapshot = await getSnapshot()
  const record = snapshot.records.find((candidate) => candidate.id === id)
  if (!record?.audioAvailable || !record.audioFileName) return null
  return { record, filePath: audioPathFor(record.audioFileName) }
}

const buildBootstrap = async (): Promise<BootstrapPayload> => {
  const snapshot = await getSnapshot()
  return {
    settings: snapshot.settings,
    // Keep playback filenames inside the main process. The renderer only
    // needs the availability flag and asks the preload bridge for audio data.
    records: snapshot.records.map(({ audioFileName: _audioFileName, audioMimeType: _audioMimeType, ...record }) => record),
    dictionary: snapshot.dictionary,
    snippets: snapshot.snippets,
    styles: snapshot.styles,
    transforms: snapshot.transforms,
    usage: snapshot.usage,
    scratchpad: snapshot.scratchpad,
    hasGroqKey: await secrets.hasGroqKey(),
    holdShortcutRegistered,
    registeredHoldShortcut,
    shortcutRegistered,
    registeredShortcut,
    capabilities: {
      microphone: true,
      cloudTranscription: true,
      localTranscription: Boolean(snapshot.settings.localCommand),
      externalInsertion: true,
      appOwnedInsertion: true,
    },
    overlay: overlayState,
  }
}

const notifyBootstrapChanged = (): void => send('toast', { kind: 'refresh' })

const nativeWindowHandle = (window: BrowserWindow | null): string | null => {
  if (!window || window.isDestroyed() || process.platform !== 'win32') return null
  const handle = window.getNativeWindowHandle()
  if (handle.length >= 8) return handle.readBigUInt64LE(0).toString()
  if (handle.length >= 4) return handle.readUInt32LE(0).toString()
  return null
}

const captureExternalInsertionTarget = (): InsertionTarget | null => {
  const target = captureInsertionTarget()
  if (!target) return null
  const ownedHandles = new Set([nativeWindowHandle(mainWindow), nativeWindowHandle(overlayWindow)].filter((handle): handle is string => Boolean(handle)))
  return ownedHandles.has(target.handle) ? null : target
}

// Windows reserves Win+Space for keyboard-layout switching. Electron's
// globalShortcut correctly reports that accelerator as unavailable even when
// it is combined with Ctrl. Keep the normal native registration for ordinary
// accelerators, and use a small low-level hook for that reserved family and
// for an overlapping push-to-talk/hands-free pair. This keeps Ctrl+Win+Space
// real while allowing Ctrl+Win to remain its hold-to-dictate prefix.
const windowsShortcutHookScript = String.raw`
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.InteropServices;

public static class FlowerWhispKeyboardHook {
  private const int WH_KEYBOARD_LL = 13;
  private const int WM_KEYDOWN = 0x0100;
  private const int WM_KEYUP = 0x0101;
  private const int WM_SYSKEYDOWN = 0x0104;
  private const int WM_SYSKEYUP = 0x0105;
  private const uint VK_LCONTROL = 0xA2;
  private const uint VK_RCONTROL = 0xA3;
  private const uint VK_LSHIFT = 0xA0;
  private const uint VK_RSHIFT = 0xA1;
  private const uint VK_LMENU = 0xA4;
  private const uint VK_RMENU = 0xA5;
  private const uint VK_LWIN = 0x5B;
  private const uint VK_RWIN = 0x5C;

  [StructLayout(LayoutKind.Sequential)]
  private struct KBDLLHOOKSTRUCT {
    public uint vkCode;
    public uint scanCode;
    public uint flags;
    public uint time;
    public UIntPtr dwExtraInfo;
  }

  [StructLayout(LayoutKind.Sequential)]
  private struct POINT { public int x; public int y; }

  [StructLayout(LayoutKind.Sequential)]
  private struct MSG {
    public IntPtr hwnd;
    public uint message;
    public UIntPtr wParam;
    public IntPtr lParam;
    public uint time;
    public POINT point;
  }

  private delegate IntPtr LowLevelKeyboardProc(int nCode, IntPtr wParam, IntPtr lParam);

  [DllImport("user32.dll", SetLastError = true)]
  private static extern IntPtr SetWindowsHookEx(int idHook, LowLevelKeyboardProc callback, IntPtr module, uint threadId);

  [DllImport("user32.dll", SetLastError = true)]
  private static extern bool UnhookWindowsHookEx(IntPtr hook);

  [DllImport("user32.dll")]
  private static extern IntPtr CallNextHookEx(IntPtr hook, int nCode, IntPtr wParam, IntPtr lParam);

  [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
  private static extern IntPtr GetModuleHandle(string moduleName);

  [DllImport("user32.dll")]
  private static extern int GetMessage(out MSG message, IntPtr hwnd, uint min, uint max);

  [DllImport("user32.dll")]
  private static extern bool TranslateMessage(ref MSG message);

  [DllImport("user32.dll")]
  private static extern IntPtr DispatchMessage(ref MSG message);

  private static readonly HashSet<uint> Down = new HashSet<uint>();
  private static readonly HashSet<uint> Suppressed = new HashSet<uint>();
  private static LowLevelKeyboardProc callback;
  private static IntPtr hook;
  private static uint finalKey;
  private static bool wantControl;
  private static bool wantShift;
  private static bool wantAlt;
  private static bool wantWin;
  private static bool holdActive;

  private static uint KeyCode(string part) {
    if (part == "Space") return 0x20;
    if (part == "Tab") return 0x09;
    if (part == "Enter") return 0x0D;
    if (part == "Escape" || part == "Esc") return 0x1B;
    if (part == "Backspace") return 0x08;
    if (part == "Delete") return 0x2E;
    if (part == "Insert") return 0x2D;
    if (part == "Home") return 0x24;
    if (part == "End") return 0x23;
    if (part == "PageUp") return 0x21;
    if (part == "PageDown") return 0x22;
    if (part == "Up") return 0x26;
    if (part == "Down") return 0x28;
    if (part == "Left") return 0x25;
    if (part == "Right") return 0x27;
    if (part == "PrintScreen") return 0x2C;
    if (part == "VolumeUp") return 0xAF;
    if (part == "VolumeDown") return 0xAE;
    if (part == "VolumeMute") return 0xAD;
    if (part == "MediaPlayPause") return 0xB3;
    if (part == "MediaNextTrack") return 0xB0;
    if (part == "MediaPreviousTrack") return 0xB1;
    if (part == "MediaStop") return 0xB2;
    if (part.Length == 1 && ((part[0] >= 'A' && part[0] <= 'Z') || (part[0] >= '0' && part[0] <= '9'))) return part[0];
    int functionNumber;
    if (part.StartsWith("F", StringComparison.Ordinal) && Int32.TryParse(part.Substring(1), out functionNumber) && functionNumber >= 1 && functionNumber <= 24) return (uint)(0x6F + functionNumber);
    return 0;
  }

  private static bool IsDown(uint left, uint right) { return Down.Contains(left) || Down.Contains(right); }

  private static bool MatchesModifiers() {
    return wantControl == IsDown(VK_LCONTROL, VK_RCONTROL)
      && wantShift == IsDown(VK_LSHIFT, VK_RSHIFT)
      && wantAlt == IsDown(VK_LMENU, VK_RMENU)
      && wantWin == IsDown(VK_LWIN, VK_RWIN);
  }

  private static bool IsFinalKey(uint vkCode) {
    // Windows exposes the Copilot key as LaunchApplication1 even though the
    // renderer and Electron accelerator vocabulary call it F23.
    return vkCode == finalKey || (finalKey == 0x86 && vkCode == 0xB6);
  }

  private static bool FinalKeyDown() {
    return Down.Contains(finalKey) || (finalKey == 0x86 && Down.Contains(0xB6));
  }

  private static bool MatchesHoldChord() {
    return MatchesModifiers() && (finalKey == 0 || FinalKeyDown());
  }

  private static void WriteEvent(string value) {
    Console.WriteLine(value);
    Console.Out.Flush();
  }

  private static IntPtr OnKeyboardEvent(int code, IntPtr wParam, IntPtr lParam) {
    if (code >= 0 && (wParam.ToInt32() == WM_KEYDOWN || wParam.ToInt32() == WM_SYSKEYDOWN || wParam.ToInt32() == WM_KEYUP || wParam.ToInt32() == WM_SYSKEYUP)) {
      var data = (KBDLLHOOKSTRUCT)Marshal.PtrToStructure(lParam, typeof(KBDLLHOOKSTRUCT));
      var isDown = wParam.ToInt32() == WM_KEYDOWN || wParam.ToInt32() == WM_SYSKEYDOWN;
      var wasDown = Down.Contains(data.vkCode);
      if (isDown) Down.Add(data.vkCode); else Down.Remove(data.vkCode);
      if (isDown && !wasDown && IsFinalKey(data.vkCode) && MatchesModifiers()) {
        Console.WriteLine("TRIGGER");
        Console.Out.Flush();
        return (IntPtr)1;
      }
    }
    return CallNextHookEx(hook, code, wParam, lParam);
  }

  private static IntPtr OnHoldKeyboardEvent(int code, IntPtr wParam, IntPtr lParam) {
    if (code >= 0 && (wParam.ToInt32() == WM_KEYDOWN || wParam.ToInt32() == WM_SYSKEYDOWN || wParam.ToInt32() == WM_KEYUP || wParam.ToInt32() == WM_SYSKEYUP)) {
      var data = (KBDLLHOOKSTRUCT)Marshal.PtrToStructure(lParam, typeof(KBDLLHOOKSTRUCT));
      var isDown = wParam.ToInt32() == WM_KEYDOWN || wParam.ToInt32() == WM_SYSKEYDOWN;
      var wasDown = Down.Contains(data.vkCode);
      var wasHoldActive = holdActive;
      if (isDown) Down.Add(data.vkCode); else Down.Remove(data.vkCode);
      var chordDown = MatchesHoldChord();
      if (!holdActive && isDown && !wasDown && chordDown) {
        holdActive = true;
        WriteEvent("HOLD_DOWN");
        // If Win is the final modifier pressed for a modifier-only hold, own
        // both of its edges. That prevents the Start menu from opening when
        // Ctrl+Win is released, without swallowing an earlier standalone Win
        // down event that Windows has already received.
        if (finalKey == 0 && (data.vkCode == VK_LWIN || data.vkCode == VK_RWIN)) Suppressed.Add(data.vkCode);
      } else if (holdActive && !chordDown) {
        holdActive = false;
        WriteEvent("HOLD_UP");
      }

      // A non-modifier hold key is application-owned while the gesture is
      // active. For modifier-only holds, suppress only an activation-edge Win
      // key whose matching down event was also swallowed above.
      if (finalKey != 0 && IsFinalKey(data.vkCode) && (wasHoldActive || holdActive)) return (IntPtr)1;
      if (Suppressed.Contains(data.vkCode)) {
        if (!isDown) Suppressed.Remove(data.vkCode);
        return (IntPtr)1;
      }
    }
    return CallNextHookEx(hook, code, wParam, lParam);
  }

  private static string KeyName(uint vkCode) {
    if (vkCode == 0xA0 || vkCode == 0xA1) return "Shift";
    if (vkCode == VK_LCONTROL || vkCode == VK_RCONTROL) return "Control";
    if (vkCode == VK_LMENU || vkCode == VK_RMENU) return "Alt";
    if (vkCode == VK_LWIN || vkCode == VK_RWIN) return "Meta";
    if (vkCode == 0x20) return " ";
    if (vkCode == 0x09) return "Tab";
    if (vkCode == 0x0D) return "Enter";
    if (vkCode == 0x1B) return "Escape";
    if (vkCode == 0x08) return "Backspace";
    if (vkCode == 0x2E) return "Delete";
    if (vkCode == 0x2D) return "Insert";
    if (vkCode == 0x24) return "Home";
    if (vkCode == 0x23) return "End";
    if (vkCode == 0x21) return "PageUp";
    if (vkCode == 0x22) return "PageDown";
    if (vkCode == 0x26) return "ArrowUp";
    if (vkCode == 0x28) return "ArrowDown";
    if (vkCode == 0x25) return "ArrowLeft";
    if (vkCode == 0x27) return "ArrowRight";
    if (vkCode == 0x2C) return "PrintScreen";
    if (vkCode == 0xAF) return "VolumeUp";
    if (vkCode == 0xAE) return "VolumeDown";
    if (vkCode == 0xAD) return "VolumeMute";
    if (vkCode == 0xB3) return "MediaPlayPause";
    if (vkCode == 0xB0) return "MediaNextTrack";
    if (vkCode == 0xB1) return "MediaPreviousTrack";
    if (vkCode == 0xB2) return "MediaStop";
    if (vkCode >= 0x41 && vkCode <= 0x5A) return ((char)vkCode).ToString();
    if (vkCode >= 0x30 && vkCode <= 0x39) return ((char)vkCode).ToString();
    if (vkCode >= 0x70 && vkCode <= 0x87) return "F" + (vkCode - 0x6F).ToString();
    if (vkCode == 0xB6) return "LaunchApplication1";
    return "";
  }

  private static string KeyCodeName(uint vkCode) {
    if (vkCode == VK_LCONTROL) return "ControlLeft";
    if (vkCode == VK_RCONTROL) return "ControlRight";
    if (vkCode == VK_LSHIFT) return "ShiftLeft";
    if (vkCode == VK_RSHIFT) return "ShiftRight";
    if (vkCode == VK_LMENU) return "AltLeft";
    if (vkCode == VK_RMENU) return "AltRight";
    if (vkCode == VK_LWIN) return "MetaLeft";
    if (vkCode == VK_RWIN) return "MetaRight";
    if (vkCode == 0x20) return "Space";
    if (vkCode == 0xB6) return "LaunchApp1";
    if (vkCode >= 0x41 && vkCode <= 0x5A) return "Key" + ((char)vkCode).ToString();
    if (vkCode >= 0x30 && vkCode <= 0x39) return "Digit" + ((char)vkCode).ToString();
    return "";
  }

  private static IntPtr OnRecordingKeyboardEvent(int code, IntPtr wParam, IntPtr lParam) {
    if (code >= 0 && (wParam.ToInt32() == WM_KEYDOWN || wParam.ToInt32() == WM_SYSKEYDOWN || wParam.ToInt32() == WM_KEYUP || wParam.ToInt32() == WM_SYSKEYUP)) {
      var data = (KBDLLHOOKSTRUCT)Marshal.PtrToStructure(lParam, typeof(KBDLLHOOKSTRUCT));
      var isDown = wParam.ToInt32() == WM_KEYDOWN || wParam.ToInt32() == WM_SYSKEYDOWN;
      var wasDown = Down.Contains(data.vkCode);
      var changed = (isDown && !wasDown) || (!isDown && wasDown);
      if (isDown) Down.Add(data.vkCode);
      if (changed) {
        var key = KeyName(data.vkCode);
        if (key.Length > 0) {
          var control = IsDown(VK_LCONTROL, VK_RCONTROL);
          var shift = IsDown(VK_LSHIFT, VK_RSHIFT);
          var alt = IsDown(VK_LMENU, VK_RMENU);
          var win = IsDown(VK_LWIN, VK_RWIN);
          WriteEvent((isDown ? "KEYDOWN|" : "KEYUP|") + key + "|" + KeyCodeName(data.vkCode) + "|" + (control ? "1" : "0") + "|" + (alt ? "1" : "0") + "|" + (shift ? "1" : "0") + "|" + (win ? "1" : "0") + "|0");
        }
      }
      if (!isDown) Down.Remove(data.vkCode);
    }
    return CallNextHookEx(hook, code, wParam, lParam);
  }

  public static void Record() {
    Down.Clear();
    callback = OnRecordingKeyboardEvent;
    hook = SetWindowsHookEx(WH_KEYBOARD_LL, callback, GetModuleHandle(null), 0);
    if (hook == IntPtr.Zero) {
      Console.WriteLine("ERROR");
      Console.Out.Flush();
      return;
    }
    Console.WriteLine("READY");
    Console.Out.Flush();
    MSG message;
    while (GetMessage(out message, IntPtr.Zero, 0, 0) > 0) {
      TranslateMessage(ref message);
      DispatchMessage(ref message);
    }
    UnhookWindowsHookEx(hook);
  }

  private static bool ConfigureShortcut(string shortcut) {
    finalKey = 0;
    wantControl = false;
    wantShift = false;
    wantAlt = false;
    wantWin = false;
    foreach (var part in shortcut.Split(new[] {'+'}, StringSplitOptions.RemoveEmptyEntries)) {
      if (part == "Control") wantControl = true;
      else if (part == "Shift") wantShift = true;
      else if (part == "Alt") wantAlt = true;
      else if (part == "Super") wantWin = true;
      else {
        if (finalKey != 0) return false;
        finalKey = KeyCode(part);
        if (finalKey == 0) return false;
      }
    }
    return finalKey != 0 || wantControl || wantShift || wantAlt || wantWin;
  }

  public static void Run(string shortcut) {
    if (!ConfigureShortcut(shortcut) || finalKey == 0 || (!wantControl && !wantShift && !wantAlt && !wantWin)) {
      WriteEvent("ERROR|CONFIGURE|" + shortcut);
      return;
    }
    callback = OnKeyboardEvent;
    hook = SetWindowsHookEx(WH_KEYBOARD_LL, callback, GetModuleHandle(null), 0);
    if (hook == IntPtr.Zero) {
      WriteEvent("ERROR|HOOK|" + Marshal.GetLastWin32Error().ToString());
      return;
    }
    Console.WriteLine("READY");
    Console.Out.Flush();
    MSG message;
    while (GetMessage(out message, IntPtr.Zero, 0, 0) > 0) {
      TranslateMessage(ref message);
      DispatchMessage(ref message);
    }
    UnhookWindowsHookEx(hook);
  }

  public static void RunHold(string shortcut) {
    if (!ConfigureShortcut(shortcut)) {
      WriteEvent("ERROR|CONFIGURE|" + shortcut);
      return;
    }
    Down.Clear();
    Suppressed.Clear();
    holdActive = false;
    callback = OnHoldKeyboardEvent;
    hook = SetWindowsHookEx(WH_KEYBOARD_LL, callback, GetModuleHandle(null), 0);
    if (hook == IntPtr.Zero) {
      WriteEvent("ERROR|HOOK|" + Marshal.GetLastWin32Error().ToString());
      return;
    }
    WriteEvent("READY");
    MSG message;
    while (GetMessage(out message, IntPtr.Zero, 0, 0) > 0) {
      TranslateMessage(ref message);
      DispatchMessage(ref message);
    }
    UnhookWindowsHookEx(hook);
  }
}
'@
if ($env:FLOWERWHISP_RECORDING -eq '1') {
  [FlowerWhispKeyboardHook]::Record()
} elseif ($env:FLOWERWHISP_HOLD -eq '1') {
  [FlowerWhispKeyboardHook]::RunHold($env:FLOWERWHISP_SHORTCUT)
} else {
  [FlowerWhispKeyboardHook]::Run($env:FLOWERWHISP_SHORTCUT)
}
`

const isWindowsSpaceShortcut = (shortcut: string): boolean => {
  const parts = new Set(shortcut.split('+').filter(Boolean))
  return parts.has('Super') && parts.has('Space')
}

const terminateShortcutHookProcess = (child: ShortcutHookProcess | null): void => {
  if (!child) return
  if (!child.killed) child.kill()
  // PowerShell hosts the managed low-level hook and can outlive Node's
  // child handle on Windows. Terminate only this exact helper process tree so
  // recording mode never leaves a stale hook claiming the user's shortcut.
  if (process.platform === 'win32' && child.pid) {
    void execFile('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true }, () => undefined)
  }
}

const stopWindowsShortcutHook = (): void => {
  const child = shortcutHookProcess
  shortcutHookProcess = null
  shortcutHookShortcut = ''
  terminateShortcutHookProcess(child)
}

const stopWindowsHoldShortcutHook = (): void => {
  const child = holdShortcutHookProcess
  holdShortcutHookProcess = null
  holdShortcutHookShortcut = ''
  terminateShortcutHookProcess(child)
}

const stopWindowsShortcutRecorder = (): void => {
  const child = shortcutRecordHookProcess
  shortcutRecordHookProcess = null
  terminateShortcutHookProcess(child)
}

const startWindowsShortcutRecorder = (): Promise<boolean> => {
  if (process.platform !== 'win32') return Promise.resolve(false)
  stopWindowsShortcutRecorder()
  const child = spawn(
    'powershell.exe',
    ['-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-Command', windowsShortcutHookScript],
    {
      windowsHide: true,
      env: { ...process.env, FLOWERWHISP_RECORDING: '1' },
      stdio: ['ignore', 'pipe', 'pipe'],
    },
  ) as ShortcutHookProcess
  shortcutRecordHookProcess = child
  let buffer = ''
  let ready = false
  let settled = false
  let timeout: NodeJS.Timeout | null = null
  const clearWait = (): void => {
    if (timeout) clearTimeout(timeout)
    timeout = null
  }
  const fail = (): void => {
    if (settled) return
    settled = true
    clearWait()
    if (shortcutRecordHookProcess === child) shortcutRecordHookProcess = null
    terminateShortcutHookProcess(child)
  }
  child.stdout.on('data', (chunk: Buffer | string) => {
    buffer += chunk.toString()
    const lines = buffer.split(/\r?\n/)
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      if (line === 'READY' && !ready) {
        ready = true
        settled = true
        clearWait()
        continue
      }
      if (line.startsWith('ERROR')) {
        console.error(`[shortcut-recorder] native helper reported ${line}`)
        fail()
        continue
      }
      const type = line.startsWith('KEYDOWN|') ? 'keydown' : line.startsWith('KEYUP|') ? 'keyup' : null
      if (!type || shortcutRecordHookProcess !== child) continue
      const [, key, code, control, alt, shift, win, repeat] = line.split('|')
      if (!key) continue
      send('shortcut:record', {
        type,
        key,
        code: code || undefined,
        ctrlKey: control === '1',
        altKey: alt === '1',
        shiftKey: shift === '1',
        metaKey: win === '1',
        repeat: repeat === '1',
      })
    }
  })
  child.stderr.on('data', (chunk: Buffer | string) => console.error(`[shortcut-recorder] ${chunk.toString().trim()}`))
  child.once('error', () => fail())
  child.once('close', () => {
    if (!ready) fail()
    if (shortcutRecordHookProcess === child) shortcutRecordHookProcess = null
  })
  timeout = setTimeout(() => fail(), 3_000)
  return new Promise((resolve) => {
    const check = (): void => {
      if (ready) resolve(true)
      else if (!shortcutRecordHookProcess || shortcutRecordHookProcess !== child) resolve(false)
      else setTimeout(check, 25)
    }
    check()
  })
}

const startWindowsShortcutHook = (shortcut: string): Promise<boolean> => {
  if (process.platform !== 'win32' || !isValidShortcut(shortcut)) return Promise.resolve(false)
  stopWindowsShortcutHook()
  const child = spawn(
    'powershell.exe',
    ['-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-Command', windowsShortcutHookScript],
    {
      windowsHide: true,
      env: { ...process.env, FLOWERWHISP_SHORTCUT: shortcut },
      stdio: ['ignore', 'pipe', 'pipe'],
    },
  ) as ShortcutHookProcess
  shortcutHookProcess = child
  shortcutHookShortcut = shortcut
  let buffer = ''
  let ready = false
  let settled = false
  let timeout: NodeJS.Timeout | null = null
  const clearWait = (): void => {
    if (timeout) clearTimeout(timeout)
    timeout = null
  }
  const fail = (): void => {
    if (settled) return
    settled = true
    clearWait()
    if (shortcutHookProcess === child) {
      shortcutHookProcess = null
      shortcutHookShortcut = ''
    }
    terminateShortcutHookProcess(child)
  }
  child.stdout.on('data', (chunk: Buffer | string) => {
    buffer += chunk.toString()
    const lines = buffer.split(/\r?\n/)
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      if (line === 'READY' && !ready) {
        ready = true
        settled = true
        clearWait()
      } else if (line === 'TRIGGER') {
        shortcutHandler()
      } else if (line.startsWith('ERROR')) {
        console.error(`[hands-free-shortcut] native helper reported ${line}`)
        fail()
      }
    }
  })
  child.stderr.on('data', (chunk: Buffer | string) => console.error(`[hands-free-shortcut] ${chunk.toString().trim()}`))
  child.once('error', () => fail())
  child.once('close', () => {
    if (!ready) fail()
    if (shortcutHookProcess === child) {
      shortcutHookProcess = null
      shortcutHookShortcut = ''
      if (registeredShortcut === shortcut) {
        registeredShortcut = ''
        shortcutRegistered = false
        send('toast', { kind: 'shortcut-unavailable', shortcut, error: 'The Windows keyboard hook stopped.' })
      }
    }
  })
  timeout = setTimeout(() => fail(), 3_000)
  return new Promise((resolve) => {
    const check = (): void => {
      if (ready) resolve(true)
      else if (!shortcutHookProcess || shortcutHookProcess !== child) resolve(false)
      else setTimeout(check, 25)
    }
    check()
  })
}

const startWindowsHoldShortcutHook = (shortcut: string): Promise<boolean> => {
  if (process.platform !== 'win32' || !isValidHoldShortcut(shortcut)) return Promise.resolve(false)
  stopWindowsHoldShortcutHook()
  const child = spawn(
    'powershell.exe',
    ['-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-Command', windowsShortcutHookScript],
    {
      windowsHide: true,
      env: { ...process.env, FLOWERWHISP_HOLD: '1', FLOWERWHISP_SHORTCUT: shortcut },
      stdio: ['ignore', 'pipe', 'pipe'],
    },
  ) as ShortcutHookProcess
  holdShortcutHookProcess = child
  holdShortcutHookShortcut = shortcut
  let buffer = ''
  let ready = false
  let settled = false
  let timeout: NodeJS.Timeout | null = null
  const clearWait = (): void => {
    if (timeout) clearTimeout(timeout)
    timeout = null
  }
  const fail = (): void => {
    if (settled) return
    settled = true
    clearWait()
    if (holdShortcutHookProcess === child) {
      holdShortcutHookProcess = null
      holdShortcutHookShortcut = ''
    }
    terminateShortcutHookProcess(child)
  }
  child.stdout.on('data', (chunk: Buffer | string) => {
    buffer += chunk.toString()
    const lines = buffer.split(/\r?\n/)
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      if (line === 'READY' && !ready) {
        ready = true
        settled = true
        clearWait()
      } else if (line === 'HOLD_DOWN') {
        holdShortcutPressed()
      } else if (line === 'HOLD_UP') {
        holdShortcutReleased()
      } else if (line.startsWith('ERROR')) {
        console.error(`[push-to-talk-shortcut] native helper reported ${line}`)
        fail()
      }
    }
  })
  child.stderr.on('data', (chunk: Buffer | string) => console.error(`[push-to-talk-shortcut] ${chunk.toString().trim()}`))
  child.once('error', () => fail())
  child.once('close', () => {
    if (!ready) fail()
    if (holdShortcutHookProcess === child) {
      holdShortcutHookProcess = null
      holdShortcutHookShortcut = ''
      if (registeredHoldShortcut === shortcut) {
        registeredHoldShortcut = ''
        holdShortcutRegistered = false
        send('toast', { kind: 'shortcut-unavailable', shortcut, error: 'The Windows hold-to-dictate hook stopped.' })
        send('toast', { kind: 'refresh' })
      }
    }
  })
  timeout = setTimeout(() => fail(), 3_000)
  return new Promise((resolve) => {
    const check = (): void => {
      if (ready) resolve(true)
      else if (!holdShortcutHookProcess || holdShortcutHookProcess !== child) resolve(false)
      else setTimeout(check, 25)
    }
    check()
  })
}

const holdShortcutPressed = (): void => {
  // A second press during the brief release grace period is Flow's
  // double-tap-to-lock gesture. Keep the existing recorder and promote it to
  // hands-free instead of tearing it down and starting another one.
  if (pendingHoldReleaseTimer) {
    clearTimeout(pendingHoldReleaseTimer)
    pendingHoldReleaseTimer = null
    if (shortcutStartInFlight && shortcutStartMode === 'hold') {
      promoteHoldToToggleWhileStarting = true
      stopRequestedWhileShortcutStarts = false
      return
    }
    if (activeSession?.mode === 'hold' && (activeSession.phase === 'starting' || activeSession.phase === 'recording')) {
      activeSession.mode = 'toggle'
      publishOverlay({ mode: 'toggle', message: 'Hands-free dictation active. Press the shortcut again to finish.' })
      return
    }
  }
  if (shortcutStartInFlight) return
  if (!activeSession || ['ready', 'success', 'error', 'cancelled'].includes(activeSession.phase)) {
    void startSession('hold')
  }
}

const holdShortcutReleased = (): void => {
  // Keep release slightly deferred so Ctrl+Win+Space can promote the prefix
  // capture even if stdout from the two Windows hook processes arrives a few
  // milliseconds out of order. The same grace period enables double-tap hold
  // to lock into hands-free mode, matching Flow's interaction model.
  if (promoteHoldToToggleWhileStarting || activeSession?.mode === 'toggle') return
  if (pendingHoldReleaseTimer) clearTimeout(pendingHoldReleaseTimer)
  pendingHoldReleaseTimer = setTimeout(() => {
    pendingHoldReleaseTimer = null
    if (promoteHoldToToggleWhileStarting || activeSession?.mode === 'toggle') return
    if (shortcutStartInFlight && shortcutStartMode === 'hold') {
      stopRequestedWhileShortcutStarts = true
      return
    }
    if (activeSession?.mode === 'hold' && (activeSession.phase === 'starting' || activeSession.phase === 'recording')) {
      void stopSession()
    }
  }, 180)
}

const shortcutHandler = (): void => {
  // Windows can deliver more than one accelerator event while a key chord is
  // being released. Debounce that edge so one physical press never starts and
  // immediately stops the same dictation.
  const now = Date.now()
  if (now - lastShortcutTriggerAt < 300) return
  lastShortcutTriggerAt = now

  // Wispr's Windows defaults deliberately overlap: Ctrl+Win is push-to-talk
  // and Ctrl+Win+Space is hands-free. The prefix starts one recorder; Space
  // locks that recorder into toggle mode instead of stopping it or opening a
  // second session. This also makes any configured toggle chord a reliable
  // way to promote an in-progress hold capture.
  if (shortcutStartInFlight && shortcutStartMode === 'hold') {
    if (pendingHoldReleaseTimer) clearTimeout(pendingHoldReleaseTimer)
    pendingHoldReleaseTimer = null
    promoteHoldToToggleWhileStarting = true
    stopRequestedWhileShortcutStarts = false
    if (activeSession?.mode === 'hold') {
      activeSession.mode = 'toggle'
      publishOverlay({ mode: 'toggle', message: 'Hands-free dictation active. Press the shortcut again to finish.' })
    }
    return
  }
  if (activeSession?.mode === 'hold' && (activeSession.phase === 'starting' || activeSession.phase === 'recording')) {
    if (pendingHoldReleaseTimer) clearTimeout(pendingHoldReleaseTimer)
    pendingHoldReleaseTimer = null
    activeSession.mode = 'toggle'
    publishOverlay({ mode: 'toggle', message: 'Hands-free dictation active. Press the shortcut again to finish.' })
    return
  }

  if (!activeSession || ['ready', 'success', 'error', 'cancelled'].includes(activeSession.phase)) {
    if (shortcutStartInFlight) {
      stopRequestedWhileShortcutStarts = true
      return
    }
    void startSession('toggle')
    return
  }

  if (activeSession.phase === 'starting' || activeSession.phase === 'recording') void stopSession()
}

type DictationShortcutSettings = Pick<PublicSettings, 'holdShortcut' | 'toggleShortcut'>

const shortcutSignature = (shortcut: string): string => shortcut.split('+').filter(Boolean).sort().join('+')

const shortcutIsStrictSubset = (candidate: string, complete: string): boolean => {
  const candidateParts = new Set(candidate.split('+').filter(Boolean))
  const completeParts = new Set(complete.split('+').filter(Boolean))
  return candidateParts.size < completeParts.size && [...candidateParts].every((part) => completeParts.has(part))
}

const unregisterShortcuts = (): void => {
  if (pendingHoldReleaseTimer) clearTimeout(pendingHoldReleaseTimer)
  pendingHoldReleaseTimer = null
  if (registeredShortcut) globalShortcut.unregister(registeredShortcut)
  stopWindowsShortcutHook()
  stopWindowsHoldShortcutHook()
  registeredShortcut = ''
  shortcutRegistered = false
  registeredHoldShortcut = ''
  holdShortcutRegistered = false
}

const attemptRegisterToggleShortcut = async (shortcut: string, preferWindowsHook = false): Promise<boolean> => {
  if (!isValidShortcut(shortcut)) return false
  shortcutRegistrationError = 'That shortcut is unavailable or already claimed by another app.'
  // When push-to-talk is a prefix of hands-free (Ctrl+Win versus
  // Ctrl+Win+Space), install this hook after the hold hook. Windows calls the
  // newest low-level hook first, so it can observe the complete chord before
  // the hold hook swallows Win to keep the Start menu closed.
  if (preferWindowsHook && await startWindowsShortcutHook(shortcut)) {
    registeredShortcut = shortcut
    shortcutRegistered = true
    return true
  }
  try {
    const registered = globalShortcut.register(shortcut, shortcutHandler)
    if (registered) {
      registeredShortcut = shortcut
      shortcutRegistered = true
      return true
    }
  } catch {
    // Electron reports conflicts and unsupported OS accelerators as a simple
    // false result, but some platform builds throw instead.
  }
  if (isWindowsSpaceShortcut(shortcut) && await startWindowsShortcutHook(shortcut)) {
    registeredShortcut = shortcut
    shortcutRegistered = true
    return true
  }
  if (isWindowsSpaceShortcut(shortcut)) shortcutRegistrationError = 'Windows reserves Win+Space, so FlowerWhisp could not install the low-level hook for this combination.'
  return false
}

const attemptRegisterHoldShortcut = async (shortcut: string): Promise<boolean> => {
  if (!isValidHoldShortcut(shortcut)) return false
  holdShortcutRegistrationError = process.platform === 'win32'
    ? 'FlowerWhisp could not install the Windows key-down/key-up hook for this hold combination.'
    : 'Hold-to-dictate currently requires the Windows native key-up hook.'
  if (await startWindowsHoldShortcutHook(shortcut)) {
    registeredHoldShortcut = shortcut
    holdShortcutRegistered = true
    return true
  }
  return false
}

const registerShortcuts = async (requested?: DictationShortcutSettings, announce = true): Promise<boolean> => {
  if (shortcutRecording) return false
  const snapshot = requested ?? (await getSnapshot()).settings
  const requestedToggleShortcut = snapshot.toggleShortcut
  const requestedHoldShortcut = snapshot.holdShortcut
  unregisterShortcuts()
  lastShortcutRegistrationError = ''

  if (shortcutSignature(requestedToggleShortcut) === shortcutSignature(requestedHoldShortcut)) {
    shortcutRegistrationError = 'Hold to dictate and toggle dictation must use different combinations.'
    holdShortcutRegistrationError = shortcutRegistrationError
    lastShortcutRegistrationError = shortcutRegistrationError
    if (announce) send('toast', { kind: 'shortcut-unavailable', shortcut: requestedToggleShortcut, error: shortcutRegistrationError })
    send('toast', { kind: 'refresh' })
    return false
  }

  const holdReady = await attemptRegisterHoldShortcut(requestedHoldShortcut)
  if (!holdReady) {
    lastShortcutRegistrationError = holdShortcutRegistrationError
    if (announce) send('toast', { kind: 'shortcut-unavailable', shortcut: requestedHoldShortcut, error: holdShortcutRegistrationError })
    send('toast', { kind: 'refresh' })
    return false
  }

  const toggleReady = await attemptRegisterToggleShortcut(
    requestedToggleShortcut,
    process.platform === 'win32' && shortcutIsStrictSubset(requestedHoldShortcut, requestedToggleShortcut),
  )
  if (!toggleReady) {
    lastShortcutRegistrationError = shortcutRegistrationError
    unregisterShortcuts()
    if (announce) send('toast', { kind: 'shortcut-unavailable', shortcut: requestedToggleShortcut, error: shortcutRegistrationError })
    send('toast', { kind: 'refresh' })
    return false
  }

  if (announce) send('toast', { kind: 'shortcuts-ready' })
  // Settings must refresh after the native registration attempt so the
  // two displayed chords and their active registrations cannot drift apart.
  send('toast', { kind: 'refresh' })
  console.info(`[shortcut] toggle=${registeredShortcut || 'none'} hold=${registeredHoldShortcut || 'none'} registered=true`)
  return true
}

const setShortcutRecording = async (recording: boolean): Promise<CommandResult> => {
  if (recording) {
    if (shortcutRecording) return result(false, undefined, 'Another shortcut field is already listening.')
    const settings = (await getSnapshot()).settings
    shortcutSettingsBeforeRecording = {
      holdShortcut: settings.holdShortcut,
      toggleShortcut: settings.toggleShortcut,
    }
    shortcutRecording = true
    unregisterShortcuts()
    const nativeRecorderReady = await startWindowsShortcutRecorder()
    if (process.platform === 'win32' && !nativeRecorderReady) {
      shortcutRecording = false
      const previous = shortcutSettingsBeforeRecording
      shortcutSettingsBeforeRecording = null
      if (previous) await registerShortcuts(previous, false)
      return result(false, undefined, 'FlowerWhisp could not start the Windows shortcut recorder. The existing shortcuts remain active.')
    }
    return result(true, 'Shortcut recording is ready.')
  }
  if (!shortcutRecording) return result(true, 'Shortcuts are already active.')
  shortcutRecording = false
  stopWindowsShortcutRecorder()
  const desired = (await getSnapshot()).settings
  if (await registerShortcuts(desired)) {
    shortcutSettingsBeforeRecording = null
    return result(true, 'Push-to-talk and hands-free shortcuts are active.')
  }

  const rejectedError = lastShortcutRegistrationError || 'The new shortcut could not be activated.'
  const previous = shortcutSettingsBeforeRecording
  shortcutSettingsBeforeRecording = null
  if (previous) {
    await store.update((snapshot) => {
      snapshot.settings.holdShortcut = previous.holdShortcut
      snapshot.settings.toggleShortcut = previous.toggleShortcut
    })
    await registerShortcuts(previous, false)
    send('toast', { kind: 'refresh' })
  }
  return result(false, undefined, `${rejectedError} The previous shortcuts were restored.`)
}

const startSession = async (mode: DictationMode, fallbackInsertionTarget?: InsertionTarget | null): Promise<CommandResult> => {
  if (activeSession && ['ready', 'success', 'error', 'cancelled'].includes(activeSession.phase)) {
    stopElapsedTicker()
    activeSession = null
  }
  if (activeSession) return result(false, undefined, 'A dictation is already in progress.')
  if (shortcutStartInFlight) return result(false, undefined, 'The microphone is still starting.')
  shortcutStartInFlight = true
  shortcutStartMode = mode
  if (mode === 'hold') promoteHoldToToggleWhileStarting = false
  const insertionTarget = fallbackInsertionTarget === undefined ? captureExternalInsertionTarget() : fallbackInsertionTarget
  try {
    const settings = (await getSnapshot()).settings
    const resolvedMode: DictationMode = mode === 'hold' && promoteHoldToToggleWhileStarting ? 'toggle' : mode
    promoteHoldToToggleWhileStarting = false
    activeSession = { id: randomUUID(), mode: resolvedMode, startedAt: Date.now(), phase: 'starting', result: '', recordId: null, fallbackInsertionTarget: insertionTarget }
  publishOverlay({
    phase: 'starting',
    sessionId: activeSession.id,
    mode: resolvedMode,
    level: 0,
    elapsedMs: 0,
    message: 'Starting microphone…',
    transcript: '',
    result: '',
    error: null,
    provider: settings.transcriptionProvider,
    cleanupLevel: settings.cleanupLevel,
    copyAvailable: false,
  })
  startElapsedTicker()
  if (settings.showPill) showOverlay()
  mainWindow?.webContents.send('recording:start', { sessionId: activeSession.id, mode: resolvedMode })
    advance('recording', { message: resolvedMode === 'hold' ? 'Keep holding the shortcut. Release it to finish.' : 'Hands-free dictation active. Press the shortcut again to finish.' })
    if (stopRequestedWhileShortcutStarts) {
      stopRequestedWhileShortcutStarts = false
      void stopSession()
    }
    return result(true)
  } finally {
    shortcutStartInFlight = false
    shortcutStartMode = null
    if (!activeSession) {
      stopRequestedWhileShortcutStarts = false
      promoteHoldToToggleWhileStarting = false
    }
  }
}

const stopSession = async (): Promise<CommandResult> => {
  if (!activeSession) return result(false, undefined, 'There is no active dictation.')
  if (['stopping', 'transcribing', 'processing', 'inserting'].includes(activeSession.phase)) return result(true, 'Dictation is already finishing.')
  const sessionId = activeSession.id
  advance('stopping', { message: 'Finishing audio capture…' })
  mainWindow?.webContents.send('recording:stop', { sessionId })
  return result(true)
}

const cancelSession = async (): Promise<CommandResult> => {
  if (!activeSession) return result(false, undefined, 'There is no active dictation.')
  const sessionId = activeSession.id
  mainWindow?.webContents.send('recording:cancel', { sessionId })
  stopElapsedTicker()
  activeSession = null
  publishOverlay({ ...defaultOverlay(), phase: 'cancelled', message: 'Dictation cancelled.' })
  hideOverlay(450)
  return result(true)
}

const handleAudio = async (payload: { sessionId: string; dataUrl: string; mimeType: string; durationMs: number }): Promise<CommandResult> => {
  if (!activeSession || payload.sessionId !== activeSession.id) return result(false, undefined, 'This recording session is no longer active.')
  if (!payload.dataUrl.startsWith('data:') || payload.dataUrl.length > 20_000_000) {
    return result(false, undefined, 'The captured audio was invalid or too large.')
  }
  advance('transcribing', { message: 'Transcribing locally in the selected provider…', elapsedMs: Date.now() - activeSession.startedAt })
  const base64 = payload.dataUrl.split(',')[1] ?? ''
  try {
    const bytes = Uint8Array.from(Buffer.from(base64, 'base64'))
    const settings = (await getSnapshot()).settings
    advance('processing', { message: settings.cleanupLevel === 'none' ? 'Applying dictionary corrections…' : 'Applying the selected cleanup…' })
    const processed = await pipeline.run({ audio: { bytes, mimeType: payload.mimeType, durationMs: payload.durationMs }, settings })
    try {
      await persistRecording(processed.record.id, bytes, payload.mimeType, settings.retention)
    } catch (error) {
      // Playback is an enhancement; a disk failure must not discard a valid transcript.
      console.warn('FlowerWhisp could not retain the source recording for playback', error)
    }
    const startedAt = activeSession.startedAt
    // Capture the active application immediately before insertion. This is
    // the user's requested behavior when they move to another field/app
    // while transcription is running. If a focus transition briefly leaves
    // Flow or the desktop in the foreground, retain the external target from
    // the moment dictation started instead of silently falling back to copy.
    const readyInsertionTarget = captureExternalInsertionTarget()
    const insertionTarget = readyInsertionTarget ?? activeSession.fallbackInsertionTarget
    console.info(`[insertion] readyTarget=${readyInsertionTarget?.handle ?? 'none'} fallbackTarget=${activeSession.fallbackInsertionTarget?.handle ?? 'none'} selectedTarget=${insertionTarget?.handle ?? 'none'}`)
    advance('inserting', { message: 'Inserting transcript into the active application…' })
    // Resolve the destination only when the transcript is ready, preferring
    // the current foreground app over the start-time fallback.
    const insertion = insertAtTarget(processed.finalText, insertionTarget)
    await store.update((snapshot) => {
      const record = snapshot.records.find((candidate) => candidate.id === processed.record.id)
      if (record) record.insertionOutcome = insertion.outcome
    })
    notifyBootstrapChanged()
    // Both successful native insertion and clipboard-only fallback are a
    // completed dictation. Keep the transcript copied in the background, do
    // not expose a manual Copy button in the pill, and return it to idle.
    stopElapsedTicker()
    if (insertion.outcome !== 'inserted') {
      console.info(`[insertion] automatic paste unavailable; transcript remains on the clipboard outcome=${insertion.outcome}`)
    }
    activeSession = null
    publishOverlay({
      phase: 'success',
      message: insertion.message,
      transcript: processed.rawText,
      result: processed.finalText,
      copyAvailable: false,
      elapsedMs: Date.now() - startedAt,
    })
    hideOverlay(1_200)
    return result(true, insertion.message)
  } catch (error) {
    stopElapsedTicker()
    const message = error instanceof Error ? error.message : 'The dictation could not be processed.'
    advance('error', { message: 'The safe capture was not inserted.', error: message, copyAvailable: false })
    return result(false, undefined, message)
  }
}

const copyResult = async (text: string): Promise<CommandResult> => {
  try {
    const copied = copyForManualPaste(text)
    const recordId = activeSession?.recordId
    if (recordId) {
      await store.update((snapshot) => {
        const record = snapshot.records.find((candidate) => candidate.id === recordId)
        if (record) record.insertionOutcome = 'copied'
      })
      notifyBootstrapChanged()
    }
    stopElapsedTicker()
    activeSession = null
    publishOverlay({ phase: 'success', message: copied.message, copyAvailable: false })
    hideOverlay(1200)
    return result(true, copied.message)
  } catch (error) {
    return result(false, undefined, error instanceof Error ? error.message : 'Copy failed.')
  }
}

const sendToScratchpad = async (text: string): Promise<CommandResult> => {
  const normalized = text.trim()
  if (!normalized) return result(false, undefined, 'There is no transcript to send.')
  const recordId = activeSession?.recordId
  await store.update((snapshot) => {
    snapshot.scratchpad = snapshot.scratchpad ? `${snapshot.scratchpad.trim()}\n\n${normalized}` : normalized
    if (recordId) {
      const record = snapshot.records.find((candidate) => candidate.id === recordId)
      if (record) record.insertionOutcome = 'scratchpad'
    }
  })
  stopElapsedTicker()
  activeSession = null
  publishOverlay({ phase: 'success', message: 'Added to Scratchpad.', copyAvailable: false })
  hideOverlay(900)
  send('toast', { kind: 'scratchpad-updated' })
  notifyBootstrapChanged()
  return result(true, 'Added to Scratchpad.')
}

const validateText = (value: unknown, max = 50_000): value is string => typeof value === 'string' && value.length <= max

const applySystemSettings = (settings: PublicSettings): void => {
  mainWindow?.setSkipTaskbar(!settings.showInDock)
  if (process.platform !== 'win32') return
  const loginArgs = app.isPackaged ? [app.getAppPath()] : [path.resolve(process.argv[1] ?? app.getAppPath())]
  app.setLoginItemSettings({
    openAtLogin: settings.launchAtLogin,
    path: process.execPath,
    args: loginArgs,
  })
}

const saveSettings = async (patch: Partial<PublicSettings>): Promise<CommandResult> => {
  if (patch.toggleShortcut !== undefined && !isValidShortcut(patch.toggleShortcut)) return result(false, undefined, 'Use at least one modifier and one final key, for example Control+Super+Space or Control+Shift+Tab.')
  if (patch.holdShortcut !== undefined && !isValidHoldShortcut(patch.holdShortcut)) return result(false, undefined, 'Use one or more modifiers, a modifier plus a final key, or a function key for hold to dictate.')
  if (patch.cleanupPrompts !== undefined) {
    for (const level of ['none', 'light', 'medium'] as const) {
      const prompt = patch.cleanupPrompts[level]
      if (!validateText(prompt, 8_000) || !prompt.trim()) return result(false, undefined, 'Each cleanup prompt needs non-empty instructions.')
    }
  }
  if (patch.theme !== undefined && !['light', 'dark', 'system'].includes(patch.theme)) return result(false, undefined, 'Choose light, dark, or system appearance.')
  const previous = await getSnapshot()
  const previousShortcuts: DictationShortcutSettings = {
    holdShortcut: previous.settings.holdShortcut,
    toggleShortcut: previous.settings.toggleShortcut,
  }
  const candidateShortcuts: DictationShortcutSettings = {
    holdShortcut: patch.holdShortcut ?? previousShortcuts.holdShortcut,
    toggleShortcut: patch.toggleShortcut ?? previousShortcuts.toggleShortcut,
  }
  if (shortcutSignature(candidateShortcuts.holdShortcut) === shortcutSignature(candidateShortcuts.toggleShortcut)) {
    return result(false, undefined, 'Hold to dictate and toggle dictation must use different combinations.')
  }
  const shortcutsChanged = candidateShortcuts.holdShortcut !== previousShortcuts.holdShortcut
    || candidateShortcuts.toggleShortcut !== previousShortcuts.toggleShortcut
  if (shortcutsChanged && !shortcutRecording && !(await registerShortcuts(candidateShortcuts, false))) {
    const registrationError = lastShortcutRegistrationError || 'The new shortcut could not be activated.'
    await registerShortcuts(previousShortcuts, false)
    return result(false, undefined, registrationError)
  }
  let updated: AppSnapshot
  try {
    updated = await store.update((snapshot) => {
      snapshot.settings = { ...snapshot.settings, ...patch }
    })
  } catch {
    if (shortcutsChanged && !shortcutRecording) await registerShortcuts(previousShortcuts, false)
    return result(false, undefined, 'FlowerWhisp could not save the settings file. The previous shortcuts remain active.')
  }
  if (patch.theme !== undefined) {
    nativeTheme.themeSource = patch.theme
    mainWindow?.setBackgroundColor(windowBackgroundColor())
  }
  if (patch.showPill !== undefined) {
    pillEnabled = patch.showPill
    if (pillEnabled) showOverlay()
    else overlayWindow?.hide()
  }
  if (patch.launchAtLogin !== undefined || patch.showInDock !== undefined) applySystemSettings(updated.settings)
  notifyBootstrapChanged()
  return result(true, 'Settings saved.')
}

const saveDictionary = async (entry: Omit<DictionaryEntry, 'id' | 'createdAt'> & { id?: string }): Promise<CommandResult> => {
  if (!validateText(entry.spoken, 200) || !validateText(entry.replacement, 400) || !entry.spoken.trim() || !entry.replacement.trim()) {
    return result(false, undefined, 'Add both a spoken phrase and a replacement.')
  }
  await store.update((snapshot) => {
    const next: DictionaryEntry = {
      id: entry.id || randomUUID(),
      spoken: entry.spoken.trim(),
      replacement: entry.replacement.trim(),
      scope: entry.scope,
      protected: Boolean(entry.protected),
      createdAt: new Date().toISOString(),
    }
    const index = snapshot.dictionary.findIndex((candidate) => candidate.id === next.id)
    if (index >= 0) snapshot.dictionary[index] = next
    else snapshot.dictionary.unshift(next)
  })
  notifyBootstrapChanged()
  return result(true, 'Dictionary saved.')
}

const saveSnippet = async (snippet: Omit<Snippet, 'id' | 'createdAt'> & { id?: string }): Promise<CommandResult> => {
  if (!validateText(snippet.trigger, 80) || !validateText(snippet.expansion, 5_000) || !snippet.trigger.trim() || !snippet.expansion.trim()) {
    return result(false, undefined, 'Add a trigger and an expansion.')
  }
  await store.update((snapshot) => {
    const next: Snippet = { ...snippet, id: snippet.id || randomUUID(), trigger: snippet.trigger.trim(), createdAt: new Date().toISOString() }
    const index = snapshot.snippets.findIndex((candidate) => candidate.id === next.id)
    if (index >= 0) snapshot.snippets[index] = next
    else snapshot.snippets.unshift(next)
  })
  notifyBootstrapChanged()
  return result(true, 'Snippet saved.')
}

const saveTransform = async (transform: Omit<TransformProfile, 'builtIn'> & { builtIn?: boolean }): Promise<CommandResult> => {
  if (!validateText(transform.name, 120) || !validateText(transform.instructions, 5_000) || !transform.name.trim() || !transform.instructions.trim()) {
    return result(false, undefined, 'Add a transform name and instructions.')
  }
  await store.update((snapshot) => {
    const next: TransformProfile = { ...transform, builtIn: Boolean(transform.builtIn) }
    const index = snapshot.transforms.findIndex((candidate) => candidate.id === next.id)
    if (index >= 0) snapshot.transforms[index] = next
    else snapshot.transforms.unshift(next)
  })
  notifyBootstrapChanged()
  return result(true, 'Transform saved.')
}

const registerIpc = (): void => {
  ipcMain.handle('app:bootstrap', async (event) => (isTrustedSender(event) ? buildBootstrap() : null))
  ipcMain.handle('app:health', async (event) => {
    const trusted = isTrustedSender(event)
    // The smoke renderer can load before initialization has finished awaiting
    // the native helper processes. Runtime evidence must observe the settled
    // registration result, not that harmless startup race.
    if (isSmoke && shortcutInitialization) await shortcutInitialization
    const health = {
      appName: app.getName(),
      packaged: app.isPackaged,
      rendererLoaded: trusted,
      preloadBridge: trusted,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      shortcuts: {
        pushToTalkRegistered: holdShortcutRegistered,
        pushToTalk: registeredHoldShortcut,
        handsFreeRegistered: shortcutRegistered,
        handsFree: registeredShortcut,
      },
    }
    if (isSmoke && trusted) {
      const evidencePath = path.join(process.cwd(), 'artifacts', 'electron-smoke', 'evidence.json')
      await mkdir(path.dirname(evidencePath), { recursive: true })
      await writeFile(evidencePath, JSON.stringify({ ...health, timestamp: new Date().toISOString() }, null, 2), 'utf8')
      setTimeout(() => {
        allowQuit = true
        app.quit()
      }, 150)
    }
    return health
  })
  ipcMain.handle('app:quit', (event) => {
    if (!isTrustedSender(event)) return
    allowQuit = true
    app.quit()
  })
  ipcMain.handle('window:minimize', (event) => {
    if (!isTrustedSender(event)) return
    BrowserWindow.fromWebContents(event.sender)?.minimize()
  })
  ipcMain.handle('window:toggle-maximize', (event) => {
    if (!isTrustedSender(event)) return
    const window = BrowserWindow.fromWebContents(event.sender)
    if (!window) return
    if (window.isMaximized()) window.unmaximize()
    else window.maximize()
  })
  ipcMain.handle('window:close', (event) => {
    if (!isTrustedSender(event)) return
    const window = BrowserWindow.fromWebContents(event.sender)
    if (!window || window !== mainWindow) return
    allowQuit = true
    window.close()
  })
  ipcMain.handle('dictation:start', (event, options?: { mode?: DictationMode }) => (isTrustedSender(event) ? startSession(options?.mode ?? 'toggle') : result(false, undefined, 'Unauthorized request.')))
  ipcMain.handle('dictation:stop', (event) => (isTrustedSender(event) ? stopSession() : result(false, undefined, 'Unauthorized request.')))
  ipcMain.handle('dictation:cancel', (event) => (isTrustedSender(event) ? cancelSession() : result(false, undefined, 'Unauthorized request.')))
  ipcMain.handle('dictation:copy', (event, text: unknown) => (isTrustedSender(event) && validateText(text) ? copyResult(text) : result(false, undefined, 'Invalid copy request.')))
  ipcMain.handle('dictation:scratchpad', (event, text: unknown) => (isTrustedSender(event) && validateText(text) ? sendToScratchpad(text) : result(false, undefined, 'Invalid Scratchpad request.')))
  ipcMain.handle('audio:submit', (event, payload: unknown) => {
    if (!isTrustedSender(event) || !payload || typeof payload !== 'object') return result(false, undefined, 'Invalid audio request.')
    const candidate = payload as Record<string, unknown>
    if (!validateText(candidate.sessionId, 100) || !validateText(candidate.dataUrl, 20_000_000) || !validateText(candidate.mimeType, 100) || typeof candidate.durationMs !== 'number') return result(false, undefined, 'Invalid audio request.')
    return handleAudio({ sessionId: candidate.sessionId, dataUrl: candidate.dataUrl, mimeType: candidate.mimeType, durationMs: Math.max(0, candidate.durationMs) })
  })
  ipcMain.on('audio:level', (event, payload: unknown) => {
    if (!isTrustedSender(event) || !payload || typeof payload !== 'object') return
    const candidate = payload as Record<string, unknown>
    if (typeof candidate.sessionId !== 'string' || typeof candidate.level !== 'number') return
    const level = Math.max(0, Math.min(1, candidate.level))
    const elapsedMs = activeSession ? Date.now() - activeSession.startedAt : overlayState.elapsedMs
    overlayState = { ...overlayState, level, elapsedMs }
    send('overlay:level', { sessionId: candidate.sessionId, level, elapsedMs })
  })
  ipcMain.on('audio:error', (event, payload: unknown) => {
    if (!isTrustedSender(event) || !payload || typeof payload !== 'object') return
    const candidate = payload as Record<string, unknown>
    if (typeof candidate.sessionId !== 'string' || typeof candidate.message !== 'string') return
    if (activeSession?.id !== candidate.sessionId) return
    stopElapsedTicker()
    activeSession = null
    publishOverlay({ phase: 'error', message: 'Microphone unavailable.', error: candidate.message, copyAvailable: false })
    hideOverlay(1500)
  })
  ipcMain.handle('settings:save', (event, patch: unknown) => (isTrustedSender(event) && patch && typeof patch === 'object' ? saveSettings(patch as Partial<PublicSettings>) : result(false, undefined, 'Invalid settings.')))
  ipcMain.handle('settings:shortcut-recording', (event, recording: unknown) => (isTrustedSender(event) && typeof recording === 'boolean' ? setShortcutRecording(recording) : result(false, undefined, 'Invalid shortcut recording state.')))
  ipcMain.handle('settings:set-key', async (event, value: unknown) => {
    if (!isTrustedSender(event) || !validateText(value, 500)) return result(false, undefined, 'Invalid API key.')
    try {
      await secrets.setGroqKey(value)
      notifyBootstrapChanged()
      return result(true, 'Groq API key saved securely.')
    } catch (error) {
      return result(false, undefined, error instanceof Error ? error.message : 'Secure storage is unavailable.')
    }
  })
  ipcMain.handle('settings:clear-key', async (event) => {
    if (!isTrustedSender(event)) return result(false, undefined, 'Unauthorized request.')
    await secrets.clearGroqKey()
    notifyBootstrapChanged()
    return result(true, 'Groq API key removed.')
  })
  ipcMain.handle('history:delete', async (event, id: unknown) => {
    if (!isTrustedSender(event) || typeof id !== 'string') return result(false, undefined, 'Invalid history entry.')
    const snapshot = await getSnapshot()
    const record = snapshot.records.find((candidate) => candidate.id === id)
    await store.update((snapshot) => (snapshot.records = snapshot.records.filter((record) => record.id !== id)))
    if (record?.audioFileName) {
      await unlink(audioPathFor(record.audioFileName)).catch(() => undefined)
    }
    notifyBootstrapChanged()
    return result(true, 'Dictation deleted.')
  })
  ipcMain.handle('history:copy', async (event, id: unknown) => {
    if (!isTrustedSender(event) || typeof id !== 'string') return result(false, undefined, 'Invalid history entry.')
    const snapshot = await getSnapshot()
    const record = snapshot.records.find((candidate) => candidate.id === id)
    return record ? copyResult(record.finalText) : result(false, undefined, 'Dictation not found.')
  })
  ipcMain.handle('history:audio', async (event, id: unknown) => {
    if (!isTrustedSender(event) || typeof id !== 'string') return result(false, undefined, 'Invalid history entry.')
    const retained = await retainedAudioPath(id)
    if (!retained) return result(false, undefined, 'No recording was retained for this transcript.')
    try {
      const bytes = await readFile(retained.filePath)
      const mimeType = retained.record.audioMimeType || (retained.record.audioFileName?.endsWith('.ogg') ? 'audio/ogg' : 'audio/webm')
      return { ok: true, mimeType, dataUrl: `data:${mimeType};base64,${bytes.toString('base64')}` }
    } catch {
      return result(false, undefined, 'The retained recording is no longer available.')
    }
  })
  ipcMain.handle('history:play', async (event, id: unknown) => {
    if (!isTrustedSender(event) || typeof id !== 'string') return result(false, undefined, 'Invalid history entry.')
    const retained = await retainedAudioPath(id)
    if (!retained) return result(false, undefined, 'No recording was retained for this transcript.')
    const openError = await shell.openPath(retained.filePath)
    return openError ? result(false, undefined, openError) : result(true, 'Playing recording.')
  })
  ipcMain.handle('history:undo', async (event, id: unknown) => {
    if (!isTrustedSender(event) || typeof id !== 'string') return result(false, undefined, 'Invalid history entry.')
    const snapshot = await getSnapshot()
    const record = snapshot.records.find((candidate) => candidate.id === id)
    if (!record) return result(false, undefined, 'Dictation not found.')
    const restoredText = record.cleanedText || record.rawText || record.finalText
    if (!restoredText) return result(false, undefined, 'The retained transcript text is unavailable.')
    await store.update((current) => {
      const currentRecord = current.records.find((candidate) => candidate.id === id)
      if (!currentRecord) return
      currentRecord.finalText = restoredText
      currentRecord.wordCount = countWords(restoredText)
      currentRecord.aiFixCount = 0
    })
    notifyBootstrapChanged()
    return result(true, record.finalText === restoredText ? 'This transcript is already using the retained text.' : 'AI edits were undone.')
  })
  ipcMain.handle('history:retry', async (event, id: unknown) => {
    if (!isTrustedSender(event) || typeof id !== 'string') return result(false, undefined, 'Invalid history entry.')
    const retained = await retainedAudioPath(id)
    if (!retained) return result(false, undefined, 'Retry is unavailable because this transcript has no retained recording.')
    try {
      const settings = (await getSnapshot()).settings
      const bytes = await readFile(retained.filePath)
      const processed = await pipeline.run({ audio: { bytes, mimeType: retained.record.audioMimeType || 'audio/webm', durationMs: retained.record.durationMs }, settings })
      await persistRecording(processed.record.id, bytes, retained.record.audioMimeType || 'audio/webm', settings.retention)
      notifyBootstrapChanged()
      return result(true, 'Retried the recording and added a new transcript.')
    } catch (error) {
      return result(false, undefined, error instanceof Error ? error.message : 'The transcript could not be retried.')
    }
  })
  ipcMain.handle('history:extract', async (event, id: unknown) => {
    if (!isTrustedSender(event) || typeof id !== 'string') return result(false, undefined, 'Invalid history entry.')
    const retained = await retainedAudioPath(id)
    if (!retained) return result(false, undefined, 'No recording was retained for this transcript.')
    const saveDialog = await dialog.showSaveDialog({
      title: 'Extract audio as FLAC',
      defaultPath: path.join(app.getPath('downloads'), `${retained.record.id}.flac`),
      filters: [{ name: 'FLAC audio', extensions: ['flac'] }],
    })
    if (saveDialog.canceled || !saveDialog.filePath) return result(false, undefined, 'Audio extraction canceled.')
    try {
      await execFileAsync(ffmpegExecutable(), ['-hide_banner', '-loglevel', 'error', '-y', '-i', retained.filePath, '-vn', '-c:a', 'flac', saveDialog.filePath])
      return result(true, `Extracted FLAC audio to ${path.basename(saveDialog.filePath)}.`)
    } catch (error) {
      return result(false, undefined, error instanceof Error ? error.message : 'The recording could not be converted to FLAC.')
    }
  })
  ipcMain.handle('dictionary:save', (event, entry) => (isTrustedSender(event) ? saveDictionary(entry as Omit<DictionaryEntry, 'id' | 'createdAt'> & { id?: string }) : result(false, undefined, 'Unauthorized request.')))
  ipcMain.handle('dictionary:delete', async (event, id: unknown) => {
    if (!isTrustedSender(event) || typeof id !== 'string') return result(false, undefined, 'Invalid dictionary entry.')
    await store.update((snapshot) => (snapshot.dictionary = snapshot.dictionary.filter((entry) => entry.id !== id)))
    notifyBootstrapChanged()
    return result(true)
  })
  ipcMain.handle('snippets:save', (event, snippet) => (isTrustedSender(event) ? saveSnippet(snippet as Omit<Snippet, 'id' | 'createdAt'> & { id?: string }) : result(false, undefined, 'Unauthorized request.')))
  ipcMain.handle('snippets:delete', async (event, id: unknown) => {
    if (!isTrustedSender(event) || typeof id !== 'string') return result(false, undefined, 'Invalid snippet.')
    await store.update((snapshot) => (snapshot.snippets = snapshot.snippets.filter((snippet) => snippet.id !== id)))
    notifyBootstrapChanged()
    return result(true)
  })
  ipcMain.handle('transforms:save', (event, transform) => (isTrustedSender(event) ? saveTransform(transform as Omit<TransformProfile, 'builtIn'> & { builtIn?: boolean }) : result(false, undefined, 'Unauthorized request.')))
  ipcMain.handle('transforms:delete', async (event, id: unknown) => {
    if (!isTrustedSender(event) || typeof id !== 'string') return result(false, undefined, 'Invalid transform.')
    await store.update((snapshot) => (snapshot.transforms = snapshot.transforms.filter((transform) => transform.id !== id || transform.builtIn)))
    notifyBootstrapChanged()
    return result(true)
  })
  ipcMain.handle('scratchpad:read', async (event) => (isTrustedSender(event) ? (await getSnapshot()).scratchpad : ''))
  ipcMain.handle('scratchpad:save', async (event, value: unknown) => {
    if (!isTrustedSender(event) || !validateText(value, 100_000)) return result(false, undefined, 'Invalid Scratchpad content.')
    await store.update((snapshot) => (snapshot.scratchpad = value))
    return result(true, 'Scratchpad saved.')
  })
}

const createTray = (): void => {
  tray = new Tray(makeTrayImage())
  tray.setToolTip('FlowerWhisp — Ready')
  tray.setContextMenu(
    Menu.buildFromTemplate([
      { label: 'Open FlowerWhisp', click: () => mainWindow?.show() },
      { type: 'separator' },
      { label: 'Start dictation', click: () => void startSession('toggle') },
      { label: 'Stop dictation', click: () => void stopSession(), enabled: Boolean(activeSession) },
      { label: 'Cancel current dictation', click: () => void cancelSession(), enabled: Boolean(activeSession) },
      { type: 'separator' },
      { label: 'Settings', click: () => { mainWindow?.show(); mainWindow?.webContents.send('toast', { kind: 'navigate', page: 'settings' }) } },
      { label: 'Quit FlowerWhisp', click: () => { allowQuit = true; app.quit() } },
    ]),
  )
  tray.on('double-click', () => mainWindow?.show())
}

const setupPermissions = (): void => {
  session.defaultSession.setPermissionRequestHandler((webContents, permission, callback) => {
    const trusted = webContents.id === mainWindow?.webContents.id || webContents.id === overlayWindow?.webContents.id
    callback(trusted && permission === 'media')
  })
  session.defaultSession.setPermissionCheckHandler((webContents, permission) => {
    if (!webContents) return false
    const trusted = webContents.id === mainWindow?.webContents.id || webContents.id === overlayWindow?.webContents.id
    return trusted && permission === 'media'
  })
}

const initialize = async (): Promise<void> => {
  const root = path.join(app.getPath('userData'), 'state')
  recordingsDirectory = path.join(app.getPath('userData'), 'recordings')
  store = new JsonStateStore(path.join(root, 'flowerwhisp.json'))
  secrets = new SecretStore(path.join(app.getPath('userData'), 'secrets'))
  await store.load()
  pillEnabled = (await getSnapshot()).settings.showPill
  pipeline = new DictationPipeline(store, secrets)
  nativeTheme.themeSource = (await getSnapshot()).settings.theme
  createWindows()
  applySystemSettings((await getSnapshot()).settings)
  registerIpc()
  createTray()
  setupPermissions()
  shortcutInitialization = registerShortcuts()
  await shortcutInitialization
  if (!isSmoke) mainWindow?.show()
}

const gotLock = app.requestSingleInstanceLock()
if (!gotLock) {
  app.quit()
} else {
  app.on('second-instance', () => mainWindow?.show())
  app.whenReady().then(initialize).catch((error) => {
    console.error('FlowerWhisp startup failed', error)
    app.quit()
  })
  app.on('will-quit', () => {
    stopElapsedTicker()
    stopWindowsShortcutHook()
    stopWindowsHoldShortcutHook()
    stopWindowsShortcutRecorder()
    globalShortcut.unregisterAll()
    tray?.destroy()
  })
  app.on('window-all-closed', () => {
    // The main window hides to the tray on close. Keep the process alive for the tray and shortcut.
  })
}
