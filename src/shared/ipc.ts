export type PageId =
  | 'dictation'
  | 'insights'
  | 'dictionary'
  | 'snippets'
  | 'style'
  | 'transforms'
  | 'scratchpad'
  | 'settings'

export type ProviderId = 'groq' | 'local'
export type LlmProviderId = 'groq' | 'none'
export type CleanupLevel = 'none' | 'light' | 'medium'
export type DictationMode = 'toggle' | 'hold'
export type { ShortcutActionId, ShortcutBindings } from './shortcuts'
import type { ShortcutActionId, ShortcutBindings } from './shortcuts'
export type RetentionMode = 'forever' | '24h' | 'never'
export type PillPosition = 'left' | 'center' | 'right'
export type CleanupStatus = 'disabled' | 'applied' | 'unchanged' | 'failed'
export type DictationPhase =
  | 'idle'
  | 'starting'
  | 'recording'
  | 'stopping'
  | 'transcribing'
  | 'processing'
  | 'ready'
  | 'inserting'
  | 'success'
  | 'error'
  | 'cancelled'

export interface PublicSettings {
  transcriptionProvider: ProviderId
  transcriptionModel: string
  llmProvider: LlmProviderId
  llmModel: string
  language: string
  cleanupLevel: CleanupLevel
  cleanupPrompts: Record<CleanupLevel, string>
  defaultStyle: string
  holdShortcut: string
  toggleShortcut: string
  shortcutBindings: ShortcutBindings
  microphoneLabel: string
  localCommand: string
  localWorkingDirectory: string
  launchAtLogin: boolean
  showPill: boolean
  pillPosition: PillPosition
  showInDock: boolean
  playSounds: boolean
  muteMusicWhileDictating: boolean
  theme: 'light' | 'dark' | 'system'
  retention: RetentionMode
}

export interface DictationRecord {
  id: string
  createdAt: string
  rawText: string
  cleanedText: string
  finalText: string
  durationMs: number
  wordCount: number
  application: string
  category: string
  transcriptionProvider: ProviderId
  transcriptionModel: string
  llmProvider: LlmProviderId
  llmModel: string
  cleanupLevel: CleanupLevel
  style: string
  dictionaryFixCount: number
  aiFixCount: number
  insertionOutcome: 'inserted' | 'copied' | 'scratchpad' | 'not-attempted' | 'failed'
  retention: RetentionMode
  /** True when the source recording was retained and can be played back. */
  audioAvailable?: boolean
  /** Main-process-only filename for the retained recording. Never a user path. */
  audioFileName?: string
  audioMimeType?: string
  /** Whether the optional text-LLM cleanup actually ran for this transcript. */
  cleanupStatus?: CleanupStatus
  /** Safe, user-facing reason when cleanup was attempted but could not be applied. */
  cleanupError?: string
}

export interface RecoveryRecording {
  id: string
  createdAt: string
  durationMs: number
  mimeType: string
  status: 'pending' | 'failed'
  retryCount: number
  error?: string
  /** Main-process-only filename. Never expose this field through bootstrap. */
  audioFileName?: string
}

export interface DictionaryEntry {
  id: string
  spoken: string
  replacement: string
  scope: 'all' | 'technical' | 'personal'
  protected: boolean
  createdAt: string
}

export interface Snippet {
  id: string
  trigger: string
  expansion: string
  enabled: boolean
  createdAt: string
}

export interface StyleProfile {
  id: string
  name: string
  description: string
  example: string
  rules: string[]
  category: 'personal' | 'work' | 'email' | 'other'
}

export interface TransformProfile {
  id: string
  name: string
  description: string
  instructions: string
  shortcut: string
  enabled: boolean
  builtIn: boolean
}

export interface UsageDay {
  date: string
  words: number
  dictations: number
  durationMs: number
}

export interface OverlayState {
  phase: DictationPhase
  sessionId: string | null
  mode: DictationMode
  level: number
  elapsedMs: number
  message: string
  transcript: string
  result: string
  error: string | null
  provider: ProviderId
  cleanupLevel: CleanupLevel
  copyAvailable: boolean
}

