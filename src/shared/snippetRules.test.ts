import { describe, expect, it } from 'vitest'

import type { Snippet } from './ipc'
import {
  normalizeSnippetTrigger,
  SNIPPET_EXPANSION_MAX_LENGTH,
  SNIPPET_TRIGGER_MAX_LENGTH,
  validateSnippetDraft,
} from './snippetRules'

const saved: Snippet = {
  id: 'saved',
  trigger: 'My Signature',
  expansion: 'Best, Tushar',
  enabled: true,
  createdAt: '2026-08-31T00:00:00.000Z',
}

describe('desktop snippet save rules', () => {
  it('normalizes only the spoken trigger', () => {
    expect(normalizeSnippetTrigger('  my   signature  ')).toBe('my signature')
  })

  it('rejects duplicate phrases and duplicate saved blocks while allowing an edit in place', () => {
    expect(validateSnippetDraft({ trigger: 'my  signature', expansion: 'Different' }, [saved]))
      .toBe('That spoken phrase already exists.')
    expect(validateSnippetDraft({ trigger: 'sign-off', expansion: ' Best, Tushar ' }, [saved]))
      .toBe('That snippet text already exists.')
    expect(validateSnippetDraft({ id: 'saved', trigger: saved.trigger, expansion: saved.expansion }, [saved]))
      .toBeUndefined()
  })

  it('enforces the supported phrase and expansion limits', () => {
    expect(validateSnippetDraft({ trigger: 'x'.repeat(SNIPPET_TRIGGER_MAX_LENGTH + 1), expansion: 'Text' }, []))
      .toContain(String(SNIPPET_TRIGGER_MAX_LENGTH))
    expect(validateSnippetDraft({ trigger: 'phrase', expansion: 'x'.repeat(SNIPPET_EXPANSION_MAX_LENGTH + 1) }, []))
      .toContain(String(SNIPPET_EXPANSION_MAX_LENGTH))
  })
})
