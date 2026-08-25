import { describe, expect, it } from 'vitest'

import {
  isValidHoldShortcut,
  isValidShortcut,
  isValidShortcutForAction,
  normalizeShortcutBindings,
  shortcutFromEvent,
} from './shortcuts'

describe('isValidShortcut', () => {
  it('accepts common toggle accelerators with a modifier and key', () => {
    expect(isValidShortcut('Control+Super+Space')).toBe(true)
    expect(isValidShortcut('Control+Space')).toBe(true)
    expect(isValidShortcut('Alt+F8')).toBe(true)
  })

  it('rejects modifier-only and unmodified toggle values', () => {
    expect(isValidShortcut('Control+Super')).toBe(false)
    expect(isValidShortcut('A+B')).toBe(false)
    expect(isValidShortcut('F1')).toBe(false)
  })

  it('keeps modifier-only values out of the toggle shortcut', () => {
    expect(isValidShortcut('Control+Super')).toBe(false)
    expect(isValidShortcut('Control')).toBe(false)
    expect(isValidShortcut('A+B')).toBe(false)
  })

  it('accepts multi-modifier keys and Copilot-compatible function keys', () => {
    expect(isValidShortcut('Control+Shift+Tab')).toBe(true)
    expect(isValidShortcut('Super+Shift+F23')).toBe(true)
    expect(isValidShortcut('F23')).toBe(true)
  })

  it('normalizes Windows and Copilot key events into Electron accelerators', () => {
    expect(shortcutFromEvent({ key: ' ', code: 'Space', ctrlKey: true, shiftKey: true })).toBe('Control+Shift+Space')
    expect(shortcutFromEvent({ key: 'Meta', code: 'MetaLeft', metaKey: true })).toBe('Super')
    expect(shortcutFromEvent({ key: 'LaunchApplication1', code: 'LaunchApp1' })).toBe('F23')
    expect(shortcutFromEvent({ key: 'Tab', code: 'Tab', metaKey: true, shiftKey: true })).toBe('Shift+Super+Tab')
  })

  it('rejects duplicate or unknown accelerator parts', () => {
    expect(isValidShortcut('Control+Control+Space')).toBe(false)
    expect(isValidShortcut('Control+A+B')).toBe(false)
    expect(isValidShortcut('Control+Super+NotAKey')).toBe(false)
  })
})

describe('isValidHoldShortcut', () => {
  it('accepts modifier-only holds, full chords, and hardware function keys', () => {
    expect(isValidHoldShortcut('Control+Super')).toBe(true)
    expect(isValidHoldShortcut('Control')).toBe(true)
    expect(isValidHoldShortcut('Control+Shift+Tab')).toBe(true)
    expect(isValidHoldShortcut('F23')).toBe(true)
    expect(isValidHoldShortcut('F8')).toBe(true)
  })

  it('rejects ambiguous and unmodified ordinary keys', () => {
    expect(isValidHoldShortcut('A')).toBe(false)
    expect(isValidHoldShortcut('Tab')).toBe(false)
    expect(isValidHoldShortcut('Control+Control')).toBe(false)
    expect(isValidHoldShortcut('Control+A+B')).toBe(false)
  })
})

describe('action shortcut bindings', () => {
  it('supports the action-specific keyboard and mouse gestures used by Flow', () => {
    expect(isValidShortcutForAction('pushToTalk', 'Control')).toBe(true)
    expect(isValidShortcutForAction('pushToTalk', 'MouseMiddle')).toBe(true)
    expect(isValidShortcutForAction('handsFree', 'DoubleTapMouseMiddle')).toBe(true)
    expect(isValidShortcutForAction('handsFree', 'Control+Space')).toBe(true)
    expect(isValidShortcutForAction('commandMode', 'Control+Alt')).toBe(true)
    expect(isValidShortcutForAction('cancel', 'Escape')).toBe(true)
  })

  it('keeps unsafe ordinary single keys out of trigger actions', () => {
    expect(isValidShortcutForAction('pasteLastTranscript', 'Z')).toBe(false)
    expect(isValidShortcutForAction('pressEnter', 'B')).toBe(false)
    expect(isValidShortcutForAction('handsFree', 'Space')).toBe(false)
  })

  it('migrates the existing dictation shortcuts without assigning new actions', () => {
    const bindings = normalizeShortcutBindings(undefined, {
      holdShortcut: 'Control',
      toggleShortcut: 'Control+Space',
    })
    expect(bindings.pushToTalk).toEqual(['Control'])
    expect(bindings.handsFree).toEqual(['Control+Space'])
    expect(bindings.pressEnter).toEqual([])
    expect(bindings.commandMode).toEqual([])
  })

  it('drops duplicates and invalid saved bindings', () => {
    const bindings = normalizeShortcutBindings({
      copyLastTranscript: ['Alt+Shift+X', 'Alt+Shift+X', 'X'],
    })
    expect(bindings.copyLastTranscript).toEqual(['Alt+Shift+X'])
  })
})
