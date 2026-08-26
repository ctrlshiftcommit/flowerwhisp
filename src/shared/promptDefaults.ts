import type { CleanupLevel } from './promptTypes'

/**
 * Safe starting instructions for each cleanup level. The user can override
 * these values from the Style > Auto cleanup editor; the prompt builder still
 * wraps them with the immutable source-fidelity guardrails.
 */
export const DEFAULT_CLEANUP_PROMPTS: Record<CleanupLevel, string> = {
  none: 'Do not edit the source text. Preserve every word, repetition, filler, punctuation mark, paragraph break, and ordering exactly as supplied.',
  light: [
    'Turn the raw speech transcript into clean written text with minimal intervention.',
    'Add confident sentence boundaries, punctuation, capitalization, spacing, and paragraph breaks.',
    'Fix only unmistakable transcription artifacts or immediate accidental word repetitions when the intended wording is clear from nearby context.',
    'Keep the speaker\'s vocabulary, contractions, tone, emphasis, ordering, and level of formality. Keep meaningful fillers and self-corrections.',
    'Do not paraphrase, shorten, expand, summarize, reorganize, add headings, or convert prose into a list.',
  ].join(' '),
  medium: [
    'Edit the raw speech transcript into fluent, natural written language while preserving the complete meaning and voice.',
    'Apply all light cleanup. Remove empty speech fillers such as um, uh, and repeated you know when they carry no meaning; collapse obvious stutters and abandoned false starts; repair locally clear grammar and sentence flow.',
    'Use paragraph breaks where the speaker clearly changes thought. Keep an explicit list as a list only when the speaker actually dictates one.',
    'Preserve every fact, name, number, date, qualifier, uncertainty, negation, request, commitment, example, and useful detail. Preserve contractions, informality, profanity, and emotional tone unless the configured style explicitly changes presentation.',
    'Do not summarize, make the speaker more confident, add context, create a greeting or sign-off, or introduce claims that were not spoken.',
  ].join(' '),
}
