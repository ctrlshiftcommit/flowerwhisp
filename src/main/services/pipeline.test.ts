import { afterEach, describe, expect, it, vi } from 'vitest'

import { DictationPipeline } from './pipeline'
import { emptySnapshot } from './store'
import type { JsonStateStore } from './store'
import type { SecretStore } from './secrets'

const jsonResponse = (value: unknown) => new Response(JSON.stringify(value), {
  status: 200,
  headers: { 'Content-Type': 'application/json' },
})

describe('DictationPipeline cleanup wiring', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends a separate Groq chat-completions request whenever cleanup is selected', async () => {
    const snapshot = emptySnapshot()
    snapshot.settings.cleanupLevel = 'light'
    // Reproduce the old persisted mismatch: selected cleanup with the legacy
    // provider switch still set to none. Cleanup level must remain authoritative.
    snapshot.settings.llmProvider = 'none'
    const store = {
      load: async () => snapshot,
      update: async (mutator: (value: typeof snapshot) => void) => { mutator(snapshot); return snapshot },
    } as unknown as JsonStateStore
    const secrets = { getGroqKey: async () => 'test-key' } as unknown as SecretStore
    const fetchMock = vi.fn(async (input: string | URL | Request) => {
      const url = String(input)
      if (url.endsWith('/audio/transcriptions')) return jsonResponse({ text: 'um this is a test' })
      if (url.endsWith('/chat/completions')) return jsonResponse({ choices: [{ message: { content: JSON.stringify({ status: 'ok', text: 'This is a test.' }) } }] })
      return new Response('', { status: 404 })
    })
    vi.stubGlobal('fetch', fetchMock)

    const result = await new DictationPipeline(store, secrets).run({
      audio: { bytes: new Uint8Array([1, 2, 3]), mimeType: 'audio/webm', durationMs: 1_200 },
      settings: snapshot.settings,
    })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(String(fetchMock.mock.calls[0][0])).toContain('/audio/transcriptions')
    expect(String(fetchMock.mock.calls[1][0])).toContain('/chat/completions')
    expect(result.finalText).toBe('This is a test.')
    expect(result.cleanupStatus).toBe('applied')
    expect(result.record.cleanupStatus).toBe('applied')
    expect(result.record.llmProvider).toBe('groq')
  })

  it('keeps the safe transcript and records a visible cleanup failure', async () => {
    const snapshot = emptySnapshot()
    snapshot.settings.cleanupLevel = 'medium'
    const store = {
      load: async () => snapshot,
      update: async (mutator: (value: typeof snapshot) => void) => { mutator(snapshot); return snapshot },
    } as unknown as JsonStateStore
    const secrets = { getGroqKey: async () => 'test-key' } as unknown as SecretStore
    vi.stubGlobal('fetch', vi.fn(async (input: string | URL | Request) => String(input).endsWith('/audio/transcriptions')
      ? jsonResponse({ text: 'safe raw text' })
      : new Response(JSON.stringify({ error: 'rejected' }), { status: 429 })))

    const result = await new DictationPipeline(store, secrets).run({
      audio: { bytes: new Uint8Array([1]), mimeType: 'audio/webm', durationMs: 300 },
      settings: snapshot.settings,
    })

    expect(result.finalText).toBe('safe raw text')
    expect(result.cleanupStatus).toBe('failed')
    expect(result.cleanupError).toContain('HTTP 429')
    expect(result.record.cleanupStatus).toBe('failed')
  })
})
