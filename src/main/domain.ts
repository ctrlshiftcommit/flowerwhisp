import {
  DICTATION_TRANSITIONS,
  isDictationState,
} from '../shared/domain'
import type {
  ActivityDayInsight,
  ApplicationInsight,
  DictionaryApplicationResult,
  DictionaryEntry,
  DictionaryInput,
  DictationState,
  DictationStateMachine,
  HistoryRecord,
  InsightSummary,
  InsightsOptions,
  RejectedStateTransition,
  StateTransitionResult,
} from '../shared/domain'

export class InvalidStateTransitionError extends Error {
  readonly result: RejectedStateTransition

  constructor(result: RejectedStateTransition) {
    super(
      `Cannot transition dictation state from ${result.from} to ${String(
        result.to,
      )}.`,
    )
    this.name = 'InvalidStateTransitionError'
    this.result = result
  }
}

export function createStateMachine(
  initialState: DictationState = 'idle',
): DictationStateMachine {
  if (!isDictationState(initialState)) {
    throw new TypeError(`Unknown dictation state: ${String(initialState)}`)
  }

  let currentState = initialState

  const transition = (next: DictationState): StateTransitionResult => {
    const from = currentState
    const allowedStates = DICTATION_TRANSITIONS[from]

    // The type signature protects TypeScript callers; this guard protects the
    // boundary when JavaScript or an untyped IPC payload calls the function.
    if (!isDictationState(next)) {
      return {
        accepted: false,
        ok: false,
        from,
        to: next,
        reason: 'unknown-state',
        allowedStates,
      }
    }

    if (!allowedStates.includes(next)) {
      return {
        accepted: false,
        ok: false,
        from,
        to: next,
        reason: 'invalid-transition',
        allowedStates,
      }
    }

    currentState = next
    return { accepted: true, ok: true, from, to: next }
  }

  return {
    get state(): DictationState {
      return currentState
    },
    getState(): DictationState {
      return currentState
    },
    canTransition(next: DictationState): boolean {
      return (
        isDictationState(next) &&
        DICTATION_TRANSITIONS[currentState].includes(next)
      )
    },
    transition,
    transitionOrThrow(next: DictationState): DictationState {
      const result = transition(next)
      if (!result.accepted) {
        throw new InvalidStateTransitionError(result)
      }
      return result.to
    },
  }
}

interface NormalizedDictionaryEntry {
  readonly phrase: string
  readonly replacement: string
  readonly order: number
}

function dictionaryPhrase(entry: DictionaryEntry): string | undefined {
  if (typeof entry.phrase === 'string') {
    return entry.phrase
  }
  if (typeof entry.spoken === 'string') {
    return entry.spoken
  }
  return undefined
}

function normalizeCase(value: string): string {
  // toLowerCase is locale-independent, which keeps matching deterministic on
  // machines with different user locale settings.
  return value.toLowerCase()
}

