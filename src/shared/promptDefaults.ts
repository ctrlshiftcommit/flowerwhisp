import type { CleanupLevel } from './promptTypes'

/**
 * Safe starting instructions for each cleanup level. The user can override
 * these values from the Style > Auto cleanup editor; the prompt builder still
 * wraps them with the immutable source-fidelity guardrails.
 */
export const DEFAULT_CLEANUP_PROMPTS: Record<CleanupLevel, string> = {
  none: 'Make no language changes. Return the protected input exactly as supplied, including its wording and ordering.',
  light: 'Make only unambiguous mechanical corrections: punctuation, capitalization, spacing, and obvious transcription artifacts. Keep the original wording and ordering.',
  medium: 'Apply light cleanup, then make locally clear grammar and sentence-flow corrections. Remove only obvious verbal fillers or false starts when doing so cannot change meaning. Do not summarize, expand, or rewrite.',
}
