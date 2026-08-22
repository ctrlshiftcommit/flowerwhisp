import { mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import path from 'node:path'

import type {
  CleanupLevel,
  DictionaryEntry,
  DictationRecord,
  PublicSettings,
  Snippet,
  StyleProfile,
  TransformProfile,
  UsageDay,
} from '../../shared/ipc'
import { isValidShortcut } from '../../shared/shortcuts'
import { DEFAULT_CLEANUP_PROMPTS } from '../../shared/promptDefaults'

export interface AppSnapshot {
  version: 1
  settings: PublicSettings
  records: DictationRecord[]
  dictionary: DictionaryEntry[]
  snippets: Snippet[]
  styles: StyleProfile[]
  transforms: TransformProfile[]
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
  // Keep the first-run accelerator on the stable Ctrl/Shift path. Windows
  // reserves the Win/Super key for shell shortcuts, and Electron can reject
  // or crash while registering a Super chord before the user has configured
  // their own shortcut.
  toggleShortcut: 'Control+Shift+Space',
  holdShortcut: 'Control+Shift+Space',
  microphoneLabel: 'System default microphone',
  localCommand: '',
  localWorkingDirectory: '',
  launchAtLogin: false,
  showPill: true,
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
    description: 'Improve clarity and concision without changing the claim.',
    instructions: 'Improve clarity and concision. Preserve every fact, qualifier, and detail.',
    shortcut: 'Super+Alt+C',
    enabled: true,
    builtIn: true,
  },
  {
    id: 'prompt-engineer',
    name: 'Prompt engineer',
    description: 'Make a prompt more precise without filling in missing context.',
    instructions: 'Clarify the request, constraints, and desired output using only the supplied text.',
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
      const savedCleanupPrompts: Partial<Record<CleanupLevel, string>> = parsed.settings?.cleanupPrompts ?? {}
      const cleanupPrompts = { ...defaults.settings.cleanupPrompts }
      for (const level of ['none', 'light', 'medium'] as const) {
        const value = savedCleanupPrompts[level]
        if (typeof value === 'string' && value.trim()) cleanupPrompts[level] = value
      }
      this.snapshot = {
        ...defaults,
        ...parsed,
        version: 1,
        settings: {
          ...mergedSettings,
          cleanupPrompts,
          toggleShortcut: isValidShortcut(mergedSettings.toggleShortcut) ? mergedSettings.toggleShortcut : defaults.settings.toggleShortcut,
          holdShortcut: isValidShortcut(mergedSettings.holdShortcut) ? mergedSettings.holdShortcut : defaults.settings.holdShortcut,
        },
        records: Array.isArray(parsed.records) ? parsed.records : [],
        dictionary: Array.isArray(parsed.dictionary) ? parsed.dictionary : [],
        snippets: Array.isArray(parsed.snippets) ? parsed.snippets : [],
        styles: Array.isArray(parsed.styles) && parsed.styles.length > 0 ? parsed.styles : defaults.styles,
        transforms:
          Array.isArray(parsed.transforms) && parsed.transforms.length > 0 ? parsed.transforms : defaults.transforms,
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
