/**
 * The state values are intentionally serializable. They are shared by the
 * main process, preload bridge, and renderer rather than being tied to an
 * Electron or React implementation.
 */
export const DICTATION_STATES = [
  'idle',
  'starting',
  'recording',
  'stopping',
  'transcribing',
  'processing',
  'inserting',
  'success',
  'error',
  'cancelled',
] as const

export type DictationState = (typeof DICTATION_STATES)[number]

/**
 * The only legal lifecycle edges. A completed, failed, or cancelled session
 * must return to idle before a new session can start.
 */
export const DICTATION_TRANSITIONS: Readonly<
  Record<DictationState, readonly DictationState[]>
> = {
  idle: ['starting'],
  starting: ['recording', 'stopping', 'error', 'cancelled'],
  recording: ['stopping', 'error', 'cancelled'],
  stopping: ['transcribing', 'error', 'cancelled'],
  transcribing: ['processing', 'error', 'cancelled'],
  processing: ['inserting', 'error', 'cancelled'],
  inserting: ['success', 'error', 'cancelled'],
  success: ['idle'],
  error: ['idle'],
  cancelled: ['idle'],
} as const

export function isDictationState(value: unknown): value is DictationState {
  return (
    typeof value === 'string' &&
    (DICTATION_STATES as readonly string[]).includes(value)
  )
}

export type StateTransitionRejectionReason =
  | 'invalid-transition'
  | 'unknown-state'

export interface AcceptedStateTransition {
  readonly accepted: true
  readonly ok: true
  readonly from: DictationState
  readonly to: DictationState
}

export interface RejectedStateTransition {
  readonly accepted: false
  readonly ok: false
  readonly from: DictationState
  readonly to: unknown
  readonly reason: StateTransitionRejectionReason
  readonly allowedStates: readonly DictationState[]
}

export type StateTransitionResult =
  | AcceptedStateTransition
  | RejectedStateTransition

export interface DictationStateMachine {
  readonly state: DictationState
  getState(): DictationState
  canTransition(next: DictationState): boolean
  transition(next: DictationState): StateTransitionResult
  transitionOrThrow(next: DictationState): DictationState
}

export const TRANSCRIPTION_PROVIDERS = ['groq', 'local', 'local-whisper'] as const
export type TranscriptionProviderId = (typeof TRANSCRIPTION_PROVIDERS)[number]
export type TranscriptionProvider = TranscriptionProviderId

export const LLM_PROVIDERS = ['none', 'groq'] as const
export type LlmProviderId = (typeof LLM_PROVIDERS)[number]
export type CleanupProviderId = LlmProviderId
export type CleanupProvider = CleanupProviderId
export type ProviderId = TranscriptionProviderId | LlmProviderId

export type CleanupLevel = 'none' | 'light' | 'medium'
export type ShortcutMode = 'hold' | 'toggle'
export type AudioRetentionPolicy =
  | 'never-store'
  | 'keep-forever'
  | 'delete-after-24-hours'

/**
 * Provider summaries are safe IPC data. In particular, this contract has no
 * API-key, token, authorization-header, or credential field.
 */
export interface ProviderStatus {
  readonly provider: ProviderId
  readonly configured: boolean
  readonly available: boolean
  readonly displayName: string
  readonly model?: string
  readonly detail?: string
}

/** Provider choices exposed to settings and the renderer; secrets stay in main. */
export interface ProviderSettings {
  readonly transcriptionProvider: TranscriptionProviderId
  readonly transcriptionModel: string
  readonly llmProvider: LlmProviderId
  readonly llmModel?: string
}

export interface TranscriptionRequest {
  readonly recordingId: string
  readonly provider: TranscriptionProviderId
  readonly model: string
  readonly language?: string
  readonly audioMimeType: string
  readonly durationMs: number
}

export interface TranscriptionResult {
  readonly provider: TranscriptionProviderId
  readonly model: string
  readonly rawText: string
  readonly language?: string
}

export interface CleanupRequest {
  readonly provider: LlmProviderId
  readonly model?: string
  readonly cleanupLevel: CleanupLevel
  readonly rawText: string
  readonly style?: StyleProfile
}

export interface CleanupResult {
  readonly provider: LlmProviderId
  readonly model?: string
  readonly cleanText: string
  readonly aiFixCount?: number
}

export interface DictionaryEntry {
  readonly id?: string
  /** Canonical domain name for the spoken/incorrect phrase. */
  readonly phrase?: string
  /** IPC compatibility name used by the settings/history store. */
  readonly spoken?: string
  readonly replacement: string
  readonly enabled?: boolean
  readonly protected?: boolean
  readonly scope?: 'all' | 'technical' | 'personal'
  readonly createdAt?: string
}

export type DictionaryInput =
  | ReadonlyArray<DictionaryEntry>
  | Readonly<Record<string, string>>

export interface DictionaryApplicationResult {
  readonly text: string
  /** Number of non-overlapping matches replaced in the original text. */
  readonly replacementCount: number
  /** Compatibility alias for existing main-process consumers. */
  readonly replacements: number
}

export interface StyleProfile {
  readonly id: string
  readonly name: string
  readonly description: string
  /** Instructions are user-selected content, not provider credentials. */
  readonly instructions?: string
  readonly rules?: ReadonlyArray<string>
  readonly exampleOutput?: string
  readonly example?: string
  readonly category?: 'personal' | 'work' | 'email' | 'other'
  readonly builtIn?: boolean
}

