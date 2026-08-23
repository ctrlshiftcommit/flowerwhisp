export const DEFAULT_TOGGLE_SHORTCUT = 'Control+Super+Space'
export const DEFAULT_HOLD_SHORTCUT = 'Control+Super'
export const COPILOT_SHORTCUT = 'F23'

const shortcutModifiers = new Set(['Control', 'CommandOrControl', 'Alt', 'Shift', 'Super'])
const shortcutKeys = new Set([
  'Space',
  'Tab',
  'Enter',
  'Escape',
  'Esc',
  'Backspace',
  'Delete',
  'Insert',
  'Home',
  'End',
  'PageUp',
  'PageDown',
  'Up',
  'Down',
  'Left',
  'Right',
  'PrintScreen',
  'VolumeUp',
  'VolumeDown',
  'VolumeMute',
  'MediaPlayPause',
  'MediaNextTrack',
  'MediaPreviousTrack',
  'MediaStop',
])

const isFunctionKey = (part: string): boolean => /^F(?:[1-9]|1[0-9]|2[0-4])$/.test(part)
const isLetterOrNumber = (part: string): boolean => /^[A-Z0-9]$/.test(part)

export const isShortcutModifier = (part: string): boolean => shortcutModifiers.has(part)

export const SHORTCUT_REQUIREMENT = 'Use any combination with at least one modifier (Ctrl, Alt, Shift, or Win) and one final key. Function keys, Tab, Space, and the Windows Copilot key are supported.'
export const HOLD_SHORTCUT_REQUIREMENT = 'Hold one or more modifier keys, a modifier plus a final key, or a function key. Dictation starts when the complete combination is down and finishes when you release it.'

export interface ShortcutKeyEvent {
  type?: 'keydown' | 'keyup'
  key: string
  code?: string
  ctrlKey?: boolean
  altKey?: boolean
  shiftKey?: boolean
  metaKey?: boolean
  getModifierState?: (key: string) => boolean
}

const shortcutKeyAliases: Record<string, string> = {
  LaunchApp1: COPILOT_SHORTCUT,
  LaunchApp2: 'F24',
  LaunchApplication1: COPILOT_SHORTCUT,
  LaunchApplication2: 'F24',
  Copilot: COPILOT_SHORTCUT,
}

const modifierKeys = new Set(['Control', 'Alt', 'Shift', 'Meta', 'OS', 'Windows', 'Win', 'Super'])

/** Convert one browser keydown into the canonical Electron accelerator format. */
export const shortcutFromEvent = (event: ShortcutKeyEvent): string => {
  const key = event.key || ''
  const code = event.code || ''
  const isWindowsKey = Boolean(
    event.metaKey
      || key === 'Meta'
      || key === 'OS'
      || key === 'Windows'
      || key === 'Win'
      || code === 'MetaLeft'
      || code === 'MetaRight'
      || code === 'OSLeft'
      || code === 'OSRight'
      || event.getModifierState?.('OS')
      || event.getModifierState?.('Meta'),
  )
  const parts = [
    event.ctrlKey || key === 'Control' ? 'Control' : '',
    event.altKey || key === 'Alt' ? 'Alt' : '',
    event.shiftKey || key === 'Shift' ? 'Shift' : '',
    isWindowsKey ? 'Super' : '',
  ].filter(Boolean)

  if (!modifierKeys.has(key)) {
    const namedKeys: Record<string, string> = {
      ' ': 'Space',
      Escape: 'Escape',
      Esc: 'Escape',
      Enter: 'Enter',
      Tab: 'Tab',
      Backspace: 'Backspace',
      Delete: 'Delete',
      Insert: 'Insert',
      Home: 'Home',
      End: 'End',
      PageUp: 'PageUp',
      PageDown: 'PageDown',
      ArrowUp: 'Up',
      ArrowDown: 'Down',
      ArrowLeft: 'Left',
      ArrowRight: 'Right',
    }
    const alias = shortcutKeyAliases[key] ?? shortcutKeyAliases[code]
    const normalizedFromCode = code.startsWith('Key') && code.length === 4
      ? code.slice(3).toUpperCase()
      : code.startsWith('Digit') && code.length === 6
        ? code.slice(5)
        : ''
    const normalized = namedKeys[key] ?? alias ?? normalizedFromCode ?? (key.length === 1 ? key.toUpperCase() : key.toUpperCase())
    if (normalized && normalized !== 'UNIDENTIFIED') parts.push(normalized)
  }

  return parts.join('+')
}

/**
 * Validate the accelerator shape shared by the recorder and the Windows
 * registration paths. A shortcut may contain any number of modifiers, but it
 * must end in exactly one key; modifier-only values are never actionable.
 */
export const isValidShortcut = (value: unknown): value is string => {
  if (typeof value !== 'string') return false
  const parts = value.split('+').filter(Boolean)
  if (parts.length === 1 && parts[0] === COPILOT_SHORTCUT) return true
  if (parts.length < 2 || new Set(parts).size !== parts.length) return false
  if (!parts.every((part) => isShortcutModifier(part) || shortcutKeys.has(part) || isLetterOrNumber(part) || isFunctionKey(part))) return false
  const keyParts = parts.filter((part) => !isShortcutModifier(part))
  if (keyParts.length > 1) return false
  if (keyParts.length !== 1) return false
  return parts.some(isShortcutModifier)
}

/**
 * Hold-to-dictate is driven by a native key-down/key-up hook, so it can use
 * modifier-only gestures such as Ctrl+Win as well as ordinary accelerators.
 * A lone function key is also useful for remappable hardware and Copilot.
 */
export const isValidHoldShortcut = (value: unknown): value is string => {
  if (typeof value !== 'string') return false
  const parts = value.split('+').filter(Boolean)
  if (parts.length === 0 || new Set(parts).size !== parts.length) return false
  if (!parts.every((part) => isShortcutModifier(part) || shortcutKeys.has(part) || isLetterOrNumber(part) || isFunctionKey(part))) return false

  const keyParts = parts.filter((part) => !isShortcutModifier(part))
  if (keyParts.length > 1) return false
  if (parts.some(isShortcutModifier)) return true
  return keyParts.length === 1 && isFunctionKey(keyParts[0])
}
