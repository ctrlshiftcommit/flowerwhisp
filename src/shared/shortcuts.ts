export const DEFAULT_TOGGLE_SHORTCUT = 'Control+Shift+Space'
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

export interface ShortcutKeyEvent {
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
