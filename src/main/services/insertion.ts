import { spawnSync } from 'node:child_process'

import { clipboard } from 'electron'

export interface InsertionTarget {
  handle: string
  /** The control that owned the caret when dictation began, when Windows exposes it. */
  focusHandle?: string
}

export interface InsertionOutcome {
  outcome: 'inserted' | 'copied' | 'failed'
  message: string
}

const powershellCommand = 'powershell.exe'

const foregroundWindowScript = `
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class FlowerWhispForegroundWindow {
  [StructLayout(LayoutKind.Sequential)]
  public struct RECT {
    public int left;
    public int top;
    public int right;
    public int bottom;
  }

  [StructLayout(LayoutKind.Sequential)]
  public struct GUITHREADINFO {
    public uint cbSize;
    public uint flags;
    public IntPtr hwndActive;
    public IntPtr hwndFocus;
    public IntPtr hwndCapture;
    public IntPtr hwndMenuOwner;
    public IntPtr hwndMoveSize;
    public IntPtr hwndCaret;
    public RECT rcCaret;
  }

  [DllImport("user32.dll")]
  public static extern IntPtr GetForegroundWindow();

  [DllImport("user32.dll")]
  public static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);

  [DllImport("user32.dll")]
  public static extern bool GetGUIThreadInfo(uint threadId, ref GUITHREADINFO threadInfo);
}
'@
$window = [FlowerWhispForegroundWindow]::GetForegroundWindow()
if ($window -eq [IntPtr]::Zero) { exit 1 }
[uint32]$processId = 0
[uint32]$threadId = [FlowerWhispForegroundWindow]::GetWindowThreadProcessId($window, [ref]$processId)
if ($processId -eq ${process.pid}) { exit 2 }
$focusHandle = 0
$threadInfo = New-Object FlowerWhispForegroundWindow+GUITHREADINFO
$threadInfo.cbSize = [Runtime.InteropServices.Marshal]::SizeOf([type][FlowerWhispForegroundWindow+GUITHREADINFO])
if ($threadId -ne 0 -and [FlowerWhispForegroundWindow]::GetGUIThreadInfo($threadId, [ref]$threadInfo) -and $threadInfo.hwndFocus -ne [IntPtr]::Zero) {
  $focusHandle = $threadInfo.hwndFocus.ToInt64()
}
[Console]::WriteLine("$($window.ToInt64())|$focusHandle")
`

const captureForegroundWindow = (): InsertionTarget | null => {
  if (process.platform !== 'win32') return null
  const result = spawnSync(
    powershellCommand,
    ['-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-Command', foregroundWindowScript],
    { encoding: 'utf8', windowsHide: true, timeout: 3_000 },
  )
  if (result.error || result.status !== 0) return null
  const [handle, focusHandle] = (result.stdout.trim().split(/\s+/)[0] ?? '').split('|')
  return /^\d+$/.test(handle) ? { handle, ...(focusHandle && /^\d+$/.test(focusHandle) && focusHandle !== '0' ? { focusHandle } : {}) } : null
}

