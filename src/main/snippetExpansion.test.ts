import { describe, expect, it } from 'vitest'

import type { Snippet } from '../shared/ipc'
import { protectSnippets } from './snippetExpansion'

const snippet = (trigger: string, expansion: string, enabled = true): Snippet => ({
  id: trigger,
  trigger,
  expansion,
  enabled,
  createdAt: '2026-08-31T00:00:00.000Z',
})

describe('protected desktop snippet expansion', () => {
  it('matches a natural phrase case-insensitively without matching inside another word', () => {
    const plan = protectSnippets(
      'Send this to MY ADDRESS, not my addressable contact',
      [snippet('my address', '123 Main Street')],
    )

    expect(plan.replacementCount).toBe(1)
    expect(plan.protectedText).not.toContain('123 Main Street')
    expect(plan.restore(plan.protectedText)).toBe(
      'Send this to 123 Main Street, not my addressable contact',
    )
  })

  it('supports spoken aliases for legacy symbolic triggers', () => {
    const plan = protectSnippets('slash sig.', [snippet('/sig', 'Best,\nTushar')])

    expect(plan.restore(plan.protectedText)).toBe('Best,\nTushar')
  })

  it('never applies deterministic changes inside the saved expansion', () => {
    const plan = protectSnippets(
      'please add my block now',
      [snippet('my block', 'please KEEP this\nExact')],
    )
    const transformed = plan.mapUnprotected((segment) => segment.toUpperCase())

    expect(plan.restore(transformed)).toBe('PLEASE ADD please KEEP this\nExact NOW')
  })

  it('expands original occurrences without recursively expanding saved content', () => {
    const plan = protectSnippets('first and second', [
      snippet('first', 'second'),
      snippet('second', 'expanded again'),
    ])

    expect(plan.replacementCount).toBe(2)
    expect(plan.restore(plan.protectedText)).toBe('second and expanded again')
  })

  it('rejects cleanup that removes, duplicates, reorders, or invents markers', () => {
    const plan = protectSnippets('first then second', [
      snippet('first', 'ONE'),
      snippet('second', 'TWO'),
    ])
    const tokens = plan.protectedText.match(/\[\[FLOWERWHISP_SNIPPET[^\]]+\]\]/gu) ?? []
    const firstToken = tokens[0]
    const secondToken = tokens[1]

    expect(tokens).toHaveLength(2)
    expect(firstToken).toBeDefined()
    expect(secondToken).toBeDefined()
    expect(plan.restore(plan.protectedText)).toBe('ONE then TWO')
    expect(plan.restore(plan.protectedText.replace(firstToken!, ''))).toBeNull()
    expect(plan.restore(`${plan.protectedText} ${firstToken}`)).toBeNull()
    expect(plan.restore(`${secondToken} then ${firstToken}`)).toBeNull()
    expect(plan.restore(`${plan.protectedText} [[FLOWERWHISP_SNIPPET_9999]]`)).toBeNull()
  })

  it('ignores disabled and malformed persisted snippets safely', () => {
    const malformed = { id: 'bad', enabled: true } as Snippet
    const plan = protectSnippets('disabled phrase', [
      snippet('disabled phrase', 'wrong', false),
      malformed,
    ])

    expect(plan.replacementCount).toBe(0)
    expect(plan.restore(plan.protectedText)).toBe('disabled phrase')
  })
})
