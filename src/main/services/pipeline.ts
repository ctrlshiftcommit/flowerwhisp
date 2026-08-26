import type { CleanupStatus, DictationRecord, PublicSettings, ProviderId } from '../../shared/ipc'
import { applyDictionary, countWords } from '../domain'
import { GroqProvider, LocalTranscriptionProvider, ProviderError, type AudioInput } from './providers'
import { JsonStateStore } from './store'
import { SecretStore } from './secrets'

export interface PipelineRequest {
  audio: AudioInput
  settings: PublicSettings
}

export interface PipelineResult {
  rawText: string
  cleanedText: string
  finalText: string
  record: DictationRecord
  provider: ProviderId
  dictionaryFixCount: number
  aiFixCount: number
  cleanupStatus: CleanupStatus
  cleanupError?: string
}

const changedWordCount = (before: string, after: string): number => {
  const left = before.trim().split(/\s+/).filter(Boolean)
  const right = after.trim().split(/\s+/).filter(Boolean)
  const shared = Math.min(left.length, right.length)
  let changes = Math.abs(left.length - right.length)
  for (let index = 0; index < shared; index += 1) {
    if (left[index].toLowerCase() !== right[index].toLowerCase()) changes += 1
  }
  return changes
}

export class DictationPipeline {
  private readonly groq: GroqProvider

  public constructor(private readonly store: JsonStateStore, private readonly secrets: SecretStore) {
    this.groq = new GroqProvider(() => this.secrets.getGroqKey())
  }

  public async run(request: PipelineRequest): Promise<PipelineResult> {
    const snapshot = await this.store.load()
    const provider = request.settings.transcriptionProvider
    const transcription =
      provider === 'groq'
        ? await this.groq.transcribe(request.audio, request.settings.transcriptionModel, request.settings.language)
        : await new LocalTranscriptionProvider(
            request.settings.localCommand,
            request.settings.localWorkingDirectory,
          ).transcribe(request.audio, request.settings.transcriptionModel)

    const dictionary = applyDictionary(
      transcription.text,
      snapshot.dictionary.map((entry) => ({
        spoken: entry.spoken,
        replacement: entry.replacement,
      })),
    )
    const rawText = transcription.text
    const cleanedText = dictionary.text
    let finalText = cleanedText
    let cleanupStatus: CleanupStatus = 'disabled'
    let cleanupError: string | undefined

    if (request.settings.cleanupLevel !== 'none') {
      const style = snapshot.styles.find((candidate) => candidate.id === request.settings.defaultStyle)
      try {
        const polished = await this.groq.cleanup(
          cleanedText,
          request.settings,
          style?.rules ?? [],
        )
        finalText = polished.text
        cleanupStatus = polished.changed ? 'applied' : 'unchanged'
      } catch (error) {
        // Cleanup is optional. Preserve the deterministic transcript, but do
        // not pretend that a text-LLM request succeeded when it did not.
        finalText = cleanedText
        cleanupStatus = 'failed'
        cleanupError = error instanceof Error ? error.message : 'Text cleanup failed.'
        console.warn(`[cleanup] failed level=${request.settings.cleanupLevel} model=${request.settings.llmModel}: ${cleanupError}`)
      }
    }

    const now = new Date()
    const retention = request.settings.retention
    const storedText = retention === 'never'
    const record: DictationRecord = {
      id: `dictation-${now.getTime()}-${Math.random().toString(36).slice(2, 8)}`,
      createdAt: now.toISOString(),
      rawText: storedText ? '' : rawText,
      cleanedText: storedText ? '' : cleanedText,
      finalText: storedText ? '' : finalText,
      durationMs: request.audio.durationMs,
      wordCount: countWords(finalText),
      application: 'Target not verified',
      category: 'Other',
      transcriptionProvider: provider,
      transcriptionModel: request.settings.transcriptionModel,
      llmProvider: request.settings.cleanupLevel === 'none' ? 'none' : 'groq',
      llmModel: request.settings.llmModel,
      cleanupLevel: request.settings.cleanupLevel,
      style: request.settings.defaultStyle,
      dictionaryFixCount: dictionary.replacements,
      aiFixCount: changedWordCount(cleanedText, finalText),
      insertionOutcome: 'not-attempted',
      retention,
      audioAvailable: false,
      cleanupStatus,
      cleanupError,
    }

    await this.store.update((current) => {
      current.records = [record, ...current.records].slice(0, 500)
      const day = now.toISOString().slice(0, 10)
      const existing = current.usage.find((entry) => entry.date === day)
      if (existing) {
        existing.words += record.wordCount
        existing.dictations += 1
        existing.durationMs += record.durationMs
      } else {
        current.usage.unshift({ date: day, words: record.wordCount, dictations: 1, durationMs: record.durationMs })
      }
    })

    return {
      rawText,
      cleanedText,
      finalText,
      record,
      provider,
      dictionaryFixCount: dictionary.replacements,
      aiFixCount: changedWordCount(cleanedText, finalText),
      cleanupStatus,
      cleanupError,
    }
  }

  public async transformText(sourceText: string, instructions: string, settings: PublicSettings): Promise<string> {
    const snapshot = await this.store.load()
    const style = snapshot.styles.find((candidate) => candidate.id === settings.defaultStyle)
    const transformed = await this.groq.transform(sourceText, instructions, settings, style?.rules ?? [])
    return transformed.text
  }
}
