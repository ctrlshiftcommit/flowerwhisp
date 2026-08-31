import { afterEach, describe, expect, it, vi } from 'vitest'

import { GroqProvider, ProviderError } from './providers'

const classificationResponse = (content: unknown) => new Response(JSON.stringify({
  choices: [{ message: { content: JSON.stringify(content) } }],
}), {
  status: 200,
  headers: { 'Content-Type': 'application/json' },
})

describe('Groq application classifier', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests one bounded output field without transcript or window-title data', async () => {
    let requestBody: {
      max_tokens?: number
      messages: Array<{ content: string }>
    } | undefined
    vi.stubGlobal('fetch', vi.fn(async (_input: string | URL | Request, init?: RequestInit) => {
      requestBody = JSON.parse(String(init?.body)) as typeof requestBody
      return classificationResponse({ category: 'work' })
    }))

    const category = await new GroqProvider(async () => 'test-key').classifyApplication(
      { applicationExecutable: 'quill-desk' },
      { llmModel: 'openai/gpt-oss-20b' },
    )

    expect(category).toBe('work')
    expect(requestBody?.max_tokens).toBeLessThanOrEqual(24)
    expect(requestBody?.messages[0].content).toContain('exactly one field')
    expect(JSON.parse(requestBody?.messages[1].content ?? '')).toEqual({ applicationExecutable: 'quill-desk' })
  })

  it('rejects a classifier response that adds a second output field', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => classificationResponse({ category: 'email', explanation: 'mail app' })))

    await expect(new GroqProvider(async () => 'test-key').classifyApplication(
      { applicationExecutable: 'quill-desk' },
      { llmModel: 'openai/gpt-oss-20b' },
    )).rejects.toEqual(expect.objectContaining<Partial<ProviderError>>({ code: 'invalid-output' }))
  })
})
