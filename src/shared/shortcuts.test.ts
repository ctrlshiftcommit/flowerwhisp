import { describe, expect, it } from 'vitest'

import { isValidHoldShortcut, isValidShortcut, shortcutFromEvent } from './shortcuts'

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