const pasteScript = (handle: string, focusHandle = ''): string => `
Add-Type @'
using System;
using System.Runtime.InteropServices;

public static class FlowerWhispPasteInput {
  private const uint INPUT_KEYBOARD = 1;
  private const uint KEYEVENTF_KEYUP = 2;
  private const uint WM_PASTE = 0x0302;
  private const ushort VK_CONTROL = 0x11;
  private const ushort VK_V = 0x56;

  [StructLayout(LayoutKind.Explicit, Size = 40)]
  private struct INPUT {
    [FieldOffset(0)]
    public uint type;

    [FieldOffset(8)]
    public KEYBDINPUT keyboardInput;
  }

  [StructLayout(LayoutKind.Sequential)]
  private struct KEYBDINPUT {
    public ushort virtualKey;
    public ushort scanCode;
    public uint flags;
    public uint time;
    public IntPtr extraInfo;
  }

  [StructLayout(LayoutKind.Sequential)]
  private struct RECT {
    public int left;
    public int top;
    public int right;
    public int bottom;
  }

  [StructLayout(LayoutKind.Sequential)]
  private struct GUITHREADINFO {
    public uint cbSize;
    public uint flags;
    public IntPtr hwndActive;
    public IntPtr hwndFocus;
    public IntPtr hwndCapture;
    public IntPtr hwndMenuOwner;
    public IntPtr hwndMoveSize;
    public IntPtr hwndCaret;
    public RECT rcCaret;
  }

  [DllImport("user32.dll")]
  private static extern bool SetForegroundWindow(IntPtr hWnd);

  [DllImport("user32.dll")]
  private static extern bool BringWindowToTop(IntPtr hWnd);

  [DllImport("user32.dll")]
  private static extern IntPtr GetForegroundWindow();

  [DllImport("user32.dll")]
  private static extern uint GetWindowThreadProcessId(IntPtr hWnd, IntPtr processId);

  [DllImport("kernel32.dll")]
  private static extern uint GetCurrentThreadId();

  [DllImport("user32.dll")]
  private static extern bool AttachThreadInput(uint attachThreadId, uint attachToThreadId, bool attach);

  [DllImport("user32.dll")]
  private static extern bool IsIconic(IntPtr hWnd);

  [DllImport("user32.dll")]
  private static extern bool ShowWindowAsync(IntPtr hWnd, int command);

  [DllImport("user32.dll", SetLastError = true)]
  private static extern uint SendInput(uint numberOfInputs, INPUT[] inputs, int sizeOfInput);

  [DllImport("user32.dll")]
  private static extern void keybd_event(byte virtualKey, byte scanCode, uint flags, UIntPtr extraInfo);

  [DllImport("user32.dll")]
  private static extern bool GetGUIThreadInfo(uint threadId, ref GUITHREADINFO threadInfo);

  [DllImport("user32.dll")]
  private static extern IntPtr SendMessage(IntPtr hWnd, uint message, IntPtr wParam, IntPtr lParam);

  public static bool Paste(IntPtr target, IntPtr capturedFocus) {
    var targetThread = GetWindowThreadProcessId(target, IntPtr.Zero);
    var focus = capturedFocus;
    if (focus != IntPtr.Zero && GetWindowThreadProcessId(focus, IntPtr.Zero) == 0) focus = IntPtr.Zero;

    if (GetForegroundWindow() != target) {
      var currentThread = GetCurrentThreadId();
      var attached = targetThread != 0 && currentThread != targetThread && AttachThreadInput(currentThread, targetThread, true);
      try {
        // Restore only a minimized target. SW_RESTORE would also take a
        // maximized window back to its normal size, which is not acceptable.
        if (IsIconic(target)) ShowWindowAsync(target, 9);
        BringWindowToTop(target);
        SetForegroundWindow(target);
        System.Threading.Thread.Sleep(120);
        if (GetForegroundWindow() != target) {
          BringWindowToTop(target);
          SetForegroundWindow(target);
          System.Threading.Thread.Sleep(120);
        }
      } finally {
        if (attached) AttachThreadInput(currentThread, targetThread, false);
      }
    }
    // A captured edit control can still own the caret even when Windows
    // refuses to transfer foreground activation across integrity levels.
    // Prefer that exact control before giving up on automatic insertion.
    if (GetForegroundWindow() != target && focus != IntPtr.Zero) {
      SendMessage(focus, WM_PASTE, IntPtr.Zero, IntPtr.Zero);
      return true;
    }
    if (GetForegroundWindow() != target) return false;
    var inputs = new INPUT[] {
      new INPUT { type = INPUT_KEYBOARD, keyboardInput = new KEYBDINPUT { virtualKey = VK_CONTROL } },
      new INPUT { type = INPUT_KEYBOARD, keyboardInput = new KEYBDINPUT { virtualKey = VK_V } },
      new INPUT { type = INPUT_KEYBOARD, keyboardInput = new KEYBDINPUT { virtualKey = VK_V, flags = KEYEVENTF_KEYUP } },
      new INPUT { type = INPUT_KEYBOARD, keyboardInput = new KEYBDINPUT { virtualKey = VK_CONTROL, flags = KEYEVENTF_KEYUP } },
    };
    var sent = SendInput((uint)inputs.Length, inputs, Marshal.SizeOf(typeof(INPUT)));
    if (sent == inputs.Length) return true;
    // A few Windows integrity/input configurations reject SendInput even
    // though the legacy keyboard path is still accepted. Retry the same
    // Ctrl+V chord without changing the target window state.
    if (sent == 0) {
      keybd_event((byte)VK_CONTROL, 0, 0, UIntPtr.Zero);
      keybd_event((byte)VK_V, 0, 0, UIntPtr.Zero);
      keybd_event((byte)VK_V, 0, KEYEVENTF_KEYUP, UIntPtr.Zero);
      keybd_event((byte)VK_CONTROL, 0, KEYEVENTF_KEYUP, UIntPtr.Zero);
      return true;
    }

    // Some editors do not accept synthetic keyboard input from a different
    // integrity/UI thread even after the top-level window is foreground. In
    // that case, paste directly into the control that owns the caret.
    var threadInfo = new GUITHREADINFO { cbSize = (uint)Marshal.SizeOf(typeof(GUITHREADINFO)) };
    if (focus == IntPtr.Zero && targetThread != 0 && GetGUIThreadInfo(targetThread, ref threadInfo)) focus = threadInfo.hwndFocus;
    if (focus != IntPtr.Zero) {
      SendMessage(focus, WM_PASTE, IntPtr.Zero, IntPtr.Zero);
      return true;
    }
    return false;
  }
}
'@
$target = [IntPtr]::new(${handle})
$capturedFocus = [IntPtr]::new(${/^\d+$/.test(focusHandle) ? focusHandle : '0'})
if ([FlowerWhispPasteInput]::Paste($target, $capturedFocus)) { exit 0 }
exit 1
`

