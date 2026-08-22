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

export const SHORTCUT_REQUIREMENT = 'Use at least one modifier (Ctrl, Alt, Shift, or Win) plus one non-modifier key.'

/**
 * Validate the small accelerator subset supported by both the recorder and
 * Electron globalShortcut. Every user-configured shortcut requires at least
 * one modifier and exactly one non-modifier key, so the displayed value stays
 * registerable as a native accelerator.
 */
export const isValidShortcut = (value: unknown): value is string => {
  if (typeof value !== 'string') return false
  const parts = value.split('+').filter(Boolean)
  if (parts.length < 2 || new Set(parts).size !== parts.length) return false
  if (!parts.every((part) => isShortcutModifier(part) || shortcutKeys.has(part) || isLetterOrNumber(part) || isFunctionKey(part))) return false
  if (!parts.some(isShortcutModifier)) return false
  const keyParts = parts.filter((part) => !isShortcutModifier(part))
  if (keyParts.length > 1) return false
  return keyParts.length === 1
}
