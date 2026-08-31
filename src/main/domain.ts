import {
  DICTATION_TRANSITIONS,
  isDictationState,
} from '../shared/domain'
import type {
  ActivityDayInsight,
  ApplicationInsight,
  CategoryInsight,
  DayPart,
  DayPartInsight,
  DictionaryApplicationResult,
  DictionaryEntry,
  DictionaryInput,
  DictationState,
  DictationStateMachine,
  HistoryRecord,
  InsightSummary,
  InsightsOptions,
  PeriodInsight,
  RejectedStateTransition,
  StateTransitionResult,
  WritingInsightCategory,
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

function recordWordCount(record: HistoryRecord): number {
  const transcriptCount = countWords(transcriptForAnalytics(record))
  return transcriptCount > 0
    ? transcriptCount
    : nonNegativeInteger(record.wordCount)
}

function toCalendarDate(createdAt: string): string | null {
  if (typeof createdAt !== 'string' || createdAt.length === 0) {
    return null
  }

  const date = new Date(createdAt)
  if (Number.isNaN(date.getTime())) {
    return null
  }

  return date.toISOString().slice(0, 10)
}

function isValidCalendarDate(value: string | undefined): value is string {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false
  }

  const date = new Date(`${value}T00:00:00.000Z`)
  return (
    !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
  )
}

