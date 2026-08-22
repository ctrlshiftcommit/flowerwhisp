import { spawnSync } from 'node:child_process'

import { clipboard } from 'electron'

export interface InsertionTarget {
  handle: string
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
  [DllImport("user32.dll")]
  public static extern IntPtr GetForegroundWindow();
}
'@
$window = [FlowerWhispForegroundWindow]::GetForegroundWindow()
if ($window -eq [IntPtr]::Zero) { exit 1 }
[Console]::WriteLine($window.ToInt64())
`

const captureForegroundWindow = (): InsertionTarget | null => {
  if (process.platform !== 'win32') return null
  const result = spawnSync(
    powershellCommand,
    ['-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-Command', foregroundWindowScript],
    { encoding: 'utf8', windowsHide: true, timeout: 3_000 },
  )
  if (result.error || result.status !== 0) return null
  const handle = result.stdout.trim().split(/\s+/)[0] ?? ''
  return /^\d+$/.test(handle) ? { handle } : null
}

const pasteScript = (handle: string): string => `
Add-Type @'
using System;
using System.Runtime.InteropServices;

public static class FlowerWhispPasteInput {
  private const uint INPUT_KEYBOARD = 1;
  private const uint KEYEVENTF_KEYUP = 2;
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

  [DllImport("user32.dll")]
  private static extern bool ShowWindowAsync(IntPtr hWnd, int command);

  [DllImport("user32.dll")]
  private static extern bool SetForegroundWindow(IntPtr hWnd);

  [DllImport("user32.dll")]
  private static extern IntPtr GetForegroundWindow();

  [DllImport("user32.dll", SetLastError = true)]
  private static extern uint SendInput(uint numberOfInputs, INPUT[] inputs, int sizeOfInput);

  public static bool Paste(IntPtr target) {
    ShowWindowAsync(target, 9);
    if (GetForegroundWindow() != target) {
      SetForegroundWindow(target);
      System.Threading.Thread.Sleep(75);
    }
    if (GetForegroundWindow() != target) return false;
    var inputs = new INPUT[] {
      new INPUT { type = INPUT_KEYBOARD, keyboardInput = new KEYBDINPUT { virtualKey = VK_CONTROL } },
      new INPUT { type = INPUT_KEYBOARD, keyboardInput = new KEYBDINPUT { virtualKey = VK_V } },
      new INPUT { type = INPUT_KEYBOARD, keyboardInput = new KEYBDINPUT { virtualKey = VK_V, flags = KEYEVENTF_KEYUP } },
      new INPUT { type = INPUT_KEYBOARD, keyboardInput = new KEYBDINPUT { virtualKey = VK_CONTROL, flags = KEYEVENTF_KEYUP } },
    };
    return SendInput((uint)inputs.Length, inputs, Marshal.SizeOf(typeof(INPUT))) == inputs.Length;
  }
}
'@
$target = [IntPtr]::new(${handle})
if ([FlowerWhispPasteInput]::Paste($target)) { exit 0 }
exit 1
`

const sendPasteToTarget = (target: InsertionTarget | null): boolean => {
  if (process.platform !== 'win32' || !target || !/^\d+$/.test(target.handle)) return false
  const result = spawnSync(
    powershellCommand,
    ['-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-Command', pasteScript(target.handle)],
    { encoding: 'utf8', windowsHide: true, timeout: 5_000 },
  )
  return !result.error && result.status === 0
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

  const previousClipboard = clipboard.readText()
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

  // Preserve the user’s previous text clipboard when the target has already
  // received the paste. Do not overwrite a newer copy operation made by the
  // user while the short restoration window was open.
  setTimeout(() => {
    if (clipboard.readText() === normalized) clipboard.writeText(previousClipboard)
  }, 750)

  return {
    outcome: 'inserted',
    message: 'Transcript inserted into the active application.',
  }
}
