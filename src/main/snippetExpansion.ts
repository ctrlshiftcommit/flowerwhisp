import type { Snippet } from '../shared/ipc'
import { normalizeSnippetTrigger } from '../shared/snippetRules'

interface Replacement {
  token: string
  expansion: string
}

interface EligibleSnippet {
  trigger: string
  expansion: string
  order: number
}

/**
 * Keeps user-authored snippet bodies out of deterministic and LLM cleanup.
 * Callers process protectedText, then restore the exact local expansion.
 */
export class ProtectedSnippetExpansion {
  public readonly replacementCount: number
  private readonly orderedReplacements: readonly Replacement[]

  public constructor(
    public readonly protectedText: string,
    replacements: readonly Replacement[],
    private readonly markerNamespace: string,
  ) {
    this.orderedReplacements = [...replacements].sort((left, right) =>
      protectedText.indexOf(left.token) - protectedText.indexOf(right.token)
    )
    this.replacementCount = this.orderedReplacements.length
  }

  /**
   * Returns null if cleanup removed, duplicated, reordered, changed, or invented
   * one of this plan's protected markers.
   */
  public restore(candidate: string): string | null {
    if (this.orderedReplacements.length === 0) return candidate

    let cursor = 0
    for (const replacement of this.orderedReplacements) {
      const occurrence = candidate.indexOf(replacement.token, cursor)
      if (occurrence < 0) return null
      if (candidate.indexOf(replacement.token, occurrence + replacement.token.length) >= 0) return null
      cursor = occurrence + replacement.token.length
    }

    let withoutKnownMarkers = candidate
    for (const replacement of this.orderedReplacements) {
      withoutKnownMarkers = withoutKnownMarkers.replace(replacement.token, '')
    }
    if (withoutKnownMarkers.includes(`[[${this.markerNamespace}`)) return null

    let restored = candidate
    for (const replacement of this.orderedReplacements) {
      restored = restored.replace(replacement.token, replacement.expansion)
    }
    return restored
  }

  /** Applies deterministic cleanup only around markers, never inside saved snippet text. */
  public mapUnprotected(transform: (segment: string) => string): string {
    if (this.orderedReplacements.length === 0) return transform(this.protectedText)

    let cursor = 0
    let result = ''
    for (const replacement of this.orderedReplacements) {
      const occurrence = this.protectedText.indexOf(replacement.token, cursor)
      if (occurrence < 0) throw new Error('Protected snippet marker is missing from the local plan.')
      result += transform(this.protectedText.slice(cursor, occurrence))
      result += replacement.token
      cursor = occurrence + replacement.token.length
    }
    return result + transform(this.protectedText.slice(cursor))
  }
}

export function protectSnippets(
  source: string,
  snippets: readonly Snippet[],
): ProtectedSnippetExpansion {
  if (typeof source !== 'string') throw new TypeError('Snippet input text must be a string.')

  const eligible = normalizeSnippets(snippets)
  const markerNamespace = collisionFreeNamespace(source)
  if (!source || eligible.length === 0) {
    return new ProtectedSnippetExpansion(source, [], markerNamespace)
  }

  const wholeDictation = eligible.find((snippet) =>
    fullDictationPattern(triggerAliases(snippet.trigger)).test(source)
  )
  if (wholeDictation) {
    const marker = token(markerNamespace, 0)
    return new ProtectedSnippetExpansion(
      marker,
      [{ token: marker, expansion: wholeDictation.expansion }],
      markerNamespace,
    )
  }

  let nextToken = 0
  let protectedText = source
  const replacements: Replacement[] = []
  for (const snippet of eligible) {
    protectedText = protectedText.replace(phrasePattern(triggerAliases(snippet.trigger)), () => {
      const marker = token(markerNamespace, nextToken)
      nextToken += 1
      replacements.push({ token: marker, expansion: snippet.expansion })
      return marker
    })
  }
  return new ProtectedSnippetExpansion(protectedText, replacements, markerNamespace)
}

function normalizeSnippets(snippets: readonly Snippet[]): EligibleSnippet[] {
  const eligible: EligibleSnippet[] = []
  snippets.forEach((snippet, order) => {
    if (
      !snippet ||
      typeof snippet.trigger !== 'string' ||
      typeof snippet.expansion !== 'string' ||
      snippet.enabled === false ||
      !snippet.trigger.trim() ||
      !snippet.expansion.trim()
    ) {
      return
    }
    eligible.push({
      trigger: normalizeSnippetTrigger(snippet.trigger),
      expansion: snippet.expansion,
      order,
    })
  })

  eligible.sort((left, right) =>
    right.trigger.length - left.trigger.length || left.order - right.order
  )
  const unique = new Map<string, EligibleSnippet>()
  for (const snippet of eligible) {
    const key = snippet.trigger.toLocaleLowerCase()
    if (!unique.has(key)) unique.set(key, snippet)
  }
  return [...unique.values()]
}

function triggerAliases(trigger: string): string[] {
  const normalized = normalizeSnippetTrigger(trigger)
  const prefix = SPOKEN_PREFIXES.find(([symbol]) => normalized.startsWith(symbol))
  if (!prefix) return [normalized]

  const [symbol, spoken] = prefix
  const remainder = normalized.slice(symbol.length).trimStart()
  if (!remainder) return [normalized]
  return [normalized, `${symbol} ${remainder}`, `${spoken} ${remainder}`]
}

function phrasePattern(aliases: readonly string[]): RegExp {
  return new RegExp(
    `(?<![\\p{L}\\p{N}_])(?:${aliasAlternatives(aliases)})(?![\\p{L}\\p{N}_])`,
    'giu',
  )
}

function fullDictationPattern(aliases: readonly string[]): RegExp {
  return new RegExp(`^\\s*(?:${aliasAlternatives(aliases)})(?:[.!?。！？])?\\s*$`, 'iu')
}

function aliasAlternatives(aliases: readonly string[]): string {
  return [...new Set(aliases.map(normalizeSnippetTrigger).filter(Boolean))]
    .sort((left, right) => right.length - left.length)
    .map((alias) => alias.split(/\s+/u).map(escapeRegExp).join('\\s+'))
    .join('|')
}

function collisionFreeNamespace(source: string): string {
  let suffix = 0
  let namespace = BASE_NAMESPACE
  while (source.includes(`[[${namespace}`)) {
    suffix += 1
    namespace = `${BASE_NAMESPACE}_SAFE_${suffix}`
  }
  return namespace
}

function token(namespace: string, index: number): string {
  return `[[${namespace}_${String(index).padStart(4, '0')}]]`
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

const BASE_NAMESPACE = 'FLOWERWHISP_SNIPPET'
const SPOKEN_PREFIXES = [
  ['/', 'slash'],
  [';', 'semicolon'],
  [':', 'colon'],
  ['#', 'hash'],
  ['@', 'at'],
  ['&', 'ampersand'],
] as const
