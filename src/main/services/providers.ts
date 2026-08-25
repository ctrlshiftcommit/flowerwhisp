import { spawn } from 'node:child_process'

import type { CleanupLevel, ProviderId, PublicSettings } from '../../shared/ipc'
import { buildCleanupSystemPrompt, buildTransformSystemPrompt } from '../prompts'

export const TRANSCRIPTION_MODELS = ['whisper-large-v3-turbo', 'whisper-large-v3'] as const
export const LLM_MODELS = ['openai/gpt-oss-20b', 'openai/gpt-oss-120b'] as const

export interface AudioInput {
  bytes: Uint8Array
  mimeType: string
  durationMs: number
}

export interface TranscriptionResult {
  text: string
  language?: string
  durationMs: number
  model: string
}

export interface TextCleanupResult {
  text: string
  changed: boolean
  model: string
}

export class ProviderError extends Error {
  public constructor(
    message: string,
    public readonly code: 'missing-key' | 'network' | 'provider' | 'local-unavailable' | 'invalid-output',
  ) {
    super(message)
    this.name = 'ProviderError'
  }
}

const assertModel = (model: string, allowed: readonly string[], kind: string): void => {
  if (!allowed.includes(model)) throw new ProviderError(`The selected ${kind} model is unavailable.`, 'provider')
}

export class GroqProvider {
  public constructor(private readonly getApiKey: () => Promise<string | null>) {}

  public async transcribe(input: AudioInput, model: string, language: string): Promise<TranscriptionResult> {
    assertModel(model, TRANSCRIPTION_MODELS, 'transcription')
    const apiKey = await this.getApiKey()
    if (!apiKey) throw new ProviderError('Add a Groq API key in Settings before using cloud transcription.', 'missing-key')

    const body = new FormData()
    body.append('file', new Blob([input.bytes as unknown as ArrayBuffer], { type: input.mimeType || 'audio/webm' }), 'dictation.webm')
    body.append('model', model)
    body.append('language', language)
    body.append('response_format', 'json')

    let response: Response
    try {
      response = await fetch('https://api.groq.com/openai/v1/audio/transcriptions', {
        method: 'POST',
        headers: { Authorization: `Bearer ${apiKey}` },
        body,
        signal: AbortSignal.timeout(45_000),
      })
    } catch {
      throw new ProviderError('Groq could not be reached. Check the connection and try again.', 'network')
    }
    if (!response.ok) throw new ProviderError('Groq rejected the transcription request. Check the model and API key.', 'provider')

    const payload = (await response.json()) as { text?: unknown; duration?: unknown; language?: unknown }
    if (typeof payload.text !== 'string' || !payload.text.trim()) {
      throw new ProviderError('Groq returned an empty transcript.', 'invalid-output')
    }
    return {
      text: payload.text.trim(),
      language: typeof payload.language === 'string' ? payload.language : language,
      durationMs: typeof payload.duration === 'number' ? payload.duration * 1000 : input.durationMs,
      model,
    }
  }

