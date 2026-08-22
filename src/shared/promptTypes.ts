/**
 * Shared contracts for the text-processing prompt pipeline.
 *
 * These types intentionally contain configuration and display text only. A
 * prompt context must never contain provider credentials, request headers, or
 * other secrets.
 */

export const CLEANUP_LEVELS = ['none', 'light', 'medium'] as const

export type CleanupLevel = (typeof CLEANUP_LEVELS)[number]

/** Named access is useful in UI and settings code, while the tuple is useful
 * for validation and iteration. Both values have the same string literals. */
export const cleanupLevels = {
  none: 'none',
  light: 'light',
  medium: 'medium',
} as const satisfies Record<CleanupLevel, CleanupLevel>

export interface PhraseDictionaryEntry {
  /** The literal text produced by the transcription stage. */
  readonly phrase: string
  /** The literal text that deterministic preprocessing must substitute. */
  readonly replacement: string
  /** A stronger display marker for entries that must remain visibly protected. */
  readonly protected?: boolean
  readonly enabled?: boolean
}

/** IPC settings currently call the source phrase `spoken`; both shapes are
 * accepted so prompt construction does not force a cross-layer rename. */
export interface SpokenDictionaryEntry {
  readonly spoken: string
  readonly replacement: string
  readonly protected?: boolean
  readonly enabled?: boolean
}

export type DictionaryEntry = PhraseDictionaryEntry | SpokenDictionaryEntry

export interface PromptStyle {
  readonly name: string
  readonly instructions?: string
  readonly description?: string
  readonly rules?: readonly string[]
  readonly id?: string
}

/** Domain vocabulary retained as an alias for callers that use settings terms. */
export type StyleProfile = PromptStyle

export interface PromptTransform {
  readonly name: string
  readonly description?: string
  readonly instructions?: string
  readonly id?: string
}

/** Domain vocabulary retained as an alias for callers that use settings terms. */
export type Transform = PromptTransform

export type PromptStyleInput = PromptStyle | string
export type PromptTransformInput = PromptTransform | string

export interface PromptContext {
  readonly cleanupLevel: CleanupLevel
  /** Optional user-authored replacement for the selected cleanup level. */
  readonly cleanupInstructions?: string
  readonly language?: string
  readonly style?: PromptStyleInput
  readonly styleId?: string
  readonly styleRules?: readonly string[]
  readonly transform?: PromptTransformInput
  readonly transformId?: string
  readonly transformInstructions?: string
  /** Canonical dictionary field used by the prompt builders. */
  readonly dictionaryEntries?: readonly DictionaryEntry[]
  /** Compatibility alias for callers that already call this setting a dictionary. */
  readonly dictionary?: readonly DictionaryEntry[]
}