function normalizeDictionary(
  dictionary: DictionaryInput,
): NormalizedDictionaryEntry[] {
  const entries: NormalizedDictionaryEntry[] = []

  if (Array.isArray(dictionary)) {
    dictionary.forEach((entry, index) => {
      const phrase = entry ? dictionaryPhrase(entry) : undefined
      if (
        !entry ||
        typeof phrase !== 'string' ||
        typeof entry.replacement !== 'string' ||
        entry.enabled === false ||
        phrase.length === 0
      ) {
        return
      }

      entries.push({
        phrase,
        replacement: entry.replacement,
        order: index,
      })
    })
  } else if (dictionary && typeof dictionary === 'object') {
    Object.entries(dictionary).forEach(([phrase, replacement], index) => {
      if (phrase.length === 0 || typeof replacement !== 'string') {
        return
      }

      entries.push({ phrase, replacement, order: index })
    })
  } else {
    throw new TypeError('Dictionary must be an array or an object map.')
  }

  // Longest-first removes overlap ambiguity ("super base" wins over
  // "base"); declaration order remains the deterministic tie-breaker.
  return entries.sort(
    (left, right) =>
      right.phrase.length - left.phrase.length || left.order - right.order,
  )
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function applyDictionary(
  text: string,
  dictionary: DictionaryInput,
): DictionaryApplicationResult {
  if (typeof text !== 'string') {
    throw new TypeError('Dictionary input text must be a string.')
  }

  const entries = normalizeDictionary(dictionary)
  if (entries.length === 0 || text.length === 0) {
    return { text, replacementCount: 0, replacements: 0 }
  }

  const byPhrase = new Map<string, NormalizedDictionaryEntry>()
  for (const entry of entries) {
    const key = normalizeCase(entry.phrase)
    if (!byPhrase.has(key)) {
      byPhrase.set(key, entry)
    }
  }

  const matcher = new RegExp(
    entries.map((entry) => escapeRegExp(entry.phrase)).join('|'),
    'giu',
  )
  let replacementCount = 0
  const replacedText = text.replace(matcher, (matched) => {
    const entry = byPhrase.get(normalizeCase(matched))
    if (!entry) {
      // This is only possible for an exotic Unicode case-folding mismatch; a
      // non-mapped match is safer to preserve than to replace incorrectly.
      return matched
    }

    replacementCount += 1
    return entry.replacement
  })

  return {
    text: replacedText,
    replacementCount,
    replacements: replacementCount,
  }
}

export function countWords(text: string): number {
  if (typeof text !== 'string') {
    throw new TypeError('Word-count input must be a string.')
  }

  return text.match(/\S+/gu)?.length ?? 0
}

export function calculateWpm(wordCount: number, durationMs: number): number
export function calculateWpm(text: string, durationMs: number): number
export function calculateWpm(
  wordCountOrText: number | string,
  durationMs: number,
): number {
  const wordCount =
    typeof wordCountOrText === 'string'
      ? countWords(wordCountOrText)
      : wordCountOrText

  if (
    !Number.isFinite(wordCount) ||
    wordCount <= 0 ||
    !Number.isFinite(durationMs) ||
    durationMs <= 0
  ) {
    return 0
  }

  return roundToTwoDecimalPlaces((wordCount * 60_000) / durationMs)
}

function roundToTwoDecimalPlaces(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100
}

function nonNegativeInteger(value: number | undefined): number {
  if (!Number.isFinite(value) || value === undefined || value <= 0) {
    return 0
  }

  return Math.floor(value)
}

function nonNegativeNumber(value: number | undefined): number {
  if (!Number.isFinite(value) || value === undefined || value <= 0) {
    return 0
  }

  return value
}

function transcriptForAnalytics(record: HistoryRecord): string {
  if (typeof record.finalText === 'string') {
    return record.finalText
  }
  if (typeof record.cleanedText === 'string') {
    return record.cleanedText
  }
  if (typeof record.cleanText === 'string') {
    return record.cleanText
  }
  return typeof record.rawText === 'string' ? record.rawText : ''
}

function toUtcDate(createdAt: string): string | null {
  if (typeof createdAt !== 'string' || createdAt.length === 0) {
    return null
  }

  const date = new Date(createdAt)
  if (Number.isNaN(date.getTime())) {
    return null
  }

  return date.toISOString().slice(0, 10)
}

function isValidUtcDate(value: string | undefined): value is string {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false
  }

  const date = new Date(`${value}T00:00:00.000Z`)
  return (
    !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
  )
}