  public async cleanup(
    text: string,
    settings: Pick<PublicSettings, 'llmModel' | 'language' | 'cleanupLevel' | 'cleanupPrompts' | 'defaultStyle'>,
    styleRules: string[],
  ): Promise<TextCleanupResult> {
    assertModel(settings.llmModel, LLM_MODELS, 'LLM')
    const apiKey = await this.getApiKey()
    if (!apiKey) throw new ProviderError('Add a Groq API key in Settings before enabling text cleanup.', 'missing-key')

    const system = buildCleanupSystemPrompt({
      cleanupLevel: settings.cleanupLevel,
      cleanupInstructions: settings.cleanupPrompts[settings.cleanupLevel],
      language: settings.language,
      styleId: settings.defaultStyle,
      styleRules,
    })
    const response = await fetch('https://api.groq.com/openai/v1/chat/completions', {
      method: 'POST',
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: settings.llmModel,
        temperature: 0.1,
        response_format: { type: 'json_object' },
        messages: [
          { role: 'system', content: system },
          { role: 'user', content: JSON.stringify({ sourceText: text, cleanupLevel: settings.cleanupLevel, styleRules }) },
        ],
      }),
      signal: AbortSignal.timeout(45_000),
    }).catch(() => {
      throw new ProviderError('Text cleanup could not reach Groq. The safe transcript is still available.', 'network')
    })

    if (!response.ok) throw new ProviderError('Groq rejected the cleanup request. The safe transcript is still available.', 'provider')
    const payload = (await response.json()) as { choices?: Array<{ message?: { content?: unknown } }> }
    const content = payload.choices?.[0]?.message?.content
    if (typeof content !== 'string') throw new ProviderError('Groq returned an invalid cleanup response.', 'invalid-output')

    try {
      const parsed = JSON.parse(content) as { status?: string; text?: unknown }
      if (!['ok', 'unchanged'].includes(parsed.status ?? '') || typeof parsed.text !== 'string' || !parsed.text.trim()) {
        throw new Error('invalid cleanup status')
      }
      return { text: parsed.text.trim(), changed: parsed.text.trim() !== text.trim(), model: settings.llmModel }
    } catch {
      throw new ProviderError('The cleanup response failed validation. The safe transcript is still available.', 'invalid-output')
    }
  }

  public async transform(
    text: string,
    instructions: string,
    settings: Pick<PublicSettings, 'llmModel' | 'language' | 'cleanupLevel' | 'defaultStyle'>,
    styleRules: string[],
  ): Promise<TextCleanupResult> {
    assertModel(settings.llmModel, LLM_MODELS, 'LLM')
    const apiKey = await this.getApiKey()
    if (!apiKey) throw new ProviderError('Add a Groq API key in Settings before using Command Mode.', 'missing-key')
    const system = buildTransformSystemPrompt({
      cleanupLevel: settings.cleanupLevel,
      language: settings.language,
      styleId: settings.defaultStyle,
      styleRules,
      transform: {
        name: 'Command Mode',
        description: 'A one-off instruction supplied by the user.',
        instructions,
      },
    })
    const response = await fetch('https://api.groq.com/openai/v1/chat/completions', {
      method: 'POST',
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: settings.llmModel,
        temperature: 0.1,
        response_format: { type: 'json_object' },
        messages: [
          { role: 'system', content: system },
          { role: 'user', content: JSON.stringify({ sourceText: text, transformInstructions: instructions }) },
        ],
      }),
      signal: AbortSignal.timeout(45_000),
    }).catch(() => {
      throw new ProviderError('Command Mode could not reach Groq.', 'network')
    })
    if (!response.ok) throw new ProviderError('Groq rejected the Command Mode request.', 'provider')
    const payload = (await response.json()) as { choices?: Array<{ message?: { content?: unknown } }> }
    const content = payload.choices?.[0]?.message?.content
    if (typeof content !== 'string') throw new ProviderError('Groq returned an invalid Command Mode response.', 'invalid-output')
    try {
      const parsed = JSON.parse(content) as { status?: string; text?: unknown }
      if (!['ok', 'unchanged'].includes(parsed.status ?? '') || typeof parsed.text !== 'string' || !parsed.text.trim()) throw new Error('invalid transform')
      const transformed = parsed.text.trim()
      return { text: transformed, changed: transformed !== text.trim(), model: settings.llmModel }
    } catch {
      throw new ProviderError('The Command Mode response failed validation.', 'invalid-output')
    }
  }
}

export class LocalTranscriptionProvider {
  public constructor(private readonly command: string, private readonly workingDirectory: string) {}

  public async transcribe(input: AudioInput, model: string): Promise<TranscriptionResult> {
    if (!this.command.trim()) {
      throw new ProviderError('Choose a local transcription command in Settings before using Local.', 'local-unavailable')
    }

    const result = await new Promise<string>((resolve, reject) => {
      const child = spawn(this.command, ['--flowerwhisp-stdin', '--model', model], {
        cwd: this.workingDirectory || undefined,
        shell: false,
        windowsHide: true,
      })
      let stdout = ''
      let stderr = ''
      child.stdout.setEncoding('utf8')
      child.stderr.setEncoding('utf8')
      child.stdout.on('data', (chunk: string) => (stdout += chunk))
      child.stderr.on('data', (chunk: string) => (stderr += chunk.slice(0, 500)))
      child.on('error', () => reject(new ProviderError('The configured local transcription command could not start.', 'local-unavailable')))
      child.on('close', (code) => {
        if (code !== 0) reject(new ProviderError(stderr.trim() || 'The local transcription command failed.', 'local-unavailable'))
        else resolve(stdout)
      })
      child.stdin.on('error', () => undefined)
      child.stdin.end(Buffer.from(input.bytes))
    })

    const text = result.trim()
    if (!text) throw new ProviderError('The local provider returned an empty transcript.', 'invalid-output')
    return { text, durationMs: input.durationMs, model }
  }
}

export const isCleanupEnabled = (level: CleanupLevel, provider: string): boolean => level !== 'none' && provider === 'groq'
