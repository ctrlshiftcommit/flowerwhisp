import { mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import path from 'node:path'

import type {
  CleanupLevel,
  DictionaryEntry,
  DictationRecord,
  PublicSettings,
  RecoveryRecording,
  Snippet,
  StyleProfile,
  TransformProfile,
  UsageDay,
} from '../../shared/ipc'
import {
  DEFAULT_HOLD_SHORTCUT,
  DEFAULT_SHORTCUT_BINDINGS,
  DEFAULT_TOGGLE_SHORTCUT,
  isValidHoldShortcut,
  isValidShortcut,
  normalizeShortcutBindings,
} from '../../shared/shortcuts'
import { DEFAULT_CLEANUP_PROMPTS } from '../../shared/promptDefaults'

const LEGACY_CLEANUP_PROMPTS: Record<CleanupLevel, string> = {
  none: 'Make no language changes. Return the protected input exactly as supplied, including its wording and ordering.',
  light: 'Make only unambiguous mechanical corrections: punctuation, capitalization, spacing, and obvious transcription artifacts. Keep the original wording and ordering.',
  medium: 'Apply light cleanup, then make locally clear grammar and sentence-flow corrections. Remove only obvious verbal fillers or false starts when doing so cannot change meaning. Do not summarize, expand, or rewrite.',
}

const LEGACY_TRANSFORM_INSTRUCTIONS: Record<string, string> = {
  polish: 'Improve clarity and concision. Preserve every fact, qualifier, and detail.',
  'prompt-engineer': 'Clarify the request, constraints, and desired output using only the supplied text.',
}

export interface AppSnapshot {
  version: 1
  settings: PublicSettings
  records: DictationRecord[]
  dictionary: DictionaryEntry[]
  snippets: Snippet[]
  styles: StyleProfile[]
  transforms: TransformProfile[]
  recoveries: RecoveryRecording[]
  usage: UsageDay[]
  scratchpad: string
}

export const defaultSettings: PublicSettings = {
  transcriptionProvider: 'groq',
  transcriptionModel: 'whisper-large-v3-turbo',
  llmProvider: 'none',
  llmModel: 'openai/gpt-oss-20b',
  language: 'en',
  cleanupLevel: 'light',
  cleanupPrompts: { ...DEFAULT_CLEANUP_PROMPTS },
  defaultStyle: 'personal-casual',
  // Hold and toggle are deliberately separate: hold needs native key-up
  // delivery, while toggle is a one-shot accelerator pressed a second time.
  holdShortcut: DEFAULT_HOLD_SHORTCUT,
  toggleShortcut: DEFAULT_TOGGLE_SHORTCUT,
  shortcutBindings: Object.fromEntries(Object.entries(DEFAULT_SHORTCUT_BINDINGS).map(([action, bindings]) => [action, [...bindings]])) as PublicSettings['shortcutBindings'],
  microphoneLabel: 'System default microphone',
  localCommand: '',
  localWorkingDirectory: '',
  launchAtLogin: false,
  showPill: true,
  pillPosition: 'center',
  showInDock: true,
  playSounds: false,
  muteMusicWhileDictating: false,
  theme: 'light',
  retention: 'forever',
}

export const defaultStyles: StyleProfile[] = [
  {
    id: 'personal-casual',
    name: 'Personal casual',
    description: 'Conversational, clear, and lightly punctuated.',
    example: 'Hey, are we still on for coffee tomorrow?',
    rules: ['Keep the tone conversational.', 'Use normal capitalization.', 'Do not add formality.'],
    category: 'personal',
  },
  {
    id: 'work-clear',
    name: 'Work clear',
    description: 'Direct and easy to scan without sounding stiff.',
    example: 'I will share the revised notes by Thursday afternoon.',
    rules: ['Prefer direct sentences.', 'Keep useful context.', 'Do not add a sign-off.'],
    category: 'work',
  },
  {
    id: 'email-formal',
    name: 'Email formal',
    description: 'Polished email wording with the speaker’s meaning intact.',
    example: 'Thank you for the update. I will review the proposal and reply by Friday.',
    rules: ['Use complete sentences.', 'Keep a respectful tone.', 'Do not invent a greeting or conclusion.'],
    category: 'email',
  },
]

export const defaultTransforms: TransformProfile[] = [
  {
    id: 'polish',
    name: 'Polish',
    description: 'Copy-edit selected text for clarity while preserving its meaning and voice.',
    instructions: 'Polish the selected text as a careful copy editor. Correct grammar, punctuation, awkward phrasing, and unnecessary repetition. Improve clarity and concision without changing the speaker\'s tone, intent, certainty, formatting, or level of formality. Preserve every fact, name, number, qualifier, request, commitment, example, and useful detail. Do not summarize, add claims, add a greeting or sign-off, or make the text sound generic or corporate.',
    shortcut: 'Super+Alt+C',
    enabled: true,
    builtIn: true,
  },
  {
    id: 'prompt-engineer',
    name: 'Prompt engineer',
    description: 'Turn selected text into a precise, reusable prompt without inventing context.',
    instructions: 'Rewrite the selected text as a clear, executable prompt. Preserve the user\'s actual objective, context, requirements, constraints, examples, tools, audience, tone, and requested output. Organize those elements in the order that makes the task easiest to follow. State success criteria only when the source supports them. Do not invent requirements, technologies, facts, deadlines, or preferences. If essential information is genuinely missing, retain the ambiguity or add a concise [NEEDS INPUT: ...] placeholder instead of guessing. Return only the finished prompt.',
    shortcut: 'Super+Alt+X',
    enabled: false,
    builtIn: true,
  },
]

export const emptySnapshot = (): AppSnapshot => ({
  version: 1,
  settings: { ...defaultSettings },
  records: [],
  dictionary: [],
  snippets: [],
  styles: defaultStyles.map((style) => ({ ...style, rules: [...style.rules] })),
  transforms: defaultTransforms.map((transform) => ({ ...transform })),
  recoveries: [],
  usage: [],
  scratchpad: '',
})

export class JsonStateStore {
  private snapshot: AppSnapshot | null = null

  public constructor(private readonly filePath: string) {}

  public async load(): Promise<AppSnapshot> {
    if (this.snapshot) return this.snapshot

    await mkdir(path.dirname(this.filePath), { recursive: true })
    try {
      const raw = await readFile(this.filePath, 'utf8')
      const parsed = JSON.parse(raw) as Partial<AppSnapshot>
      const defaults = emptySnapshot()
      const mergedSettings = { ...defaults.settings, ...(parsed.settings ?? {}) }
      const shortcutBindings = normalizeShortcutBindings(parsed.settings?.shortcutBindings, {
        holdShortcut: parsed.settings?.holdShortcut,
        toggleShortcut: parsed.settings?.toggleShortcut,
      })
      const savedCleanupPrompts: Partial<Record<CleanupLevel, string>> = parsed.settings?.cleanupPrompts ?? {}
      const cleanupPrompts = { ...defaults.settings.cleanupPrompts }
      for (const level of ['none', 'light', 'medium'] as const) {
        const value = savedCleanupPrompts[level]
        if (typeof value === 'string' && value.trim() && value !== LEGACY_CLEANUP_PROMPTS[level]) cleanupPrompts[level] = value
      }
      const savedTransforms = Array.isArray(parsed.transforms) && parsed.transforms.length > 0 ? parsed.transforms : defaults.transforms
      const transforms = savedTransforms.map((transform) => {
        const improvedDefault = defaults.transforms.find((candidate) => candidate.id === transform.id && candidate.builtIn)
        if (!improvedDefault || transform.instructions !== LEGACY_TRANSFORM_INSTRUCTIONS[transform.id]) return transform
        return {
          ...transform,
          name: improvedDefault.name,
          description: improvedDefault.description,
          instructions: improvedDefault.instructions,
        }
      })
      this.snapshot = {
        ...defaults,
        ...parsed,
        version: 1,
        settings: {
          ...mergedSettings,
          // Cleanup level is the user-facing authority. Older builds let a
          // hidden llmProvider="none" silently defeat a selected cleanup card.
          llmProvider: mergedSettings.cleanupLevel === 'none' ? 'none' : 'groq',
          cleanupPrompts,
          holdShortcut: isValidHoldShortcut(mergedSettings.holdShortcut) ? mergedSettings.holdShortcut : defaults.settings.holdShortcut,
          toggleShortcut: isValidShortcut(mergedSettings.toggleShortcut) ? mergedSettings.toggleShortcut : defaults.settings.toggleShortcut,
          shortcutBindings,
        },
        records: Array.isArray(parsed.records) ? parsed.records : [],
        dictionary: Array.isArray(parsed.dictionary) ? parsed.dictionary : [],
        snippets: Array.isArray(parsed.snippets) ? parsed.snippets : [],
        styles: Array.isArray(parsed.styles) && parsed.styles.length > 0 ? parsed.styles : defaults.styles,
        transforms,
        recoveries: Array.isArray(parsed.recoveries) ? parsed.recoveries : [],
        usage: Array.isArray(parsed.usage) ? parsed.usage : [],
        scratchpad: typeof parsed.scratchpad === 'string' ? parsed.scratchpad : '',
      }
    } catch {
      this.snapshot = emptySnapshot()
    }

    return this.snapshot
  }

  public async update(mutator: (snapshot: AppSnapshot) => void): Promise<AppSnapshot> {
    const current = await this.load()
    mutator(current)
    await this.persist(current)
    return current
  }

  public async persist(snapshot: AppSnapshot): Promise<void> {
    await mkdir(path.dirname(this.filePath), { recursive: true })
    const tempPath = `${this.filePath}.${process.pid}.tmp`
    await writeFile(tempPath, JSON.stringify(snapshot, null, 2), 'utf8')
    await rename(tempPath, this.filePath)
    this.snapshot = snapshot
  }
}
