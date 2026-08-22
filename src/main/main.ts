import { randomUUID } from 'node:crypto'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import {
  app,
  BrowserWindow,
  clipboard,
  globalShortcut,
  ipcMain,
  Menu,
  nativeImage,
  nativeTheme,
  screen,
  session,
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
import { isValidShortcut } from '../shared/shortcuts'
import { DictationPipeline } from './services/pipeline'
import { captureInsertionTarget, copyForManualPaste, insertAtTarget, type InsertionTarget } from './services/insertion'
import { SecretStore } from './services/secrets'
import { JsonStateStore, type AppSnapshot } from './services/store'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rendererPath = path.join(__dirname, '../renderer/index.html')
const preloadPath = path.join(__dirname, '../preload/preload.cjs')
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
app.setName('FlowerWhisp')

let mainWindow: BrowserWindow | null = null
let overlayWindow: BrowserWindow | null = null
let tray: Tray | null = null
let store: JsonStateStore
let secrets: SecretStore
let pipeline: DictationPipeline
let registeredShortcut = ''
let shortcutRegistered = false
let shortcutRecording = false
let allowQuit = false
let pillEnabled = true

type ActiveSession = {
  id: string
  mode: DictationMode
  startedAt: number
  phase: DictationPhase
  result: string
  recordId: string | null
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

const traySvg = `<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32"><rect x="2" y="2" width="28" height="28" rx="9" fill="#1f1d1a"/><path d="M10 16c3-5 5-5 6 0s3 5 6 0" fill="none" stroke="#d6c7ff" stroke-width="2.2" stroke-linecap="round"/></svg>`

const makeTrayImage = () => {
  const data = `data:image/svg+xml;base64,${Buffer.from(traySvg).toString('base64')}`
  return nativeImage.createFromDataURL(data).resize({ width: 16, height: 16 })
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
  overlayWindow.showInactive()
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

const windowBackgroundColor = (): string => nativeTheme.shouldUseDarkColors ? '#1f1d1b' : '#f4f0e9'

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

const buildBootstrap = async (): Promise<BootstrapPayload> => {
  const snapshot = await getSnapshot()
  return {
    settings: snapshot.settings,
    records: snapshot.records,
    dictionary: snapshot.dictionary,
    snippets: snapshot.snippets,
    styles: snapshot.styles,
    transforms: snapshot.transforms,
    usage: snapshot.usage,
    scratchpad: snapshot.scratchpad,
    hasGroqKey: await secrets.hasGroqKey(),
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

const shortcutHandler = (): void => {
  if (activeSession) void stopSession()
  else void startSession('toggle')
}

const unregisterShortcut = (): void => {
  if (registeredShortcut) globalShortcut.unregister(registeredShortcut)
  registeredShortcut = ''
  shortcutRegistered = false
}

const attemptRegisterShortcut = (shortcut: string): boolean => {
  if (!isValidShortcut(shortcut)) return false
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
  return false
}

const registerShortcut = async (): Promise<boolean> => {
  if (shortcutRecording) return false
  const snapshot = await getSnapshot()
  const requestedShortcut = snapshot.settings.toggleShortcut
  const fallbackShortcut = 'Control+Alt+Space'
  unregisterShortcut()
  let registered = attemptRegisterShortcut(requestedShortcut)
  if (!registered && requestedShortcut !== fallbackShortcut) registered = attemptRegisterShortcut(fallbackShortcut)
  if (!registered) registeredShortcut = ''
  send('toast', {
    kind: registered ? 'shortcut-ready' : 'shortcut-unavailable',
    shortcut: registered ? registeredShortcut : requestedShortcut,
  })
  console.info(`[shortcut] requested=${requestedShortcut} active=${registeredShortcut || 'none'} registered=${registered}`)
  return registered
}

const setShortcutRecording = async (recording: boolean): Promise<CommandResult> => {
  shortcutRecording = recording
  if (recording) {
    unregisterShortcut()
    return result(true, 'Shortcut recording is ready.')
  }
  await registerShortcut()
  return shortcutRegistered
    ? result(true, 'Shortcut restored.')
    : result(false, undefined, `Could not restore ${registeredShortcut || 'the shortcut'}.`)
}

const startSession = async (mode: DictationMode): Promise<CommandResult> => {
  if (activeSession && ['ready', 'success', 'error', 'cancelled'].includes(activeSession.phase)) activeSession = null
  if (activeSession) return result(false, undefined, 'A dictation is already in progress.')
  const settings = (await getSnapshot()).settings
  activeSession = { id: randomUUID(), mode, startedAt: Date.now(), phase: 'starting', result: '', recordId: null }
  publishOverlay({
    phase: 'starting',
    sessionId: activeSession.id,
    mode,
    level: 0,
    elapsedMs: 0,
    message: mode === 'hold' ? 'Hold mode is available inside FlowerWhisp.' : 'Starting microphone…',
    transcript: '',
    result: '',
    error: null,
    provider: settings.transcriptionProvider,
    cleanupLevel: settings.cleanupLevel,
    copyAvailable: false,
  })
  if (settings.showPill) showOverlay()
  mainWindow?.webContents.send('recording:start', { sessionId: activeSession.id, mode })
  advance('recording', { message: mode === 'hold' ? 'Hold mode is local to the app without a native key-up hook.' : 'Speak naturally, then press the shortcut again.' })
  return result(true)
}

const stopSession = async (): Promise<CommandResult> => {
  if (!activeSession) return result(false, undefined, 'There is no active dictation.')
  const sessionId = activeSession.id
  advance('stopping', { message: 'Finishing audio capture…' })
  mainWindow?.webContents.send('recording:stop', { sessionId })
  return result(true)
}

const cancelSession = async (): Promise<CommandResult> => {
  if (!activeSession) return result(false, undefined, 'There is no active dictation.')
  const sessionId = activeSession.id
  mainWindow?.webContents.send('recording:cancel', { sessionId })
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
    const startedAt = activeSession.startedAt
    advance('inserting', { message: 'Inserting transcript into the active application…' })
    // Resolve the destination only when the transcript is ready. The user may
    // have moved the cursor or switched apps while transcription was running.
    const insertion = insertAtTarget(processed.finalText, captureExternalInsertionTarget())
    await store.update((snapshot) => {
      const record = snapshot.records.find((candidate) => candidate.id === processed.record.id)
      if (record) record.insertionOutcome = insertion.outcome
    })
    notifyBootstrapChanged()
    activeSession.result = processed.finalText
    activeSession.recordId = processed.record.id
    if (insertion.outcome === 'inserted') {
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
    }
    advance('ready', {
      message: insertion.message,
      transcript: processed.rawText,
      result: processed.finalText,
      copyAvailable: true,
      elapsedMs: Date.now() - activeSession.startedAt,
    })
    return result(true, insertion.message)
  } catch (error) {
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
  activeSession = null
  publishOverlay({ phase: 'success', message: 'Added to Scratchpad.', copyAvailable: false })
  hideOverlay(900)
  send('toast', { kind: 'scratchpad-updated' })
  notifyBootstrapChanged()
  return result(true, 'Added to Scratchpad.')
}

const validateText = (value: unknown, max = 50_000): value is string => typeof value === 'string' && value.length <= max

const saveSettings = async (patch: Partial<PublicSettings>): Promise<CommandResult> => {
  if (patch.toggleShortcut !== undefined && !isValidShortcut(patch.toggleShortcut)) return result(false, undefined, 'Toggle shortcut needs a modifier and one key, for example Control+Shift+Tab.')
  if (patch.holdShortcut !== undefined && !isValidShortcut(patch.holdShortcut)) return result(false, undefined, 'Hold shortcut needs a modifier and one key, for example Control+Shift+Space.')
  if (patch.cleanupPrompts !== undefined) {
    for (const level of ['none', 'light', 'medium'] as const) {
      const prompt = patch.cleanupPrompts[level]
      if (!validateText(prompt, 8_000) || !prompt.trim()) return result(false, undefined, 'Each cleanup prompt needs non-empty instructions.')
    }
  }
  if (patch.theme !== undefined && !['light', 'dark', 'system'].includes(patch.theme)) return result(false, undefined, 'Choose light, dark, or system appearance.')
  const previous = await getSnapshot()
  const previousShortcut = previous.settings.toggleShortcut
  if (patch.toggleShortcut !== undefined && patch.toggleShortcut !== previousShortcut) {
    const previousActiveShortcut = registeredShortcut
    if (!shortcutRecording) unregisterShortcut()
    if (!attemptRegisterShortcut(patch.toggleShortcut)) {
      if (!shortcutRecording && previousActiveShortcut) attemptRegisterShortcut(previousActiveShortcut)
      return result(false, undefined, 'That shortcut is unavailable or already claimed by another app. Choose a different combination.')
    }
  }
  await store.update((snapshot) => {
    snapshot.settings = { ...snapshot.settings, ...patch }
  })
  if (patch.theme !== undefined) {
    nativeTheme.themeSource = patch.theme
    mainWindow?.setBackgroundColor(windowBackgroundColor())
  }
  if (patch.showPill !== undefined) {
    pillEnabled = patch.showPill
    if (pillEnabled) showOverlay()
    else overlayWindow?.hide()
  }
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
    const health = {
      appName: app.getName(),
      packaged: app.isPackaged,
      rendererLoaded: trusted,
      preloadBridge: trusted,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
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
    await store.update((snapshot) => (snapshot.records = snapshot.records.filter((record) => record.id !== id)))
    notifyBootstrapChanged()
    return result(true, 'Dictation deleted.')
  })
  ipcMain.handle('history:favorite', async (event, id: unknown) => {
    if (!isTrustedSender(event) || typeof id !== 'string') return result(false, undefined, 'Invalid history entry.')
    await store.update((snapshot) => {
      const record = snapshot.records.find((candidate) => candidate.id === id)
      if (record) record.favorite = !record.favorite
    })
    notifyBootstrapChanged()
    return result(true)
  })
  ipcMain.handle('history:copy', async (event, id: unknown) => {
    if (!isTrustedSender(event) || typeof id !== 'string') return result(false, undefined, 'Invalid history entry.')
    const snapshot = await getSnapshot()
    const record = snapshot.records.find((candidate) => candidate.id === id)
    return record ? copyResult(record.finalText) : result(false, undefined, 'Dictation not found.')
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
  store = new JsonStateStore(path.join(root, 'flowerwhisp.json'))
  secrets = new SecretStore(path.join(app.getPath('userData'), 'secrets'))
  await store.load()
  pillEnabled = (await getSnapshot()).settings.showPill
  pipeline = new DictationPipeline(store, secrets)
  nativeTheme.themeSource = (await getSnapshot()).settings.theme
  createWindows()
  registerIpc()
  createTray()
  setupPermissions()
  await registerShortcut()
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
    globalShortcut.unregisterAll()
    tray?.destroy()
  })
  app.on('window-all-closed', () => {
    // The main window hides to the tray on close. Keep the process alive for the tray and shortcut.
  })
}