function shiftCalendarDate(value: string, days: number): string {
  const date = new Date(`${value}T00:00:00.000Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

const insightCategories: readonly WritingInsightCategory[] = [
  'personal',
  'work',
  'email',
  'other',
]

const insightDayParts: readonly DayPart[] = [
  'morning',
  'afternoon',
  'evening',
  'night',
]

interface MutableActivityDay {
  date: string
  dictationCount: number
  wordCount: number
  durationMs: number
}

function normalizeInsightCategory(value: string | undefined): WritingInsightCategory {
  const normalized = value?.trim().toLowerCase() ?? ''
  if (normalized.includes('personal')) return 'personal'
  if (normalized.includes('work')) return 'work'
  if (normalized.includes('email')) return 'email'
  return 'other'
}

function isKnownApplication(value: string): boolean {
  const normalized = value.trim().toLowerCase()
  return Boolean(normalized) &&
    normalized !== 'unknown application' &&
    normalized !== 'target not verified'
}

function hourInTimeZone(createdAt: string, timeZone: string): number | null {
  const date = new Date(createdAt)
  if (Number.isNaN(date.getTime())) return null

  try {
    const hour = new Intl.DateTimeFormat('en-US', {
      hour: 'numeric',
      hourCycle: 'h23',
      timeZone,
    }).formatToParts(date).find((part) => part.type === 'hour')?.value
    const parsed = Number(hour)
    return Number.isFinite(parsed) ? parsed : null
  } catch {
    return date.getUTCHours()
  }
}

function dayPartForHour(hour: number): DayPart {
  if (hour >= 5 && hour < 12) return 'morning'
  if (hour >= 12 && hour < 17) return 'afternoon'
  if (hour >= 17 && hour < 21) return 'evening'
  return 'night'
}

function insightPercentage(
  wordCount: number,
  dictationCount: number,
  totalWords: number,
  totalDictations: number,
): number {
  const numerator = totalWords > 0 ? wordCount : dictationCount
  const denominator = totalWords > 0 ? totalWords : totalDictations
  return denominator > 0
    ? roundToTwoDecimalPlaces((numerator / denominator) * 100)
    : 0
}

function periodInsight(
  activityMap: ReadonlyMap<string, ActivityDayInsight>,
  endDate: string,
  daysBeforeEnd: number,
): PeriodInsight {
  const periodEnd = shiftCalendarDate(endDate, -daysBeforeEnd)
  const startDate = shiftCalendarDate(periodEnd, -6)
  let dictationCount = 0
  let wordCount = 0
  let durationMs = 0

  for (let offset = 0; offset < 7; offset += 1) {
    const activity = activityMap.get(shiftCalendarDate(startDate, offset))
    if (!activity) continue
    dictationCount += activity.dictationCount
    wordCount += activity.wordCount
    durationMs += activity.durationMs
  }

  return { startDate, endDate: periodEnd, dictationCount, wordCount, durationMs }
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

  let dictionaryFixes = 0
  let aiFixes = 0
  let retainedSuccessfulDictations = 0
  let errorDictations = 0
  let cancelledDictations = 0
  let retainedWords = 0
  let retainedDurationMs = 0
  let longestSessionMs = 0
  let insertedDictations = 0
  let clipboardFallbacks = 0
  let scratchpadSaves = 0
  let failedInsertions = 0
  let unattemptedInsertions = 0
  let cleanupApplied = 0
  let cleanupUnchanged = 0
  let cleanupFailed = 0
  let cleanupDisabled = 0
  const timeZone = options.timeZone ?? 'UTC'

  const applicationMap = new Map<
    string,
    {
      applicationName: string
      applicationCategory?: string
      dictationCount: number
      wordCount: number
      durationMs: number
    }
  >()
  const categoryMap = new Map<WritingInsightCategory, {
    dictationCount: number
    wordCount: number
    durationMs: number
  }>()
  const dayPartMap = new Map<DayPart, {
    dictationCount: number
    wordCount: number
    durationMs: number
  }>()
  const recordActivityMap = new Map<string, MutableActivityDay>()

  for (const record of records) {
    const wordCount = recordWordCount(record)
    const durationMs = nonNegativeNumber(record.durationMs)

    switch (record.status) {
      case 'error':
        errorDictations += 1
        continue
      case 'cancelled':
        cancelledDictations += 1
        continue
      case 'success':
      default:
        retainedSuccessfulDictations += 1
        break
    }

    retainedWords += wordCount
    retainedDurationMs += durationMs
    longestSessionMs = Math.max(longestSessionMs, durationMs)
    dictionaryFixes += nonNegativeInteger(record.dictionaryFixCount)
    aiFixes += nonNegativeInteger(record.aiFixCount)

    switch (record.insertionOutcome) {
      case 'inserted': insertedDictations += 1; break
      case 'copied': clipboardFallbacks += 1; break
      case 'scratchpad': scratchpadSaves += 1; break
      case 'failed': failedInsertions += 1; break
      case 'not-attempted': unattemptedInsertions += 1; break
    }

    const cleanupStatus = record.cleanupStatus ??
      (record.cleanupLevel === 'none' || record.llmProvider === 'none'
        ? 'disabled'
        : undefined)
    switch (cleanupStatus) {
      case 'applied': cleanupApplied += 1; break
      case 'unchanged': cleanupUnchanged += 1; break
      case 'failed': cleanupFailed += 1; break
      case 'disabled': cleanupDisabled += 1; break
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
    if (isKnownApplication(applicationName)) {
      const key = applicationKey(applicationName, applicationCategory)
      const application = applicationMap.get(key) ?? {
        applicationName,
        applicationCategory,
        dictationCount: 0,
        wordCount: 0,
        durationMs: 0,
      }
      application.dictationCount += 1
      application.wordCount += wordCount
      application.durationMs += durationMs
      applicationMap.set(key, application)
    }

    const category = normalizeInsightCategory(applicationCategory)
    const categoryInsight = categoryMap.get(category) ?? {
      dictationCount: 0,
      wordCount: 0,
      durationMs: 0,
    }
    categoryInsight.dictationCount += 1
    categoryInsight.wordCount += wordCount
    categoryInsight.durationMs += durationMs
    categoryMap.set(category, categoryInsight)

    const hour = hourInTimeZone(record.createdAt, timeZone)
    if (hour !== null) {
      const part = dayPartForHour(hour)
      const dayPart = dayPartMap.get(part) ?? {
        dictationCount: 0,
        wordCount: 0,
        durationMs: 0,
      }
      dayPart.dictationCount += 1
      dayPart.wordCount += wordCount
      dayPart.durationMs += durationMs
      dayPartMap.set(part, dayPart)
    }

    const date = toCalendarDate(record.createdAt)
    if (date) {
      const activity = recordActivityMap.get(date) ?? {
        date,
        dictationCount: 0,
        wordCount: 0,
        durationMs: 0,
      }
      activity.dictationCount += 1
      activity.wordCount += wordCount
      activity.durationMs += durationMs
      recordActivityMap.set(date, activity)
    }
  }

  const usageMap = new Map<string, MutableActivityDay>()
  for (const usage of options.usage ?? []) {
    if (!isValidCalendarDate(usage.date)) continue
    const current = usageMap.get(usage.date) ?? {
      date: usage.date,
      dictationCount: 0,
      wordCount: 0,
      durationMs: 0,
    }
    current.dictationCount += nonNegativeInteger(usage.dictations)
    current.wordCount += nonNegativeInteger(usage.words)
    current.durationMs += nonNegativeNumber(usage.durationMs)
    usageMap.set(usage.date, current)
  }

  const allActivityDates = new Set([
    ...recordActivityMap.keys(),
    ...usageMap.keys(),
  ])
  const activityMap = new Map<string, ActivityDayInsight>()
  for (const date of allActivityDates) {
    const recordActivity = recordActivityMap.get(date)
    const usageActivity = usageMap.get(date)
    activityMap.set(date, {
      date,
      dictationCount: Math.max(
        recordActivity?.dictationCount ?? 0,
        usageActivity?.dictationCount ?? 0,
      ),
      wordCount: Math.max(
        recordActivity?.wordCount ?? 0,
        usageActivity?.wordCount ?? 0,
      ),
      durationMs: Math.max(
        recordActivity?.durationMs ?? 0,
        usageActivity?.durationMs ?? 0,
      ),
    })
  }

  const activityByDay: ActivityDayInsight[] = [...activityMap.values()]
    .filter((activity) =>
      activity.dictationCount > 0 ||
      activity.wordCount > 0 ||
      activity.durationMs > 0,
    )
    .sort((left, right) =>
      left.date < right.date ? -1 : left.date > right.date ? 1 : 0,
    )

  const activityDictations = activityByDay.reduce(
    (sum, activity) => sum + activity.dictationCount,
    0,
  )
  const activityWords = activityByDay.reduce(
    (sum, activity) => sum + activity.wordCount,
    0,
  )
  const activityDurationMs = activityByDay.reduce(
    (sum, activity) => sum + activity.durationMs,
    0,
  )
  const successfulDictations = Math.max(
    retainedSuccessfulDictations,
    activityDictations,
  )
  const totalDictations = successfulDictations
  const totalWords = Math.max(retainedWords, activityWords)
  const totalDurationMs = Math.max(retainedDurationMs, activityDurationMs)
  const totalDurationMinutes = roundToTwoDecimalPlaces(totalDurationMs / 60_000)
  const averageWordsPerDictation =
    successfulDictations === 0
      ? 0
      : roundToTwoDecimalPlaces(totalWords / successfulDictations)

  const knownApplicationTotals = [...applicationMap.values()].reduce(
    (totals, application) => ({
      words: totals.words + application.wordCount,
      dictations: totals.dictations + application.dictationCount,
    }),
    { words: 0, dictations: 0 },
  )
  const applicationUsage: ApplicationInsight[] = [...applicationMap.values()]
    .map((application) => ({
      ...application,
      percentage: insightPercentage(
        application.wordCount,
        application.dictationCount,
        knownApplicationTotals.words,
        knownApplicationTotals.dictations,
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

  const categoryTotals = [...categoryMap.values()].reduce(
    (totals, category) => ({
      words: totals.words + category.wordCount,
      dictations: totals.dictations + category.dictationCount,
    }),
    { words: 0, dictations: 0 },
  )
  const categoryUsage: CategoryInsight[] = insightCategories.map((category) => {
    const insight = categoryMap.get(category) ?? {
      dictationCount: 0,
      wordCount: 0,
      durationMs: 0,
    }
    return {
      category,
      ...insight,
      percentage: insightPercentage(
        insight.wordCount,
        insight.dictationCount,
        categoryTotals.words,
        categoryTotals.dictations,
      ),
    }
  })

  const dayPartTotals = [...dayPartMap.values()].reduce(
    (totals, part) => ({
      words: totals.words + part.wordCount,
      dictations: totals.dictations + part.dictationCount,
    }),
    { words: 0, dictations: 0 },
  )
  const dayPartUsage: DayPartInsight[] = insightDayParts.map((part) => {
    const insight = dayPartMap.get(part) ?? {
      dictationCount: 0,
      wordCount: 0,
      durationMs: 0,
    }
    return {
      part,
      ...insight,
      percentage: insightPercentage(
        insight.wordCount,
        insight.dictationCount,
        dayPartTotals.words,
        dayPartTotals.dictations,
      ),
    }
  })

  const activityDates = new Set(activityByDay.map((activity) => activity.date))
  const sortedActivityDates = [...activityDates].sort()
  const latestActivityDate = sortedActivityDates.at(-1)
  const asOfDate = isValidCalendarDate(options.asOfDate)
    ? options.asOfDate
    : latestActivityDate

  let currentStreakDays = 0
  if (asOfDate) {
    let cursor = activityDates.has(asOfDate)
      ? asOfDate
      : shiftCalendarDate(asOfDate, -1)
    while (activityDates.has(cursor)) {
      currentStreakDays += 1
      cursor = shiftCalendarDate(cursor, -1)
    }
  }

  let longestStreakDays = 0
  let runningStreakDays = 0
  let previousDate: string | undefined
  for (const date of sortedActivityDates) {
    runningStreakDays =
      previousDate && shiftCalendarDate(previousDate, 1) === date
        ? runningStreakDays + 1
        : 1
    longestStreakDays = Math.max(longestStreakDays, runningStreakDays)
    previousDate = date
  }

  const recentDayCount = Math.min(
    90,
    Math.max(1, nonNegativeInteger(options.recentDayCount) || 14),
  )
  const recentDays = asOfDate
    ? Array.from({ length: recentDayCount }, (_, index) => {
        const date = shiftCalendarDate(asOfDate, index - recentDayCount + 1)
        return activityMap.get(date) ?? {
          date,
          dictationCount: 0,
          wordCount: 0,
          durationMs: 0,
        }
      })
    : []
  const currentPeriod = asOfDate
    ? periodInsight(activityMap, asOfDate, 0)
    : null
  const previousPeriod = asOfDate
    ? periodInsight(activityMap, asOfDate, 7)
    : null
  const wordTrendPercent = currentPeriod && previousPeriod?.wordCount
    ? roundToTwoDecimalPlaces(
        ((currentPeriod.wordCount - previousPeriod.wordCount) /
          previousPeriod.wordCount) * 100,
      )
    : null
  const bestDay = activityByDay.reduce<ActivityDayInsight | null>(
    (best, activity) => {
      if (!best) return activity
      if (activity.wordCount !== best.wordCount) {
        return activity.wordCount > best.wordCount ? activity : best
      }
      if (activity.dictationCount !== best.dictationCount) {
        return activity.dictationCount > best.dictationCount ? activity : best
      }
      return activity.date > best.date ? activity : best
    },
    null,
  )

  return {
    totalDictations,
    totalWords,
    estimatedTokens: Math.ceil((totalWords * 4) / 3),
    totalDurationMs,
    totalDurationMinutes,
    averageWpm: calculateWpm(totalWords, totalDurationMs),
    averageWordsPerDictation,
    averageSessionDurationMs: successfulDictations > 0
      ? Math.round(totalDurationMs / successfulDictations)
      : 0,
    longestSessionMs,
    activeDays: activityByDay.length,
    totalFixes: dictionaryFixes + aiFixes,
    dictionaryFixes,
    aiFixes,
    successfulDictations,
    errorDictations,
    cancelledDictations,
    insertedDictations,
    clipboardFallbacks,
    scratchpadSaves,
    failedInsertions,
    unattemptedInsertions,
    cleanupApplied,
    cleanupUnchanged,
    cleanupFailed,
    cleanupDisabled,
    applicationUsage,
    categoryUsage,
    dayPartUsage,
    activityByDay,
    recentDays,
    currentPeriod,
    previousPeriod,
    wordTrendPercent,
    bestDay,
    currentStreakDays,
    longestStreakDays,
    asOfDate: asOfDate ?? null,
  }
}
