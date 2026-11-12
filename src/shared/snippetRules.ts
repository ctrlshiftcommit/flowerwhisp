import type { Snippet } from './ipc'

export const SNIPPET_TRIGGER_MAX_LENGTH = 60
export const SNIPPET_EXPANSION_MAX_LENGTH = 4_000

export type SnippetDraft = Pick<Snippet, 'trigger' | 'expansion'> & { id?: string }

export function normalizeSnippetTrigger(value: string): string {
  return value.trim().replace(/\s+/gu, ' ')
}

export function validateSnippetDraft(
  draft: SnippetDraft,
  existing: readonly Snippet[],
): string | undefined {
  const trigger = normalizeSnippetTrigger(draft.trigger)
  if (!trigger) return 'Add a spoken phrase.'
  if (trigger.length > SNIPPET_TRIGGER_MAX_LENGTH) {
    return `Spoken phrases can be up to ${SNIPPET_TRIGGER_MAX_LENGTH} characters.`
  }
  if (!draft.expansion.trim()) return 'Add the text to insert.'
  if (draft.expansion.length > SNIPPET_EXPANSION_MAX_LENGTH) {
    return `Snippet text can be up to ${SNIPPET_EXPANSION_MAX_LENGTH} characters.`
  }

  const canonicalTrigger = trigger.toLowerCase()
  const otherEntries = existing.filter((entry) => !draft.id || entry.id !== draft.id)
  if (otherEntries.some((entry) =>
    typeof entry.trigger === 'string' &&
    normalizeSnippetTrigger(entry.trigger).toLowerCase() === canonicalTrigger
  )) {
    return 'That spoken phrase already exists.'
  }
  if (otherEntries.some((entry) =>
    typeof entry.expansion === 'string' && entry.expansion.trim() === draft.expansion.trim()
  )) {
    return 'That snippet text already exists.'
  }
  return undefined
}