const sendPasteToTarget = (target: InsertionTarget | null): boolean => {
  if (process.platform !== 'win32' || !target || !/^\d+$/.test(target.handle)) return false
  const result = spawnSync(
    powershellCommand,
    ['-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-Command', pasteScript(target.handle, target.focusHandle)],
    { encoding: 'utf8', windowsHide: true, timeout: 5_000 },
  )
  if (result.error || result.status !== 0) {
    console.warn(`[insertion] native paste failed status=${result.status ?? 'unknown'} error=${result.error?.message ?? result.stderr?.trim() ?? 'unknown'}`)
    return false
  }
  return true
}

export const captureInsertionTarget = (): InsertionTarget | null => captureForegroundWindow()

export const copyForManualPaste = (text: string): InsertionOutcome => {
  const normalized = text.trim()
  if (!normalized) throw new Error('There is no transcript to copy.')
  clipboard.writeText(normalized)
  return {
    outcome: 'copied',
    message: 'Copied to the clipboard. Press Ctrl+V in the target application.',
  }
}

export const insertAtTarget = (text: string, target: InsertionTarget | null): InsertionOutcome => {
  const normalized = text.trim()
  if (!normalized) throw new Error('There is no transcript to insert.')

  // Keep the transcript in the clipboard even when native insertion succeeds.
  // The user explicitly expects the result to be available for a later paste;
  // native insertion is an additional best-effort action, not a clipboard
  // replacement operation that should be undone after 750ms.
  clipboard.writeText(normalized)

  if (!target) {
    return {
      outcome: 'copied',
      message: 'The target app was not available, so the transcript was copied. Press Ctrl+V to insert it.',
    }
  }

  if (!sendPasteToTarget(target)) {
    return {
      outcome: 'failed',
      message: 'Automatic paste failed. The transcript is copied; press Ctrl+V in the target application.',
    }
  }

  return {
    outcome: 'inserted',
    message: 'Transcript inserted into the active application.',
  }
}