function shiftUtcDate(value: string, days: number): string {
  const date = new Date(`${value}T00:00:00.000Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

function applicationKey(
  applicationName: string,
  applicationCategory: string | undefined,
): string {
  return `${applicationName}\u0000${applicationCategory ?? ''}`
}

export function summarizeInsights(
  records: ReadonlyArray<HistoryRecord>,
  options: InsightsOptions = {},
): InsightSummary {
  if (!Array.isArray(records)) {
    throw new TypeError('Insight records must be an array.')
  }

  let totalWords = 0
  let totalDurationMs = 0
  let dictionaryFixes = 0
  let aiFixes = 0
  let successfulDictations = 0
  let errorDictations = 0
  let cancelledDictations = 0

  const applicationMap = new Map<
    string,
    {
      applicationName: string
      applicationCategory?: string
      dictationCount: number
      wordCount: number
    }
  >()
  const activityMap = new Map<
    string,
    { dictationCount: number; wordCount: number; durationMs: number }
  >()
  const activityDates = new Set<string>()

  for (const record of records) {
    const wordCount = countWords(transcriptForAnalytics(record))
    const durationMs = nonNegativeNumber(record.durationMs)
    const dictionaryFixCount = nonNegativeInteger(record.dictionaryFixCount)
    const aiFixCount = nonNegativeInteger(record.aiFixCount)

    totalWords += wordCount
    totalDurationMs += durationMs
    dictionaryFixes += dictionaryFixCount
    aiFixes += aiFixCount

    switch (record.status) {
      case 'error':
        errorDictations += 1
        break
      case 'cancelled':
        cancelledDictations += 1
        break
      case 'success':
      default:
        successfulDictations += 1
        break
    }

    const applicationNameValue = record.applicationName ?? record.application
    const applicationName =
      typeof applicationNameValue === 'string' && applicationNameValue.trim()
        ? applicationNameValue.trim()
        : 'Unknown application'
    const applicationCategoryValue = record.applicationCategory ?? record.category
    const applicationCategory =
      typeof applicationCategoryValue === 'string' &&
      applicationCategoryValue.trim()
        ? applicationCategoryValue.trim()
        : undefined
    const key = applicationKey(applicationName, applicationCategory)
    const application = applicationMap.get(key) ?? {
      applicationName,
      applicationCategory,
      dictationCount: 0,
      wordCount: 0,
    }
    application.dictationCount += 1
    application.wordCount += wordCount
    applicationMap.set(key, application)

    const date = toUtcDate(record.createdAt)
    if (date) {
      activityDates.add(date)
      const activity = activityMap.get(date) ?? {
        dictationCount: 0,
        wordCount: 0,
        durationMs: 0,
      }
      activity.dictationCount += 1
      activity.wordCount += wordCount
      activity.durationMs += durationMs
      activityMap.set(date, activity)
    }
  }

  const totalDictations = records.length
  const totalDurationMinutes = roundToTwoDecimalPlaces(totalDurationMs / 60_000)
  const averageWordsPerDictation =
    totalDictations === 0
      ? 0
      : roundToTwoDecimalPlaces(totalWords / totalDictations)
  const applicationUsage: ApplicationInsight[] = [...applicationMap.values()]
    .map((application) => ({
      ...application,
      percentage: roundToTwoDecimalPlaces(
        (application.wordCount / (totalWords || totalDictations)) * 100,
      ),
    }))
    .sort(
      (left, right) =>
        right.wordCount - left.wordCount ||
        right.dictationCount - left.dictationCount ||
        (left.applicationName < right.applicationName
          ? -1
          : left.applicationName > right.applicationName
            ? 1
            : 0),
    )

  const activityByDay: ActivityDayInsight[] = [...activityMap.entries()]
    .sort(([left], [right]) => (left < right ? -1 : left > right ? 1 : 0))
    .map(([date, activity]) => ({ date, ...activity }))

  const sortedActivityDates = [...activityDates].sort()
  const latestActivityDate = sortedActivityDates.at(-1)
  const asOfDate = isValidUtcDate(options.asOfDate)
    ? options.asOfDate
    : latestActivityDate

  let currentStreakDays = 0
  if (asOfDate) {
    let cursor = asOfDate
    while (activityDates.has(cursor)) {
      currentStreakDays += 1
      cursor = shiftUtcDate(cursor, -1)
    }
  }

  let longestStreakDays = 0
  let runningStreakDays = 0
  let previousDate: string | undefined
  for (const date of sortedActivityDates) {
    runningStreakDays =
      previousDate && shiftUtcDate(previousDate, 1) === date
        ? runningStreakDays + 1
        : 1
    longestStreakDays = Math.max(longestStreakDays, runningStreakDays)
    previousDate = date
  }

  return {
    totalDictations,
    totalWords,
    totalDurationMs,
    totalDurationMinutes,
    averageWpm: calculateWpm(totalWords, totalDurationMs),
    averageWordsPerDictation,
    totalFixes: dictionaryFixes + aiFixes,
    dictionaryFixes,
    aiFixes,
    successfulDictations,
    errorDictations,
    cancelledDictations,
    applicationUsage,
    activityByDay,
    currentStreakDays,
    longestStreakDays,
  }
}