export interface BootstrapPayload {
  settings: PublicSettings
  records: DictationRecord[]
  dictionary: DictionaryEntry[]
  snippets: Snippet[]
  styles: StyleProfile[]
  transforms: TransformProfile[]
  recoveries: Array<Omit<RecoveryRecording, 'audioFileName'>>
  usage: UsageDay[]
  scratchpad: string
  hasGroqKey: boolean
  holdShortcutRegistered: boolean
  registeredHoldShortcut: string
  shortcutRegistered: boolean
  registeredShortcut: string
  shortcutRegistrations: Record<ShortcutActionId, {
    registered: string[]
    unavailable: string[]
  }>
  transformShortcutRegistrations: Record<string, {
    registered: boolean
    error?: string
  }>
  capabilities: {
    microphone: boolean
    cloudTranscription: boolean
    localTranscription: boolean
    externalInsertion: boolean
    appOwnedInsertion: boolean
  }
  overlay: OverlayState
}

export interface HealthPayload {
  appName: string
  packaged: boolean
  rendererLoaded: boolean
  preloadBridge: boolean
  contextIsolation: boolean
  nodeIntegration: boolean
  sandbox: boolean
}

export interface CommandResult {
  ok: boolean
  message?: string
  error?: string
}

export interface CommandModePayload {
  sourceText: string
  message?: string
}

export interface CommandModeResult extends CommandResult {
  text?: string
}

export interface HistoryAudioResult extends CommandResult {
  dataUrl?: string
  mimeType?: string
}

export type AppEventChannel =
  | 'dictation:state'
  | 'recording:start'
  | 'recording:stop'
  | 'recording:cancel'
  | 'shortcut:record'
  | 'command:open'
  | 'command:view-changes'
  | 'action:cancel'
  | 'overlay:state'
  | 'overlay:level'
  | 'toast'

export interface FlowerWhispApi {
  app: {
    health(): Promise<HealthPayload>
    quit(): Promise<void>
    minimize(): Promise<void>
    toggleMaximize(): Promise<void>
    close(): Promise<void>
  }
  bootstrap(): Promise<BootstrapPayload>
  dictation: {
    start(options?: { mode?: DictationMode }): Promise<CommandResult>
    stop(): Promise<CommandResult>
    cancel(): Promise<CommandResult>
    copy(text: string): Promise<CommandResult>
    sendToScratchpad(text: string): Promise<CommandResult>
  }
  audio: {
    submit(payload: {
      sessionId: string
      dataUrl: string
      mimeType: string
      durationMs: number
    }): Promise<CommandResult>
    reportLevel(sessionId: string, level: number): void
    reportError(sessionId: string, message: string): void
  }
  pill: {
    setHovered(hovered: boolean): void
  }
  settings: {
    save(patch: Partial<PublicSettings>): Promise<CommandResult>
    setShortcutRecording(recording: boolean): Promise<CommandResult>
    setGroqKey(value: string): Promise<CommandResult>
    clearGroqKey(): Promise<CommandResult>
  }
  history: {
    delete(id: string): Promise<CommandResult>
    copy(id: string): Promise<CommandResult>
    audio(id: string): Promise<HistoryAudioResult>
    play(id: string): Promise<CommandResult>
    undo(id: string): Promise<CommandResult>
    retry(id: string): Promise<CommandResult>
    extract(id: string): Promise<CommandResult>
  }
  recovery: {
    retry(id: string): Promise<CommandResult>
    discard(id: string): Promise<CommandResult>
  }
  dictionary: {
    save(entry: Omit<DictionaryEntry, 'id' | 'createdAt'> & { id?: string }): Promise<CommandResult>
    delete(id: string): Promise<CommandResult>
  }
  snippets: {
    save(snippet: Omit<Snippet, 'id' | 'createdAt'> & { id?: string }): Promise<CommandResult>
    delete(id: string): Promise<CommandResult>
  }
  transforms: {
    save(transform: Omit<TransformProfile, 'builtIn'> & { builtIn?: boolean }): Promise<CommandResult>
    delete(id: string): Promise<CommandResult>
  }
  scratchpad: {
    read(): Promise<string>
    save(value: string): Promise<CommandResult>
  }
  command: {
    run(sourceText: string, instructions: string): Promise<CommandModeResult>
    apply(text: string): Promise<CommandResult>
    askPerplexity(sourceText: string, question: string): Promise<CommandResult>
  }
  on(channel: AppEventChannel, listener: (payload: unknown) => void): () => void
}

declare global {
  interface Window {
    flowerWhisp: FlowerWhispApi
  }
}
