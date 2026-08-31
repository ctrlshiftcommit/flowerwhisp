import { describe, expect, it } from 'vitest'

import { detectApplicationContext } from './applicationContext'

describe('desktop application context detection', () => {
  it.each([
    ['WhatsApp', 'personal', 'WhatsApp'],
    ['slack.exe', 'work', 'Slack'],
    ['OUTLOOK', 'email', 'Outlook'],
    ['notepad', 'other', 'Notepad'],
  ] as const)('categorizes known process %s without an LLM call', (processName, purpose, applicationName) => {
    const result = detectApplicationContext({ handle: '1', processName })

    expect(result).toMatchObject({
      applicationName,
      purpose,
      source: 'rule',
      automaticCleanupAllowed: true,
    })
    expect(result.classifierInput).toBeUndefined()
  })

  it('uses browser titles locally to recognize known web applications', () => {
    const result = detectApplicationContext({
      handle: '1',
      processName: 'chrome',
      windowTitle: 'Inbox (2) - Gmail - Google Chrome',
    })

    expect(result).toMatchObject({
      applicationName: 'Gmail',
      purpose: 'email',
      source: 'rule',
      automaticCleanupAllowed: true,
    })
  })

  it('suppresses automatic cleanup in a browser address bar', () => {
    const result = detectApplicationContext({
      handle: '1',
      processName: 'msedge',
      windowTitle: 'Gmail - Microsoft Edge',
      isBrowserAddressBar: true,
    })

    expect(result.applicationName).toBe('Gmail')
    expect(result.purpose).toBe('email')
    expect(result.automaticCleanupAllowed).toBe(false)
  })

  it('also recognizes common omnibox metadata when the native boolean is unavailable', () => {
    const result = detectApplicationContext({
      handle: '1',
      processName: 'chrome',
      focusClass: 'Chrome_OmniboxView',
    })

    expect(result.purpose).toBe('other')
    expect(result.automaticCleanupAllowed).toBe(false)
  })

  it('treats a generic browser as other without exposing its page title to a classifier', () => {
    const result = detectApplicationContext({
      handle: '1',
      processName: 'firefox',
      windowTitle: 'Private project name - Mozilla Firefox',
    })

    expect(result).toMatchObject({
      applicationName: 'Mozilla Firefox',
      purpose: 'other',
      source: 'rule',
    })
    expect(result.classifierInput).toBeUndefined()
  })

  it('offers only an unknown executable label to the fallback classifier', () => {
    const result = detectApplicationContext({
      handle: '1',
      processName: 'quill-desk.exe',
      windowTitle: 'Confidential client document',
    })

    expect(result).toMatchObject({
      applicationName: 'Quill Desk',
      classifierInput: 'quill-desk',
      cacheKey: 'quill-desk',
      automaticCleanupAllowed: true,
    })
    expect(result.purpose).toBeUndefined()
  })

  it('falls back safely when Windows exposes no target', () => {
    expect(detectApplicationContext(null)).toEqual({
      applicationName: 'Unknown application',
      purpose: 'other',
      source: 'fallback',
      automaticCleanupAllowed: true,
    })
  })
})