export interface DictationSettings extends ProviderSettings {
  readonly language: string
  readonly cleanupLevel: CleanupLevel
  readonly defaultStyleId?: string
  readonly defaultStyle?: string
  readonly shortcutMode: ShortcutMode
  readonly audioRetention: AudioRetentionPolicy
  readonly retention?: 'forever' | '24h' | 'never'
  readonly dictionary: ReadonlyArray<DictionaryEntry>
}

export type DictationOutcome = 'success' | 'error' | 'cancelled'

/**
 * The original transcript is required. Clean and final text are optional so
 * failed or partially processed dictations still retain recoverable input.
 */
export interface TranscriptFields {
  readonly rawText: string
  readonly cleanedText?: string
  readonly cleanText?: string
  readonly finalText?: string
}

/**
 * A serializable history record. Cached wordCount is retained for privacy
 * modes that deliberately discard transcript text after processing.
 */
export interface HistoryRecord extends TranscriptFields {
  readonly id: string
  readonly createdAt: string
  readonly durationMs: number
  readonly wordCount?: number
  readonly application?: string
  readonly category?: string
  readonly applicationName?: string
  readonly applicationCategory?: string
  readonly transcriptionProvider?: TranscriptionProviderId
  readonly transcriptionModel?: string
  readonly llmProvider?: LlmProviderId
  readonly llmModel?: string
  readonly cleanupLevel?: CleanupLevel
  readonly style?: string
  readonly dictionaryFixCount?: number
  readonly aiFixCount?: number
  readonly insertionOutcome?:
    | 'inserted'
    | 'copied'
    | 'scratchpad'
    | 'not-attempted'
    | 'failed'
  readonly cleanupStatus?: 'disabled' | 'applied' | 'unchanged' | 'failed'
  readonly status?: DictationOutcome
}

export type DictationHistoryRecord = HistoryRecord

export interface ApplicationInsight {
  readonly applicationName: string
  readonly applicationCategory?: string
  readonly dictationCount: number
  readonly wordCount: number
  readonly durationMs: number
  readonly percentage: number
}

export interface ActivityDayInsight {
  /** Calendar date in YYYY-MM-DD form. */
  readonly date: string
  readonly dictationCount: number
  readonly wordCount: number
  readonly durationMs: number
}

export type WritingInsightCategory = 'personal' | 'work' | 'email' | 'other'

export interface CategoryInsight {
  readonly category: WritingInsightCategory
  readonly dictationCount: number
  readonly wordCount: number
  readonly durationMs: number
  readonly percentage: number
}

export type DayPart = 'morning' | 'afternoon' | 'evening' | 'night'

export interface DayPartInsight {
  readonly part: DayPart
  readonly dictationCount: number
  readonly wordCount: number
  readonly durationMs: number
  readonly percentage: number
}

export interface PeriodInsight {
  readonly startDate: string
  readonly endDate: string
  readonly dictationCount: number
  readonly wordCount: number
  readonly durationMs: number
}

export interface InsightUsageDay {
  readonly date: string
  readonly words: number
  readonly dictations: number
  readonly durationMs: number
}

export interface InsightsOptions {
  /**
   * Calendar date used to calculate the current streak. When omitted, the
   * latest valid activity date is used, keeping the function deterministic.
   */
  readonly asOfDate?: string
  /** Privacy-safe daily totals persisted independently of transcript history. */
  readonly usage?: ReadonlyArray<InsightUsageDay>
  /** Used only for time-of-day grouping; activity dates follow persisted days. */
  readonly timeZone?: string
  readonly recentDayCount?: number
}

export interface InsightSummary {
  readonly totalDictations: number
  readonly totalWords: number
  /** A word-derived estimate, not provider billing or API usage. */
  readonly estimatedTokens: number
  readonly totalDurationMs: number
  readonly totalDurationMinutes: number
  readonly averageWpm: number
  readonly averageWordsPerDictation: number
  readonly averageSessionDurationMs: number
  readonly longestSessionMs: number
  readonly activeDays: number
  readonly totalFixes: number
  readonly dictionaryFixes: number
  readonly aiFixes: number
  readonly successfulDictations: number
  readonly errorDictations: number
  readonly cancelledDictations: number
  readonly insertedDictations: number
  readonly clipboardFallbacks: number
  readonly scratchpadSaves: number
  readonly failedInsertions: number
  readonly unattemptedInsertions: number
  readonly cleanupApplied: number
  readonly cleanupUnchanged: number
  readonly cleanupFailed: number
  readonly cleanupDisabled: number
  readonly applicationUsage: readonly ApplicationInsight[]
  readonly categoryUsage: readonly CategoryInsight[]
  readonly dayPartUsage: readonly DayPartInsight[]
  readonly activityByDay: readonly ActivityDayInsight[]
  readonly recentDays: readonly ActivityDayInsight[]
  readonly currentPeriod: PeriodInsight | null
  readonly previousPeriod: PeriodInsight | null
  readonly wordTrendPercent: number | null
  readonly bestDay: ActivityDayInsight | null
  readonly currentStreakDays: number
  readonly longestStreakDays: number
  readonly asOfDate: string | null
}
