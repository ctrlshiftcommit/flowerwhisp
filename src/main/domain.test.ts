import { describe, expect, it } from 'vitest'

import type { DictationState, HistoryRecord } from '../shared/domain'
import {
  applyDictionary,
  calculateWpm,
  countWords,
  createStateMachine,
  summarizeInsights,
} from './domain'

describe('dictation state machine', () => {
  it('accepts the lifecycle path and rejects an impossible edge without changing state', () => {
    const machine = createStateMachine()

    expect(machine.state).toBe('idle')
    expect(machine.canTransition('starting')).toBe(true)
    expect(machine.canTransition('recording')).toBe(false)

    const rejected = machine.transition('recording')
    expect(rejected).toMatchObject({
      accepted: false,
      ok: false,
      from: 'idle',
      to: 'recording',
      reason: 'invalid-transition',
    })
    expect(machine.state).toBe('idle')

    machine.transitionOrThrow('starting')
    machine.transitionOrThrow('recording')
    machine.transitionOrThrow('stopping')
    machine.transitionOrThrow('transcribing')
    machine.transitionOrThrow('processing')
    machine.transitionOrThrow('inserting')
    expect(machine.transitionOrThrow('success')).toBe('success')
    expect(machine.transitionOrThrow('idle')).toBe('idle')

    expect(() =>
      machine.transitionOrThrow('success' as DictationState),
    ).toThrowError('Cannot transition dictation state from idle to success.')

    const unknownState = machine.transition('not-a-state' as DictationState)
    expect(unknownState).toMatchObject({
      accepted: false,
      reason: 'unknown-state',
      to: 'not-a-state',
    })
  })
})

describe('dictionary and word calculations', () => {
  it('replaces case-insensitive literal phrases once, longest-first, and reports matches', () => {
    const dictionary = [
      { phrase: 'super base', replacement: 'Supabase' },
      { phrase: 'base', replacement: 'BASE' },
      { phrase: 'disabled', replacement: 'ignored', enabled: false },
    ] as const
    const originalDictionary = dictionary.map((entry) => ({ ...entry }))

    const result = applyDictionary(
      'SUPER BASE and base; the Base and disabled.',
      dictionary,
    )

    expect(result).toEqual({
      text: 'Supabase and BASE; the BASE and disabled.',
      replacementCount: 3,
      replacements: 3,
    })
    expect(dictionary).toEqual(originalDictionary)

    expect(
      applyDictionary('cat', [
        { phrase: 'cat', replacement: 'dog' },
        { phrase: 'dog', replacement: 'wolf' },
      ]),
    ).toEqual({ text: 'dog', replacementCount: 1, replacements: 1 })
  })

  it('counts whitespace-delimited words and calculates speaking-rate WPM', () => {
    expect(countWords('  hello, world!\nthis is FlowerWhisp ')).toBe(5)
    expect(calculateWpm(150, 60_000)).toBe(150)
    expect(calculateWpm('one two', 60_000)).toBe(2)
    expect(calculateWpm(10, 0)).toBe(0)
  })
})

describe('insight summaries', () => {
  it('derives totals, fixes, application usage, activity, and streaks from records', () => {
    const records: HistoryRecord[] = [
      {
        id: 'first',
        createdAt: '2026-08-19T08:00:00.000Z',
        durationMs: 60_000,
        rawText: 'raw first',
        cleanText: 'clean first two',
        finalText: 'first two three',
        wordCount: 999,
        applicationName: 'Editor',
        applicationCategory: 'Documents',
        dictionaryFixCount: 2,
        aiFixCount: 1,
        status: 'success',
      },
      {
        id: 'second',
        createdAt: '2026-08-20T09:00:00.000Z',
        durationMs: 30_000,
        rawText: 'second speech',
        finalText: 'second speech',
        applicationName: 'Browser',
        applicationCategory: 'AI prompts',
        dictionaryFixCount: 1,
        aiFixCount: 3,
        status: 'success',
      },
    ]
    const before = records.map((record) => ({ ...record }))

    const summary = summarizeInsights(records, { asOfDate: '2026-08-20' })

    expect(summary).toMatchObject({
      totalDictations: 2,
      totalWords: 5,
      totalDurationMs: 90_000,
      totalDurationMinutes: 1.5,
      averageWpm: 3.33,
      averageWordsPerDictation: 2.5,
      totalFixes: 7,
      dictionaryFixes: 3,
      aiFixes: 4,
      successfulDictations: 2,
      errorDictations: 0,
      cancelledDictations: 0,
      currentStreakDays: 2,
      longestStreakDays: 2,
    })
    expect(summary.applicationUsage).toEqual([
      {
        applicationName: 'Editor',
        applicationCategory: 'Documents',
        dictationCount: 1,
        wordCount: 3,
        percentage: 60,
      },
      {
        applicationName: 'Browser',
        applicationCategory: 'AI prompts',
        dictationCount: 1,
        wordCount: 2,
        percentage: 40,
      },
    ])
    expect(summary.activityByDay).toEqual([
      {
        date: '2026-08-19',
        dictationCount: 1,
        wordCount: 3,
        durationMs: 60_000,
      },
      {
        date: '2026-08-20',
        dictationCount: 1,
        wordCount: 2,
        durationMs: 30_000,
      },
    ])
    expect(records).toEqual(before)
  })

  it('returns zeroed metrics for an empty history', () => {
    expect(summarizeInsights([])).toEqual({
      totalDictations: 0,
      totalWords: 0,
      totalDurationMs: 0,
      totalDurationMinutes: 0,
      averageWpm: 0,
      averageWordsPerDictation: 0,
      totalFixes: 0,
      dictionaryFixes: 0,
      aiFixes: 0,
      successfulDictations: 0,
      errorDictations: 0,
      cancelledDictations: 0,
      applicationUsage: [],
      activityByDay: [],
      currentStreakDays: 0,
      longestStreakDays: 0,
    })
  })
})
