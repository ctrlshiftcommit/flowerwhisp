import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { CSSProperties, FormEvent, KeyboardEvent as ReactKeyboardEvent, ReactNode } from 'react'
import {
  ArrowsClockwise,
  Bell,
  BookOpen,
  CaretRight,
  ChartLineUp,
  Check,
  ClipboardText,
  Copy,
  Desktop,
  DotsThree,
  EnvelopeSimple as EnvelopeIcon,
  FloppyDisk,
  Gear,
  Info,
  List,
  MagnifyingGlass,
  Microphone,
  Moon,
  NotePencil,
  Pause,
  Plus,
  Play,
  Quotes,
  ShieldCheck,
  SidebarSimple,
  Sparkle,
  Sun,
  TextAa,
  Trash,
  X,
} from '@phosphor-icons/react'
import type { Icon as PhosphorIcon } from '@phosphor-icons/react'

import type { InsightSummary } from '../shared/domain'
import type {
  BootstrapPayload,
  CleanupLevel,
  CommandResult,
  DictationMode,
  DictationRecord,
  DictationPhase,
  DictionaryEntry,
  FlowerWhispApi,
  OverlayState,
  PageId,
  PublicSettings,
  ShortcutActionId,
  Snippet,
  StyleProfile,
  TransformProfile,
} from '../shared/ipc'
import { DEFAULT_CLEANUP_PROMPTS } from '../shared/promptDefaults'
import type { ShortcutKeyEvent } from '../shared/shortcuts'
import {
  DEFAULT_HOLD_SHORTCUT,
  DEFAULT_SHORTCUT_BINDINGS,
  DEFAULT_TOGGLE_SHORTCUT,
  HOLD_SHORTCUT_REQUIREMENT,
  isShortcutModifier,
  isValidHoldShortcut,
  isValidShortcut,
  isValidShortcutForAction,
  SHORTCUT_REQUIREMENT,
  shortcutFromEvent,
} from '../shared/shortcuts'

type NavIcon = PhosphorIcon

const emptySettings: PublicSettings = {
  transcriptionProvider: 'groq',
  transcriptionModel: 'whisper-large-v3-turbo',
  llmProvider: 'groq',
  llmModel: 'openai/gpt-oss-20b',
  language: 'en',
  cleanupLevel: 'light',
  cleanupPrompts: { ...DEFAULT_CLEANUP_PROMPTS },
  defaultStyle: 'personal-casual',
  styleByCategory: {
    personal: 'personal-casual',
    work: 'work-clear',
    email: 'email-formal',
    other: 'other-formal',
  },
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

const emptyOverlay: OverlayState = {
  phase: 'idle',
  sessionId: null,
  mode: 'toggle',
  level: 0,
  elapsedMs: 0,
  message: '',
  transcript: '',
  result: '',
  error: null,
  provider: 'groq',
  cleanupLevel: 'light',
  copyAvailable: false,
}

const emptyInsights: InsightSummary = {
  totalDictations: 0,
  totalWords: 0,
  estimatedTokens: 0,
  totalDurationMs: 0,
  totalDurationMinutes: 0,
  averageWpm: 0,
  averageWordsPerDictation: 0,
  averageSessionDurationMs: 0,
  longestSessionMs: 0,
  activeDays: 0,
  totalFixes: 0,
  dictionaryFixes: 0,
  aiFixes: 0,
  successfulDictations: 0,
  errorDictations: 0,
  cancelledDictations: 0,
  insertedDictations: 0,
  clipboardFallbacks: 0,
  scratchpadSaves: 0,
  failedInsertions: 0,
  unattemptedInsertions: 0,
  cleanupApplied: 0,
  cleanupUnchanged: 0,
  cleanupFailed: 0,
  cleanupDisabled: 0,
  applicationUsage: [],
  categoryUsage: [
    { category: 'personal', dictationCount: 0, wordCount: 0, durationMs: 0, percentage: 0 },
    { category: 'work', dictationCount: 0, wordCount: 0, durationMs: 0, percentage: 0 },
    { category: 'email', dictationCount: 0, wordCount: 0, durationMs: 0, percentage: 0 },
    { category: 'other', dictationCount: 0, wordCount: 0, durationMs: 0, percentage: 0 },
  ],
  dayPartUsage: [
    { part: 'morning', dictationCount: 0, wordCount: 0, durationMs: 0, percentage: 0 },
    { part: 'afternoon', dictationCount: 0, wordCount: 0, durationMs: 0, percentage: 0 },
    { part: 'evening', dictationCount: 0, wordCount: 0, durationMs: 0, percentage: 0 },
    { part: 'night', dictationCount: 0, wordCount: 0, durationMs: 0, percentage: 0 },
  ],
  activityByDay: [],
  recentDays: [],
  currentPeriod: null,
  previousPeriod: null,
  wordTrendPercent: null,
  bestDay: null,
  currentStreakDays: 0,
  longestStreakDays: 0,
  asOfDate: null,
}

const emptyBootstrap = (): BootstrapPayload => ({
  settings: { ...emptySettings },
  records: [],
  dictionary: [],
  snippets: [],
  styles: [],
  transforms: [],
  recoveries: [],
  usage: [],
  insights: emptyInsights,
  scratchpad: '',
  hasGroqKey: false,
  holdShortcutRegistered: false,
  registeredHoldShortcut: '',
  shortcutRegistered: false,
  registeredShortcut: '',
  shortcutRegistrations: Object.fromEntries(Object.keys(DEFAULT_SHORTCUT_BINDINGS).map((action) => [action, { registered: [], unavailable: [] }])) as unknown as BootstrapPayload['shortcutRegistrations'],
  transformShortcutRegistrations: {},
  capabilities: {
    microphone: true,
    cloudTranscription: true,
    localTranscription: false,
    externalInsertion: true,
    appOwnedInsertion: true,
  },
  overlay: emptyOverlay,
})

const offlineResponse = (message = 'The desktop bridge is unavailable in this preview.') => ({ ok: false, error: message })

const offlineApi: FlowerWhispApi = {
  app: {
    health: async () => ({
      appName: 'FlowerWhisp',
      packaged: false,
      rendererLoaded: true,
      preloadBridge: false,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    }),
    quit: async () => undefined,
    minimize: async () => undefined,
    toggleMaximize: async () => undefined,
    close: async () => undefined,
  },
  bootstrap: async () => emptyBootstrap(),
  dictation: {
    start: async () => offlineResponse(),
    stop: async () => offlineResponse(),
    cancel: async () => offlineResponse(),
    copy: async () => offlineResponse(),
    sendToScratchpad: async () => offlineResponse(),
  },
  audio: {
    submit: async () => offlineResponse(),
    reportLevel: () => undefined,
    reportError: () => undefined,
  },
  pill: { setHovered: () => undefined },
  settings: {
    save: async () => offlineResponse(),
    setShortcutRecording: async () => offlineResponse(),
    setGroqKey: async () => offlineResponse(),
    clearGroqKey: async () => offlineResponse(),
  },
  history: {
    delete: async () => offlineResponse(),
    copy: async () => offlineResponse(),
    audio: async () => offlineResponse(),
    play: async () => offlineResponse(),
    undo: async () => offlineResponse(),
    retry: async () => offlineResponse(),
    extract: async () => offlineResponse(),
  },
  recovery: {
    retry: async () => offlineResponse(),
    discard: async () => offlineResponse(),
  },
  dictionary: {
    save: async () => offlineResponse(),
    delete: async () => offlineResponse(),
  },
  snippets: {
    save: async () => offlineResponse(),
    delete: async () => offlineResponse(),
  },
  transforms: {
    save: async () => offlineResponse(),
    delete: async () => offlineResponse(),
  },
  scratchpad: {
    read: async () => '',
    save: async () => offlineResponse(),
  },
  command: {
    run: async () => offlineResponse(),
    apply: async () => offlineResponse(),
    askPerplexity: async () => offlineResponse(),
  },
  on: () => () => undefined,
}

const api: FlowerWhispApi = typeof window !== 'undefined' && window.flowerWhisp ? window.flowerWhisp : offlineApi

const navSections: Array<{ label: string; items: Array<{ id: PageId; label: string; Icon: NavIcon }> }> = [
  {
    label: 'Library',
    items: [
      { id: 'dictation', label: 'Dictation', Icon: Microphone },
    ],
  },
  {
    label: 'Tools',
    items: [
      { id: 'dictionary', label: 'Dictionary', Icon: BookOpen },
      { id: 'snippets', label: 'Snippets', Icon: Quotes },
      { id: 'style', label: 'Style', Icon: TextAa },
      { id: 'transforms', label: 'Transforms', Icon: ArrowsClockwise },
      { id: 'scratchpad', label: 'Scratchpad', Icon: NotePencil },
    ],
  },
  {
    label: 'Observe',
    items: [{ id: 'insights', label: 'Insights', Icon: ChartLineUp }],
  },
]

const pageTitles: Record<PageId, { title: string; subtitle: string }> = {
  dictation: { title: 'Welcome back', subtitle: '' },
  insights: { title: 'Insights', subtitle: '' },
  dictionary: { title: 'Dictionary', subtitle: '' },
  snippets: { title: 'Snippets', subtitle: '' },
  style: { title: 'Style', subtitle: '' },
  transforms: { title: 'Transforms', subtitle: '' },
  scratchpad: { title: 'Scratchpad', subtitle: '' },
  settings: { title: 'Settings', subtitle: '' },
}

type SettingsSectionId = 'system' | 'general' | 'ai' | 'privacy' | 'appearance'

const settingsSections: Array<{ id: SettingsSectionId; label: string; group: string }> = [
  { id: 'system', label: 'System', group: 'Application' },
  { id: 'general', label: 'General', group: 'Capture' },
  { id: 'ai', label: 'Providers', group: 'Capture' },
  { id: 'privacy', label: 'Privacy', group: 'Application' },
  { id: 'appearance', label: 'Appearance', group: 'Application' },
]

const phaseLabel: Record<DictationPhase, string> = {
  idle: 'Ready',
  starting: 'Starting',
  recording: 'Listening',
  stopping: 'Finishing',
  transcribing: 'Transcribing',
  processing: 'Polishing',
  ready: 'Ready to copy',
  inserting: 'Inserting',
  success: 'Complete',
  error: 'Needs attention',
  cancelled: 'Cancelled',
}

const formatDuration = (milliseconds: number): string => {
  const seconds = Math.max(0, Math.round(milliseconds / 1000))
  const minutes = Math.floor(seconds / 60)
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`
}

const shortcutPartLabel = (part: string): string => {
  if (part === 'Control') return 'Ctrl'
  if (part === 'Super') return 'Win'
  if (part === 'Space') return 'Space'
  if (part === 'Escape') return 'Esc'
  if (part === 'F23') return 'Copilot'
  if (part === 'MouseMiddle') return 'Middle Click'
  if (part === 'Mouse4') return 'Mouse 4'
  if (part === 'Mouse5') return 'Mouse 5'
  if (part === 'DoubleTapMouseMiddle') return 'Double tap Middle Click'
  if (part === 'DoubleTapMouse4') return 'Double tap Mouse 4'
  if (part === 'DoubleTapMouse5') return 'Double tap Mouse 5'
  return part
}

const formatShortcut = (value: string): string => value.split('+').filter(Boolean).map(shortcutPartLabel).join(' + ')

const shortcutActionCopy: Record<ShortcutActionId, { label: string; description: string }> = {
  pushToTalk: { label: 'Push to talk', description: 'Hold a keyboard combination or mouse button while speaking. Release it to transcribe and paste.' },
  handsFree: { label: 'Hands-free mode', description: 'Press once to start dictation and press the same shortcut again to finish and paste.' },
  pressEnter: { label: 'Press Enter', description: 'Send Enter to the app that currently owns the text cursor.' },
  commandMode: { label: 'Command Mode', description: 'Capture selected text and open a Flow or Perplexity command.' },
  pasteLastTranscript: { label: 'Paste last transcript', description: 'Paste the most recent transcript at the current text cursor.' },
  copyLastTranscript: { label: 'Copy last transcript', description: 'Copy the most recent transcript to the clipboard.' },
  openScratchpad: { label: 'Open Scratchpad', description: 'Show FlowerWhisp and open the private Scratchpad.' },
  transformViewChanges: { label: 'Transform view changes', description: 'Open the most recent before-and-after Transform view.' },
  cancel: { label: 'Cancel', description: 'Cancel active dictation and dismiss FlowerWhisp overlays or notices.' },
}

const formatDate = (value: string): string =>
  new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(new Date(value))

const recordSummary = (record: DictationRecord): string => {
  if (!record.finalText) return 'Transcript retained as a privacy-safe aggregate only.'
  return record.finalText.length > 138 ? `${record.finalText.slice(0, 138).trimEnd()}…` : record.finalText
}

const Button = ({
  children,
  onClick,
  variant = 'secondary',
  icon: Icon,
  disabled = false,
  type = 'button',
  className = '',
}: {
  children: ReactNode
  onClick?: () => void
  variant?: 'primary' | 'secondary' | 'quiet' | 'danger'
  icon?: NavIcon
  disabled?: boolean
  type?: 'button' | 'submit'
  className?: string
}) => (
  <button className={`button button-${variant} ${className}`} onClick={onClick} disabled={disabled} type={type}>
    {Icon ? <Icon size={16} weight="regular" /> : null}
    <span>{children}</span>
  </button>
)

const IconButton = ({
  label,
  onClick,
  icon: Icon,
  active = false,
  disabled = false,
}: {
  label: string
  onClick: () => void
  icon: NavIcon
  active?: boolean
  disabled?: boolean
}) => (
  <button className={`icon-button ${active ? 'is-active' : ''}`} aria-label={label} title={label} onClick={onClick} disabled={disabled}>
    <Icon size={17} weight="regular" />
  </button>
)

const ShortcutRecorder = ({
  label,
  value,
  action,
  disabled = false,
  onChange,
  onListeningChange,
}: {
  label: string
  value: string
  action: ShortcutActionId
  disabled?: boolean
  onChange: (value: string) => Promise<CommandResult>
  onListeningChange?: (listening: boolean) => void
}) => {
  const [listening, setListening] = useState(false)
  const [pending, setPending] = useState('')
  const [error, setError] = useState('')
  const shortcutRecordingActive = useRef(false)
  const pendingShortcut = useRef('')
  const mouseCommitTimer = useRef<number | null>(null)
  const lastMouseDown = useRef<{ key: string; at: number } | null>(null)
  const begin = async () => {
    if (disabled || shortcutRecordingActive.current) return
    const response = await api.settings.setShortcutRecording(true)
    if (!response.ok) {
      setError(response.error ?? 'Could not pause the active shortcut.')
      return
    }
    shortcutRecordingActive.current = true
    pendingShortcut.current = ''
    if (mouseCommitTimer.current !== null) window.clearTimeout(mouseCommitTimer.current)
    mouseCommitTimer.current = null
    lastMouseDown.current = null
    setPending('')
    setError('')
    setListening(true)
    onListeningChange?.(true)
  }
  const finish = useCallback(async (): Promise<CommandResult | null> => {
    let restoreResponse: CommandResult | null = null
    if (shortcutRecordingActive.current) {
      shortcutRecordingActive.current = false
      restoreResponse = await api.settings.setShortcutRecording(false)
    }
    setListening(false)
    pendingShortcut.current = ''
    setPending('')
    onListeningChange?.(false)
    if (restoreResponse && !restoreResponse.ok) setError(restoreResponse.error ?? 'The saved shortcut could not be activated.')
    return restoreResponse
  }, [onListeningChange])
  const commit = useCallback(async (next: string) => {
    const response = await onChange(next)
    if (!response.ok) {
      setError(response.error ?? 'That shortcut could not be saved. Try another combination.')
      return
    }
    await finish()
  }, [finish, onChange])
  const handleRecordedEvent = useCallback((event: ShortcutKeyEvent & { repeat?: boolean }) => {
    if (event.repeat) return
    const eventType = event.type ?? 'keydown'
    if ((eventType === 'mousedown' || eventType === 'mouseup') && ['MouseMiddle', 'Mouse4', 'Mouse5'].includes(event.key)) {
      if (action === 'pushToTalk') {
        pendingShortcut.current = event.key
        setPending(event.key)
        setError('')
        if (eventType === 'mouseup') void commit(event.key)
        return
      }
      if (eventType === 'mouseup') return
      const now = Date.now()
      const previous = lastMouseDown.current
      if (previous?.key === event.key && now - previous.at <= 430) {
        if (mouseCommitTimer.current !== null) window.clearTimeout(mouseCommitTimer.current)
        mouseCommitTimer.current = null
        lastMouseDown.current = null
        const doubleTap = `DoubleTap${event.key}`
        pendingShortcut.current = doubleTap
        setPending(doubleTap)
        void commit(doubleTap)
        return
      }
      lastMouseDown.current = { key: event.key, at: now }
      pendingShortcut.current = event.key
      setPending(event.key)
      mouseCommitTimer.current = window.setTimeout(() => {
        mouseCommitTimer.current = null
        lastMouseDown.current = null
        void commit(event.key)
      }, 440)
      return
    }
    const eventIsControl = event.key === 'Control' || event.code?.startsWith('Control')
    const eventIsAlt = event.key === 'Alt' || event.code?.startsWith('Alt')
    const eventIsShift = event.key === 'Shift' || event.code?.startsWith('Shift')
    const eventIsWindows = Boolean(
      event.metaKey
        || event.key === 'Meta'
        || event.key === 'OS'
        || event.key === 'Windows'
        || event.key === 'Win'
        || event.code === 'MetaLeft'
        || event.code === 'MetaRight'
        || event.getModifierState?.('OS')
        || event.getModifierState?.('Meta'),
    )
    const eventIsModifier = eventIsControl || eventIsAlt || eventIsShift || eventIsWindows
    const hasModifier = Boolean(event.ctrlKey || event.altKey || event.shiftKey || eventIsWindows || eventIsControl || eventIsAlt || eventIsShift)
    if (eventType === 'keydown' && event.key === 'Escape' && !hasModifier && action !== 'cancel') {
      void finish()
      return
    }

    if (eventType === 'keyup') {
      if (action !== 'pushToTalk' && action !== 'commandMode') return
      const next = pendingShortcut.current
      if (!next) return
      if (!isValidShortcutForAction(action, next)) {
        setError(action === 'pushToTalk' ? HOLD_SHORTCUT_REQUIREMENT : 'Use a modifier chord, a function key, or another supported global shortcut.')
        return
      }
      setError('')
      void commit(next)
      return
    }

    // Native recording delivers both edges. Build the complete chord on each
    // key-down; toggle commits when its final key arrives, while hold commits
    // only when the user releases the completed gesture.
    const finalKey = eventIsModifier
      ? ''
      : shortcutFromEvent({
          ...event,
          ctrlKey: false,
          altKey: false,
          shiftKey: false,
          metaKey: false,
          getModifierState: undefined,
        })
    const modifiers = new Set<string>()
    if (event.ctrlKey || eventIsControl) modifiers.add('Control')
    if (event.altKey || eventIsAlt) modifiers.add('Alt')
    if (event.shiftKey || eventIsShift) modifiers.add('Shift')
    if (eventIsWindows) modifiers.add('Super')
    const orderedModifiers = ['Control', 'Alt', 'Shift', 'Super'].filter((part) => modifiers.has(part))
    const next = [...orderedModifiers, ...(finalKey ? [finalKey] : [])].join('+')
    if (!next) return
    const complete = isValidShortcutForAction(action, next)
    pendingShortcut.current = next
    setPending(next)
    if (action === 'pushToTalk' || (action === 'commandMode' && !finalKey)) {
      if (!complete) {
        setError(finalKey ? 'That hold key is not supported. Try a modifier chord, function key, Tab, Space, or another special key.' : HOLD_SHORTCUT_REQUIREMENT)
        return
      }
      setError('')
      return
    }
    if (!finalKey) {
      // Modifier-only states are expected while the user is still holding
      // the chord. Keep the recorder calm until its final key arrives.
      setError('')
      return
    }
    if (!complete) {
      setError(next.split('+').some(isShortcutModifier) ? 'That final key is not supported. Try a letter, number, function key, Tab, Space, or another special key.' : SHORTCUT_REQUIREMENT)
      return
    }
    setError('')
    void commit(next)
  }, [action, commit, finish])
  useEffect(() => {
    if (!listening) return undefined
    const offShortcut = api.on('shortcut:record', (payload) => {
      if (!payload || typeof payload !== 'object') return
      handleRecordedEvent(payload as ShortcutKeyEvent & { repeat?: boolean })
    })
    return () => {
      offShortcut()
      if (shortcutRecordingActive.current) {
        shortcutRecordingActive.current = false
        void api.settings.setShortcutRecording(false)
      }
      if (mouseCommitTimer.current !== null) window.clearTimeout(mouseCommitTimer.current)
      onListeningChange?.(false)
    }
  }, [finish, handleRecordedEvent, listening, onListeningChange])

  const display = pending || value
  const keyCount = display.split('+').filter(Boolean).length
  return (
    <button
      className={`shortcut-recorder ${listening ? 'is-listening' : ''} ${keyCount >= 5 ? 'is-long' : ''}`}
      type="button"
      aria-label={`${label}. ${listening ? 'Press the key combination now.' : 'Click to change shortcut.'}`}
      aria-pressed={listening}
      disabled={disabled}
      onClick={(event) => {
        event.currentTarget.blur()
        void begin()
      }}
    >
      <span className="shortcut-recorder-keys" aria-hidden="true">
        {display.split('+').filter(Boolean).map((part) => <kbd key={part}>{shortcutPartLabel(part)}</kbd>)}
        {!display ? <span className="shortcut-recorder-empty">No shortcut</span> : null}
      </span>
      <span className="shortcut-recorder-hint">{listening ? (action === 'pushToTalk' || action === 'commandMode') && pending && isValidShortcutForAction(action, pending) ? 'Release to save' : 'Press keys or mouse…' : value ? 'Click to change' : 'Click to add'}</span>
      {error ? <span className="shortcut-recorder-error" role="alert">{error}</span> : null}
    </button>
  )
}

const ShortcutActionEditor = ({
  action,
  bindings,
  registered,
  unavailable,
  listeningAction,
  onListeningAction,
  onChange,
}: {
  action: ShortcutActionId
  bindings: string[]
  registered: string[]
  unavailable: string[]
  listeningAction: ShortcutActionId | null
  onListeningAction: (action: ShortcutActionId | null) => void
  onChange: (bindings: string[]) => Promise<CommandResult>
}) => {
  const listening = listeningAction === action
  const add = async (binding: string) => onChange([...new Set([...bindings, binding])])
  const remove = (binding: string) => { void onChange(bindings.filter((candidate) => candidate !== binding)) }
  const status = listening
    ? 'Listening for keys or mouse…'
    : unavailable.length > 0
      ? `${unavailable.length} unavailable`
      : registered.length > 0
        ? `${registered.length} active globally`
        : 'Not assigned'
  return <SettingRow label={shortcutActionCopy[action].label} description={shortcutActionCopy[action].description}>
    <div className="shortcut-action-editor">
      {bindings.length > 0 ? <div className="shortcut-binding-list">{bindings.map((binding) => <div className={`shortcut-binding ${unavailable.includes(binding) ? 'is-unavailable' : ''}`} key={binding}><span>{formatShortcut(binding)}</span><IconButton label={`Remove ${formatShortcut(binding)}`} icon={X} onClick={() => remove(binding)} disabled={listeningAction !== null} /></div>)}</div> : null}
      <ShortcutRecorder
        label={`Add ${shortcutActionCopy[action].label} shortcut`}
        action={action}
        value=""
        disabled={listeningAction !== null && !listening}
        onChange={add}
        onListeningChange={(next) => onListeningAction(next ? action : listeningAction === action ? null : listeningAction)}
      />
      <span className={`shortcut-status ${listening ? 'is-listening' : unavailable.length > 0 ? 'is-unavailable' : registered.length > 0 ? 'is-ready' : ''}`} role="status"><span className="shortcut-status-dot" />{status}</span>
    </div>
  </SettingRow>
}

const clampLevel = (value: number): number => Math.max(0, Math.min(1, value))

const useSignalFrame = (level: number, smoothing = 0.2) => {
  const targetLevel = useRef(clampLevel(level))
  const displayLevel = useRef(targetLevel.current)
  const phase = useRef(0)
  const [frame, setFrame] = useState({ level: displayLevel.current, phase: 0 })

  useEffect(() => {
    targetLevel.current = clampLevel(level)
  }, [level])

  useEffect(() => {
    let raf = 0
    let lastPaint = 0
    const tick = (now: number) => {
      // Thirty updates per second is enough for a readable meter and keeps the
      // overlay inexpensive while the microphone is active.
      if (now - lastPaint >= 33) {
        displayLevel.current += (targetLevel.current - displayLevel.current) * smoothing
        phase.current += 0.11
        setFrame({ level: displayLevel.current, phase: phase.current })
        lastPaint = now
      }
      raf = window.requestAnimationFrame(tick)
    }
    raf = window.requestAnimationFrame(tick)
    return () => window.cancelAnimationFrame(raf)
  }, [smoothing])

  return frame
}

const WaveBars = ({ level = 0, compact = false }: { level?: number; compact?: boolean }) => {
  const frame = useSignalFrame(level)
  const count = compact ? 9 : 13
  const activity = Math.max(0.16, Math.min(1, frame.level * 1.9))
  return <div className={`wave-bars ${compact ? 'wave-bars-compact' : ''}`} aria-label={frame.level > 0.03 ? 'Microphone signal detected' : 'Microphone waiting'}>
    {Array.from({ length: count }, (_, index) => {
      const travellingShape = Math.abs(Math.sin(index * 0.72 + frame.phase * 0.82))
      const shape = 0.3 + Math.pow(travellingShape, 1.35) * 0.7
      const height = compact ? 4 + activity * (17 * shape) : 6 + activity * (31 * shape)
      const normalized = Math.max(0, Math.min(1, height / (compact ? 22 : 36)))
      return <span key={index} style={{ height: `${height.toFixed(2)}px`, transform: `scaleY(${0.82 + normalized * 0.18})`, opacity: `${0.58 + normalized * 0.42}` }} />
    })}
  </div>
}

const PillGraph = ({ level = 0, elapsedMs = 0 }: { level?: number; elapsedMs?: number }) => {
  const frame = useSignalFrame(level, 0.24)
  const activity = Math.max(0.18, Math.min(1, frame.level * 2.15))
  return <div className="pill-visualizer" aria-label={frame.level > 0.03 ? 'Live microphone level' : 'Microphone waiting'}><div className="pill-graph" aria-hidden="true">{Array.from({ length: 10 }, (_, index) => {
    const envelope = 0.3 + Math.pow(Math.abs(Math.sin(index * 0.78 + frame.phase)), 1.35) * 0.7
    const height = 3 + activity * envelope * 25
    const scale = 0.88 + envelope * 0.12
    return <span key={index} style={{ height: `${height.toFixed(2)}px`, transform: `scaleY(${scale.toFixed(3)})`, opacity: `${0.68 + Math.min(0.32, activity * envelope)}` }} />
  })}</div><span className="pill-time">{formatDuration(elapsedMs)}</span></div>
}

const Notice = ({ message, tone, onDismiss }: { message: string; tone: 'success' | 'error' | 'neutral'; onDismiss: () => void }) => (
  <div className={`notice notice-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
    <span>{message}</span>
    <button aria-label="Dismiss notification" onClick={onDismiss}>
      <X size={15} />
    </button>
  </div>
)

const Sidebar = ({ page, setPage, collapsed }: { page: PageId; setPage: (page: PageId) => void; collapsed: boolean }) => (
  <aside className={`sidebar ${collapsed ? 'is-collapsed' : ''}`}>
    <div className="brand-lockup">
      <div className="brand-mark" aria-hidden="true">
        <img className="brand-mark-image" src="./flowerwhisp.png" alt="" />
      </div>
      <div className="brand-name">Flow</div>
    </div>

    <nav className="sidebar-nav" aria-label="Primary navigation">
      {navSections.flatMap((section) => section.items).map(({ id, label, Icon }) => (
        <button className={`nav-item ${page === id ? 'is-selected' : ''}`} key={id} onClick={() => setPage(id)} aria-current={page === id ? 'page' : undefined}>
          <Icon size={19} weight={page === id ? 'bold' : 'regular'} />
          <span>{label}</span>
        </button>
      ))}
    </nav>

    <div className="sidebar-bottom">
      <button className={`nav-item ${page === 'settings' ? 'is-selected' : ''}`} type="button" onClick={() => setPage('settings')}>
        <Gear size={19} weight={page === 'settings' ? 'bold' : 'regular'} />
        <span>Settings</span>
      </button>
    </div>
  </aside>
)

const AppChrome = ({ notificationsOpen, onNotifications, sidebarCollapsed, onToggleSidebar }: { notificationsOpen: boolean; onNotifications: () => void; sidebarCollapsed: boolean; onToggleSidebar: () => void }) => (
  <header className="app-chrome">
    <div className="app-chrome-left"><button type="button" aria-label={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'} aria-expanded={!sidebarCollapsed} onClick={onToggleSidebar}><SidebarSimple size={19} /></button></div>
    <div className="app-chrome-right">
      <button className={`notification-button ${notificationsOpen ? 'is-active' : ''}`} type="button" aria-label="Notifications" aria-expanded={notificationsOpen} onClick={onNotifications}><Bell size={19} /></button>
      {notificationsOpen ? <div className="notification-popover" role="dialog" aria-label="Notifications">
        <div className="notification-popover-heading"><strong>Notifications</strong><button type="button" aria-label="Close notifications" onClick={onNotifications}><X size={15} /></button></div>
        <p className="notification-empty">You’re all caught up.</p>
      </div> : null}
      <button type="button" aria-label="Minimize window" onClick={() => void api.app.minimize()}>−</button><button type="button" aria-label="Maximize window" onClick={() => void api.app.toggleMaximize()}>□</button><button type="button" aria-label="Close window" onClick={() => void api.app.close()}>×</button>
    </div>
  </header>
)

const PageHeader = ({ page }: { page: PageId }) => {
  const content = pageTitles[page]
  return (
    <header className="page-header">
      <div>
        <h1>{content.title}</h1>
        {content.subtitle ? <p>{content.subtitle}</p> : null}
      </div>
    </header>
  )
}

const CaptureBand = ({ overlay, onStart, onOpenStyle, onStop, onCancel, onCopy, onScratchpad }: { overlay: OverlayState; onStart: () => void; onOpenStyle: () => void; onStop: () => void; onCancel: () => void; onCopy: () => void; onScratchpad: () => void }) => {
  const active = ['starting', 'recording', 'stopping', 'transcribing', 'processing', 'inserting'].includes(overlay.phase)
  const ready = overlay.phase === 'ready'
  const isError = overlay.phase === 'error'
  const opensStyle = !active && !ready && !isError
  const handleCardKeyDown = (event: ReactKeyboardEvent<HTMLElement>) => {
    if (!opensStyle || (event.key !== 'Enter' && event.key !== ' ')) return
    event.preventDefault()
    onOpenStyle()
  }
  return (
    <section className={`capture-band ${active ? 'is-recording' : ''} ${ready ? 'is-ready' : ''} ${isError ? 'is-error' : ''} ${opensStyle ? 'is-style-link' : ''}`} onClick={opensStyle ? onOpenStyle : undefined} onKeyDown={handleCardKeyDown} tabIndex={opensStyle ? 0 : undefined}>
      <div className="capture-copy">
        <div className="capture-orbit" aria-hidden="true">
          <span className="orbit-dot" />
          <span className="orbit-line" />
          <span className="orbit-line orbit-line-short" />
        </div>
        <h2>{active ? phaseLabel[overlay.phase] : ready ? 'Your words are ready.' : isError ? 'The capture needs a reset.' : <>Make Flow sound like <em>you</em></>}</h2>
        <p>
          {active
            ? overlay.message
              : ready
              ? 'Flow inserts the result into the active app; copy it manually if the target was unavailable, or keep it in Scratchpad.'
              : isError
                ? overlay.error || overlay.message
                : 'Set up different writing styles for different apps.'}
        </p>
        <div className="capture-actions">
          {!active && !ready ? <Button variant="primary" icon={Microphone} onClick={onOpenStyle}>Start now</Button> : null}
          {active ? <Button variant="secondary" icon={Check} onClick={onStop}>Finish</Button> : null}
          {active ? <Button variant="quiet" icon={X} onClick={onCancel}>Cancel</Button> : null}
          {ready ? <Button variant="primary" icon={Copy} onClick={onCopy}>Copy for paste</Button> : null}
          {ready ? <Button variant="secondary" icon={NotePencil} onClick={onScratchpad}>Send to Scratchpad</Button> : null}
          {ready || isError ? <Button variant="quiet" onClick={onStart}>Try again</Button> : null}
        </div>
      </div>
      <div className="capture-console" aria-live="polite" aria-label="Flow preview">
        {active ? <WaveBars level={overlay.level} /> : null}
      </div>
    </section>
  )
}

const insightDate = (value: string): Date => new Date(`${value}T12:00:00.000Z`)

const shiftInsightDate = (value: string, days: number): string => {
  const date = insightDate(value)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

const formatInsightDate = (
  value: string,
  options: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'short' },
): string => new Intl.DateTimeFormat(undefined, {
  ...options,
  timeZone: 'UTC',
}).format(insightDate(value))

const formatInsightDuration = (milliseconds: number): string => {
  const seconds = Math.max(0, Math.round(milliseconds / 1_000))
  if (seconds === 0) return '0m'
  const hours = Math.floor(seconds / 3_600)
  const minutes = Math.floor((seconds % 3_600) / 60)
  const remainder = seconds % 60
  if (hours > 0) return `${hours}h ${minutes}m`
  if (minutes > 0) return `${minutes}m ${remainder}s`
  return `${remainder}s`
}

const pluralize = (count: number, singular: string, plural = `${singular}s`): string =>
  `${count.toLocaleString()} ${count === 1 ? singular : plural}`

const SummaryRail = ({ data }: { data: BootstrapPayload }) => {
  const { insights } = data
  const today = insights.asOfDate
    ? insights.activityByDay.find((day) => day.date === insights.asOfDate)
    : undefined
  return (
    <div className="summary-rail" aria-label="Usage summary">
      <div className="summary-item summary-item-lead">
        <span className="summary-value">{insights.totalWords.toLocaleString()}</span>
        <span className="summary-label">total words</span>
      </div>
      <div className="summary-item">
        <span className="summary-value">{insights.averageWpm ? Math.round(insights.averageWpm) : '—'}</span>
        <span className="summary-label">wpm</span>
      </div>
      <div className="summary-item">
        <span className="summary-value">{insights.currentStreakDays || '—'}</span>
        <span className="summary-label">day streak</span>
      </div>
      <div className="summary-note">
        <span className="summary-label">Today</span>
        <span>{today ? pluralize(today.dictationCount, 'dictation') : 'No dictations yet'}</span>
      </div>
    </div>
  )
}

type TranscriptAction = 'undo' | 'retry' | 'extract'

const Ledger = ({ records, onCopy, onDelete, onPlay, playingId, onAction }: { records: DictationRecord[]; onCopy: (record: DictationRecord) => void; onDelete: (id: string) => void; onPlay: (record: DictationRecord) => void; playingId: string | null; onAction: (record: DictationRecord, action: TranscriptAction) => void }) => {
  const [openMenu, setOpenMenu] = useState<string | null>(null)
  return (
    <section className="ledger-section">
      <div className="section-heading-row">
        <div>
          <h3>Today</h3>
        </div>
        <MagnifyingGlass size={20} aria-label="Search dictations" />
      </div>
      {records.length === 0 ? (
        <div className="empty-ledger">
          <div className="empty-glyph"><Microphone size={22} /></div>
          <div><strong>No dictations yet.</strong><p>Use the configured shortcut or start button to create your first transcript.</p></div>
        </div>
      ) : (
        <div className="ledger-list">
          {records.map((record) => (
            <article className="ledger-row" key={record.id}>
              <div className="ledger-time">{formatDate(record.createdAt)}</div>
              <div className="ledger-body">
                <p>{recordSummary(record)}</p>
                <div className="ledger-meta">
                  <span>{record.transcriptionProvider === 'groq' ? 'Groq' : 'Local'}</span>
                  <span>{record.cleanupStatus === 'applied' ? `${record.cleanupLevel} cleanup applied` : record.cleanupStatus === 'unchanged' ? `${record.cleanupLevel} cleanup checked` : record.cleanupStatus === 'failed' ? 'cleanup failed · safe text used' : 'raw'}</span>
                  <span>{formatDuration(record.durationMs)}</span>
                  <span>{record.insertionOutcome === 'inserted' ? 'inserted in active app' : record.insertionOutcome === 'copied' ? 'copied for paste' : record.insertionOutcome === 'scratchpad' ? 'sent to Scratchpad' : 'not inserted'}</span>
                </div>
              </div>
              <div className="ledger-actions">
                <IconButton label={record.audioAvailable ? (playingId === record.id ? 'Pause recording' : 'Play recording') : 'No recording retained'} icon={playingId === record.id ? Pause : Play} active={playingId === record.id} disabled={!record.audioAvailable} onClick={() => onPlay(record)} />
                <IconButton label="Copy final text" icon={Copy} onClick={() => onCopy(record)} />
                <div className="transcript-menu-wrap">
                  <IconButton label="More transcript options" icon={DotsThree} active={openMenu === record.id} onClick={() => setOpenMenu((current) => current === record.id ? null : record.id)} />
                  {openMenu === record.id ? <div className="transcript-menu" role="menu">
                    <button type="button" role="menuitem" onClick={() => { setOpenMenu(null); onAction(record, 'undo') }}>Undo AI edit</button>
                    <button type="button" role="menuitem" onClick={() => { setOpenMenu(null); onAction(record, 'retry') }}>Retry transcript</button>
                    <button type="button" role="menuitem" onClick={() => { setOpenMenu(null); onAction(record, 'extract') }}>Extract audio</button>
                    <button className="is-danger" type="button" role="menuitem" onClick={() => { setOpenMenu(null); onDelete(record.id) }}>Delete transcript</button>
                  </div> : null}
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

const RecoveryBanner = ({ data, onRetry, onDiscard }: { data: BootstrapPayload; onRetry: (id: string) => void; onDiscard: (id: string) => void }) => {
  const recovery = data.recoveries[0]
  if (!recovery) return null
  return <section className="recovery-banner" role="alert"><div className="recovery-banner-icon"><ShieldCheck size={24} weight="fill" /></div><div className="recovery-banner-copy"><span className="detail-kicker">Recording recovered</span><h2>Your audio is safe. Retry the transcript.</h2><p>{recovery.error || 'The previous transcription did not finish, but FlowerWhisp saved the recording before sending it to the provider.'}</p><span>{formatDuration(recovery.durationMs)} · {formatDate(recovery.createdAt)}{recovery.retryCount > 0 ? ` · ${recovery.retryCount} retry${recovery.retryCount === 1 ? '' : 'ies'}` : ''}</span></div><div className="recovery-banner-actions"><Button variant="primary" icon={ArrowsClockwise} onClick={() => onRetry(recovery.id)} disabled={recovery.status === 'pending'}>{recovery.status === 'pending' ? 'Retrying…' : 'Retry transcript'}</Button><Button variant="quiet" onClick={() => onDiscard(recovery.id)}>Discard audio</Button></div></section>
}

const DictationPage = ({ data, overlay, onStart, onOpenStyle, onStop, onCancel, onCopy, onScratchpad, onDelete, onPlay, playingId, onAction, onRetryRecovery, onDiscardRecovery }: { data: BootstrapPayload; overlay: OverlayState; onStart: () => void; onOpenStyle: () => void; onStop: () => void; onCancel: () => void; onCopy: (text?: string) => void; onScratchpad: () => void; onDelete: (id: string) => void; onPlay: (record: DictationRecord) => void; playingId: string | null; onAction: (record: DictationRecord, action: TranscriptAction) => void; onRetryRecovery: (id: string) => void; onDiscardRecovery: (id: string) => void }) => (
  <div className="page page-dictation">
    <RecoveryBanner data={data} onRetry={onRetryRecovery} onDiscard={onDiscardRecovery} />
    <CaptureBand overlay={overlay} onStart={onStart} onOpenStyle={onOpenStyle} onStop={onStop} onCancel={onCancel} onCopy={onCopy} onScratchpad={onScratchpad} />
    <SummaryRail data={data} />
    <Ledger records={data.records} onCopy={(record) => onCopy(record.finalText)} onDelete={onDelete} onPlay={onPlay} playingId={playingId} onAction={onAction} />
  </div>
)

const insightCategoryLabels = {
  personal: 'Personal messages',
  work: 'Work messages',
  email: 'Email',
  other: 'Other writing',
} as const

const insightDayPartLabels = {
  morning: 'Morning',
  afternoon: 'Afternoon',
  evening: 'Evening',
  night: 'Night',
} as const

const InsightsPage = ({ data }: { data: BootstrapPayload }) => {
  const { insights } = data
  const knownAppCount = new Set(
    insights.applicationUsage.map((application) => application.applicationName),
  ).size
  const maxRecentWords = Math.max(
    1,
    ...insights.recentDays.map((day) => day.wordCount),
  )
  const activityByDate = new Map(
    insights.activityByDay.map((day) => [day.date, day]),
  )
  const calendarDays = useMemo(() => {
    if (!insights.asOfDate) return []
    const weekday = insightDate(insights.asOfDate).getUTCDay()
    const firstDate = shiftInsightDate(insights.asOfDate, -(weekday + 77))
    const maxWords = Math.max(
      1,
      ...insights.activityByDay.map((day) => day.wordCount),
    )
    return Array.from({ length: 84 }, (_, index) => {
      const date = shiftInsightDate(firstDate, index)
      const activity = activityByDate.get(date)
      const words = activity?.wordCount ?? 0
      const level = words === 0 ? 0 : Math.min(4, Math.max(1, Math.ceil((words / maxWords) * 4)))
      return {
        date,
        words,
        dictations: activity?.dictationCount ?? 0,
        level,
        future: date > (insights.asOfDate as string),
      }
    })
  }, [insights.activityByDay, insights.asOfDate])
  const trend = insights.wordTrendPercent
  const trendLabel = trend === null
    ? 'No prior 7-day baseline'
    : `${trend > 0 ? '+' : ''}${Math.round(trend)}% words vs prior 7 days`
  const trendTone = trend === null || trend === 0
    ? 'is-neutral'
    : trend > 0
      ? 'is-positive'
      : 'is-negative'

  if (insights.totalDictations === 0) {
    return (
      <div className="page page-insights analytics-page">
        <div className="analytics-scope"><span className="analytics-scope-dot" /><strong>All-time desktop usage</strong><span>Calculated locally</span></div>
        <section className="analytics-empty">
          <div className="analytics-empty-mark"><ChartLineUp size={27} /></div>
          <span className="detail-kicker">Nothing fabricated</span>
          <h2>Your real patterns will appear here.</h2>
          <p>Complete your first dictation and FlowerWhisp will calculate words, speaking time, pace, streaks, writing contexts, cleanup outcomes, and delivery results.</p>
        </section>
        <p className="analytics-method-note"><ShieldCheck size={16} /> Insights are calculated locally from privacy-safe usage totals and retained session metadata.</p>
      </div>
    )
  }

  const overviewMetrics = [
    {
      label: 'Total words',
      value: insights.totalWords.toLocaleString(),
      detail: `${pluralize(insights.totalDictations, 'completed dictation')}`,
    },
    {
      label: 'Speaking time',
      value: formatInsightDuration(insights.totalDurationMs),
      detail: 'Recorded audio duration',
    },
    {
      label: 'Sessions',
      value: insights.totalDictations.toLocaleString(),
      detail: `${Math.round(insights.averageWordsPerDictation).toLocaleString()} words on average`,
    },
    {
      label: 'Est. text tokens',
      value: insights.estimatedTokens.toLocaleString(),
      detail: 'Word-derived estimate, not provider billing',
      title: 'Estimated as four text tokens for every three dictated words. This is not exact Groq API usage.',
    },
  ]
  const statMetrics = [
    { label: 'Speaking pace', value: insights.averageWpm ? `${Math.round(insights.averageWpm)} wpm` : 'Not enough data' },
    { label: 'Average session', value: formatInsightDuration(insights.averageSessionDurationMs) },
    { label: 'Longest session', value: formatInsightDuration(insights.longestSessionMs) },
    { label: 'Active days', value: insights.activeDays.toLocaleString() },
    { label: 'Flow fixes', value: insights.totalFixes.toLocaleString() },
    { label: 'Known apps', value: knownAppCount.toLocaleString() },
  ]
  const cleanupRows = [
    { label: 'Changed by cleanup', value: insights.cleanupApplied },
    { label: 'Checked, unchanged', value: insights.cleanupUnchanged },
    { label: 'Cleanup unavailable', value: insights.cleanupFailed },
    { label: 'Cleanup disabled', value: insights.cleanupDisabled },
  ]
  const deliveryRows = [
    { label: 'Inserted at cursor', value: insights.insertedDictations },
    { label: 'Copied for paste', value: insights.clipboardFallbacks },
    { label: 'Sent to Scratchpad', value: insights.scratchpadSaves },
    { label: 'Not delivered', value: insights.failedInsertions + insights.unattemptedInsertions },
  ]

  return (
    <div className="page page-insights analytics-page">
      <div className="analytics-scope">
        <span className="analytics-scope-dot" />
        <strong>All-time desktop usage</strong>
        <span>{pluralize(insights.totalDictations, 'completed dictation')} across {pluralize(insights.activeDays, 'active day')}</span>
      </div>

      <section className="analytics-overview" aria-label="Usage totals">
        {overviewMetrics.map((metric) => <article className="analytics-metric" key={metric.label} title={metric.title}><span>{metric.label}</span><strong>{metric.value}</strong><small>{metric.detail}</small></article>)}
      </section>

      <section className="analytics-stat-strip" aria-label="Session averages">
        {statMetrics.map((metric) => <div key={metric.label}><span>{metric.label}</span><strong>{metric.value}</strong></div>)}
      </section>

      <div className="analytics-primary-grid">
        <section className="analytics-panel analytics-pulse-panel">
          <header className="analytics-panel-heading">
            <div><span className="detail-kicker">Recent rhythm</span><h2>14-day pulse</h2></div>
            <span className={`analytics-trend ${trendTone}`}>{trendLabel}</span>
          </header>
          <div className="analytics-bars" role="img" aria-label="Words dictated on each of the last 14 days">
            {insights.recentDays.map((day) => {
              const height = day.wordCount === 0 ? 2 : Math.max(8, Math.round((day.wordCount / maxRecentWords) * 100))
              const label = `${formatInsightDate(day.date, { weekday: 'long', day: 'numeric', month: 'long' })}: ${pluralize(day.wordCount, 'word')}, ${pluralize(day.dictationCount, 'dictation')}`
              return <div className={`analytics-bar-column ${day.wordCount > 0 ? 'has-activity' : ''}`} key={day.date} role="img" aria-label={label} title={label}><div className="analytics-bar-track"><i style={{ '--analytics-bar-height': `${height}%` } as CSSProperties} /></div><span>{formatInsightDate(day.date, { day: 'numeric' })}</span><small>{formatInsightDate(day.date, { weekday: 'narrow' })}</small></div>
            })}
          </div>
          <div className="analytics-periods">
            <div><span>Last 7 days</span><strong>{(insights.currentPeriod?.wordCount ?? 0).toLocaleString()} words</strong><small>{pluralize(insights.currentPeriod?.dictationCount ?? 0, 'session')}</small></div>
            <div><span>Previous 7 days</span><strong>{(insights.previousPeriod?.wordCount ?? 0).toLocaleString()} words</strong><small>{pluralize(insights.previousPeriod?.dictationCount ?? 0, 'session')}</small></div>
          </div>
          <details className="analytics-daily-table">
            <summary>View exact daily totals</summary>
            <div className="accessible-table-wrap"><table><thead><tr><th>Date</th><th>Words</th><th>Sessions</th><th>Speaking</th></tr></thead><tbody>{insights.recentDays.map((day) => <tr key={day.date}><td>{formatInsightDate(day.date, { weekday: 'short', day: 'numeric', month: 'short' })}</td><td>{day.wordCount.toLocaleString()}</td><td>{day.dictationCount.toLocaleString()}</td><td>{formatInsightDuration(day.durationMs)}</td></tr>)}</tbody></table></div>
          </details>
        </section>

        <section className="analytics-panel analytics-streak-panel">
          <header className="analytics-panel-heading">
            <div><span className="detail-kicker">Consistency</span><h2>{pluralize(insights.currentStreakDays, 'day')} streak</h2></div>
            <div className="analytics-longest"><span>Longest</span><strong>{pluralize(insights.longestStreakDays, 'day')}</strong></div>
          </header>
          {calendarDays.length > 0 ? <>
            <div className="analytics-calendar-range"><span>{formatInsightDate(calendarDays[0].date, { day: 'numeric', month: 'short' })}</span><span>through {formatInsightDate(insights.asOfDate as string, { day: 'numeric', month: 'short' })}</span></div>
            <div className="analytics-calendar-wrap">
              <div className="analytics-calendar-weekdays" aria-hidden="true">{['S', 'M', 'T', 'W', 'T', 'F', 'S'].map((day, index) => <span key={`${day}-${index}`}>{day}</span>)}</div>
              <div className="analytics-calendar" role="img" aria-label="Twelve-week dictation activity calendar">{calendarDays.map((day) => {
                const label = `${formatInsightDate(day.date, { weekday: 'long', day: 'numeric', month: 'long' })}: ${pluralize(day.words, 'word')}, ${pluralize(day.dictations, 'dictation')}`
                return <i className={`analytics-calendar-cell level-${day.level} ${day.future ? 'is-future' : ''}`} key={day.date} title={day.future ? `${formatInsightDate(day.date, { day: 'numeric', month: 'long' })}: future date` : label} aria-label={day.future ? undefined : label} />
              })}</div>
            </div>
            <div className="analytics-calendar-legend"><span>Less</span>{[0, 1, 2, 3, 4].map((level) => <i className={`analytics-calendar-cell level-${level}`} key={level} />)}<span>More</span></div>
          </> : null}
          <div className="analytics-best-day"><span>Best day</span>{insights.bestDay ? <><strong>{formatInsightDate(insights.bestDay.date, { weekday: 'long', day: 'numeric', month: 'short' })}</strong><small>{pluralize(insights.bestDay.wordCount, 'word')} · {pluralize(insights.bestDay.dictationCount, 'session')}</small></> : <strong>No activity yet</strong>}</div>
        </section>
      </div>

      <div className="analytics-secondary-grid">
        <section className="analytics-panel analytics-context-panel">
          <header className="analytics-panel-heading"><div><span className="detail-kicker">Desktop usage</span><h2>Writing contexts</h2></div><span>{knownAppCount} known {knownAppCount === 1 ? 'app' : 'apps'}</span></header>
          <div className="analytics-distribution-list">
            {insights.categoryUsage.map((category) => <div className="analytics-distribution-row" key={category.category}><div className="analytics-distribution-copy"><span>{insightCategoryLabels[category.category]}</span><small>{pluralize(category.dictationCount, 'session')} · {pluralize(category.wordCount, 'word')}</small></div><div className="analytics-distribution-track"><i style={{ '--analytics-width': `${category.percentage}%` } as CSSProperties} /></div><strong>{Math.round(category.percentage)}%</strong></div>)}
          </div>
          <div className="analytics-app-list">
            <span className="analytics-subheading">Detected apps</span>
            {insights.applicationUsage.length > 0 ? insights.applicationUsage.slice(0, 4).map((application) => <div key={`${application.applicationName}-${application.applicationCategory ?? ''}`}><span>{application.applicationName}</span><small>{pluralize(application.dictationCount, 'session')} · {pluralize(application.wordCount, 'word')}</small><strong>{Math.round(application.percentage)}%</strong></div>) : <p>App detection has not identified a destination yet. Unverified targets are not counted as apps.</p>}
          </div>
        </section>

        <section className="analytics-panel analytics-outcomes-panel">
          <header className="analytics-panel-heading"><div><span className="detail-kicker">What Flow did</span><h2>Cleanup &amp; delivery</h2></div><span>{pluralize(insights.totalFixes, 'fix')}</span></header>
          <div className="analytics-outcome-columns">
            <div><span className="analytics-subheading">Cleanup</span>{cleanupRows.map((row) => <p key={row.label}><span>{row.label}</span><strong>{row.value.toLocaleString()}</strong></p>)}</div>
            <div><span className="analytics-subheading">Delivery</span>{deliveryRows.map((row) => <p key={row.label}><span>{row.label}</span><strong>{row.value.toLocaleString()}</strong></p>)}</div>
          </div>
          <div className="analytics-fix-ledger"><div><span>Word changes</span><strong>{insights.aiFixes.toLocaleString()}</strong></div><div><span>Dictionary fixes</span><strong>{insights.dictionaryFixes.toLocaleString()}</strong></div></div>
        </section>

        <section className="analytics-panel analytics-rhythm-panel">
          <header className="analytics-panel-heading"><div><span className="detail-kicker">When you speak</span><h2>Daily rhythm</h2></div><span>Local time</span></header>
          <div className="analytics-dayparts">{insights.dayPartUsage.map((part) => <div key={part.part}><div><span>{insightDayPartLabels[part.part]}</span><strong>{pluralize(part.dictationCount, 'session')}</strong></div><div className="analytics-distribution-track"><i style={{ '--analytics-width': `${part.percentage}%` } as CSSProperties} /></div><small>{Math.round(part.percentage)}% of retained words</small></div>)}</div>
          <div className="analytics-rhythm-facts"><div><span>Average words</span><strong>{Math.round(insights.averageWordsPerDictation).toLocaleString()}</strong><small>per completed session</small></div><div><span>Speaking pace</span><strong>{insights.averageWpm ? Math.round(insights.averageWpm).toLocaleString() : '—'}</strong><small>words per minute</small></div></div>
        </section>
      </div>

      <p className="analytics-method-note"><ShieldCheck size={16} /> All-time totals use privacy-safe daily aggregates. App, context, cleanup, and delivery breakdowns use retained session metadata. Estimated tokens are a text approximation, not billed provider usage.</p>
    </div>
  )
}

const FlowPromo = ({ kind, onAction, onDismiss }: { kind: 'dictionary' | 'snippets'; onAction: () => void; onDismiss?: () => void }) => kind === 'dictionary' ? (
  <section className="flow-promo flow-promo-dictionary">
    <button className="flow-promo-close" type="button" aria-label="Close" onClick={onDismiss}>×</button>
    <h2>Flow spells the way <em>you</em> do.</h2>
    <p>Flow learns your unique words and names — automatically or manually. Add personal terms, company jargon, client names, or industry-specific lingo. Share them with your team so everyone stays on the same page.</p>
    <div className="flow-promo-chips"><button type="button" onClick={onAction}>Add new word</button><span>Wispr Flow</span><span>Samir</span><span>Sara</span><span>Karol</span><span>Spyder</span></div>
  </section>
) : (
  <section className="flow-promo flow-promo-snippets">
    <h2>The stuff <em>you</em> shouldn’t have to re-type.</h2>
    <p>Save text you type often — an email, intro, or prompt — then say a word to drop it in instantly.</p>
    <div className="snippet-promo-examples"><span>“my LinkedIn”</span><b>→</b><span>https://www.linkedin.com/in/john-doe/</span><span>“rewrite prompt”</span><b>→</b><span>Rewrite this to be more concise...</span><span>“intro email”</span><b>→</b><span>Hey, would love to find some time to chat later...</span></div>
    <button type="button" onClick={onAction}>Add new snippet</button>
  </section>
)

const ReferenceModal = ({ title, description, onClose, children }: { title: string; description: string; onClose: () => void; children: ReactNode }) => <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}><section className="reference-modal" role="dialog" aria-modal="true" aria-labelledby="reference-modal-title"><div className="reference-modal-heading"><div><span className="detail-kicker">Flow library</span><h2 id="reference-modal-title">{title}</h2>{description ? <p>{description}</p> : null}</div><IconButton label="Close dialog" icon={X} onClick={onClose} /></div>{children}</section></div>

const PromptEditorModal = ({ title, description, value, onClose, onSave }: { title: string; description: string; value: string; onClose: () => void; onSave: (value: string) => Promise<CommandResult> }) => {
  const [draft, setDraft] = useState(value)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  useEffect(() => setDraft(value), [value])
  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!draft.trim()) {
      setError('Add instructions before saving this prompt.')
      return
    }
    setSaving(true)
    const response = await onSave(draft)
    setSaving(false)
    if (!response.ok) setError(response.error ?? 'Could not save this prompt.')
  }
  return <ReferenceModal title={title} description={description} onClose={onClose}><form className="reference-modal-form prompt-editor-form" onSubmit={save}><label htmlFor="prompt-editor-text">System instructions</label><textarea id="prompt-editor-text" value={draft} onChange={(event) => { setDraft(event.target.value); setError('') }} rows={12} spellCheck="false" /><div className="prompt-editor-note"><ShieldCheck size={16} /><span>These instructions are user-editable configuration. Flow still keeps the source-text and no-invention guardrails around them.</span></div>{error ? <p className="form-error" role="alert">{error}</p> : null}<div className="form-actions"><Button variant="quiet" onClick={onClose}>Cancel</Button><Button type="submit" variant="primary" icon={Check} disabled={saving}>{saving ? 'Saving…' : 'Save prompt'}</Button></div></form></ReferenceModal>
}

const DictionaryPage = ({ entries, onRefresh }: { entries: DictionaryEntry[]; onRefresh: () => void }) => {
  const [search, setSearch] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [showPromo, setShowPromo] = useState(true)
  const [spoken, setSpoken] = useState('')
  const [replacement, setReplacement] = useState('')
  const [correctMisspelling, setCorrectMisspelling] = useState(true)
  const [shareWithTeam, setShareWithTeam] = useState(false)
  const [libraryTab, setLibraryTab] = useState<'all' | 'personal' | 'shared'>('all')
  const filtered = entries.filter((entry) => {
    const matchesTab = libraryTab === 'all' || (libraryTab === 'personal' ? entry.scope === 'personal' || entry.scope === 'all' : entry.scope === 'technical')
    return matchesTab && `${entry.spoken} ${entry.replacement}`.toLowerCase().includes(search.toLowerCase())
  })
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const response = await api.dictionary.save({ spoken, replacement, scope: shareWithTeam ? 'technical' : 'personal', protected: correctMisspelling })
    if (response.ok) {
      setSpoken('')
      setReplacement('')
      setCorrectMisspelling(true)
      setShareWithTeam(false)
      setShowForm(false)
      onRefresh()
    }
  }
  return (
    <div className="page page-library reference-library-page">
      <div className="reference-toolbar"><label className="search-field"><MagnifyingGlass size={19} /><span className="sr-only">Search dictionary</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search" /></label><div><Button variant="primary" onClick={() => setShowForm(true)}>Add new</Button></div></div>
      <div className="library-tabs" role="tablist" aria-label="Dictionary scope"><button className={libraryTab === 'all' ? 'is-active' : ''} type="button" role="tab" aria-selected={libraryTab === 'all'} onClick={() => setLibraryTab('all')}>All</button><button className={libraryTab === 'personal' ? 'is-active' : ''} type="button" role="tab" aria-selected={libraryTab === 'personal'} onClick={() => setLibraryTab('personal')}>Personal</button><button className={libraryTab === 'shared' ? 'is-active' : ''} type="button" role="tab" aria-selected={libraryTab === 'shared'} onClick={() => setLibraryTab('shared')}>Shared with team</button></div>
      {showPromo ? <FlowPromo kind="dictionary" onAction={() => setShowForm(true)} onDismiss={() => setShowPromo(false)} /> : null}
      <section className="reference-entry-list">{filtered.map((entry) => <article className="reference-entry" key={entry.id}><div><strong>{entry.spoken}</strong><span>{entry.replacement}</span></div><div className="reference-entry-actions"><span className="reference-scope-label">{entry.scope === 'technical' ? 'Shared' : entry.scope === 'personal' ? 'Personal' : 'All apps'}</span><IconButton label="Delete dictionary phrase" icon={Trash} onClick={async () => { await api.dictionary.delete(entry.id); onRefresh() }} /></div></article>)}{filtered.length === 0 ? <div className="reference-empty-state"><BookOpen size={22} /><strong>{search ? 'No matches.' : 'Your dictionary is empty.'}</strong><span>{search ? 'Try a different search.' : 'Add words and names that Flow should spell exactly.'}</span></div> : null}</section>
      {showForm ? <ReferenceModal title="Add to vocabulary" description="Add a word or phrase Flow should recognize exactly." onClose={() => setShowForm(false)}><form className="reference-modal-form" onSubmit={submit}><label htmlFor="spoken">Add a new word</label><input id="spoken" value={spoken} onChange={(event) => setSpoken(event.target.value)} placeholder="e.g. Supabase" required /><label htmlFor="replacement">Correct it to</label><input id="replacement" value={replacement} onChange={(event) => setReplacement(event.target.value)} placeholder="e.g. Supabase" required /><label className="checkbox-row"><input type="checkbox" checked={correctMisspelling} onChange={(event) => setCorrectMisspelling(event.target.checked)} /><span>Correct a misspelling</span></label><label className="checkbox-row"><input type="checkbox" checked={shareWithTeam} onChange={(event) => setShareWithTeam(event.target.checked)} /><span>Share with team</span></label><div className="form-actions"><Button variant="quiet" onClick={() => setShowForm(false)}>Cancel</Button><Button type="submit" variant="primary" icon={Check}>Add word</Button></div></form></ReferenceModal> : null}
    </div>
  )
}

const SnippetsPage = ({ snippets, onRefresh }: { snippets: Snippet[]; onRefresh: () => void }) => {
  const [selectedId, setSelectedId] = useState(snippets[0]?.id ?? '')
  const [showEditor, setShowEditor] = useState(false)
  const [search, setSearch] = useState('')
  const selected = snippets.find((snippet) => snippet.id === selectedId)
  const [trigger, setTrigger] = useState(selected?.trigger ?? ';email')
  const [expansion, setExpansion] = useState(selected?.expansion ?? 'Thanks for reaching out.\nI will get back to you shortly.')
  useEffect(() => { if (selected) { setTrigger(selected.trigger); setExpansion(selected.expansion) } }, [selectedId])
  const filtered = snippets.filter((snippet) => `${snippet.trigger} ${snippet.expansion}`.toLowerCase().includes(search.toLowerCase()))
  const openNew = () => { setSelectedId(''); setTrigger(''); setExpansion(''); setShowEditor(true) }
  const save = async (event: FormEvent) => { event.preventDefault(); const response = await api.snippets.save({ id: selectedId || undefined, trigger, expansion, enabled: true }); if (response.ok) { setShowEditor(false); onRefresh() } }
  return <div className="page page-library reference-library-page"><div className="reference-toolbar"><label className="search-field"><MagnifyingGlass size={19} /><span className="sr-only">Search snippets</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search" /></label><div><Button variant="primary" onClick={openNew}>Add new</Button></div></div><FlowPromo kind="snippets" onAction={openNew} /><section className="reference-entry-list">{filtered.map((snippet) => <article className="reference-entry" key={snippet.id}><div><strong>{snippet.trigger}</strong><span>{snippet.expansion}</span></div><div className="reference-entry-actions"><button type="button" onClick={() => { setSelectedId(snippet.id); setShowEditor(true) }}>Edit</button><IconButton label="Delete snippet" icon={Trash} onClick={async () => { await api.snippets.delete(snippet.id); onRefresh() }} /></div></article>)}{filtered.length === 0 ? <div className="reference-empty-state"><Quotes size={22} /><strong>{search ? 'No matches.' : 'No snippets yet.'}</strong><span>Save an email, intro, or prompt you type often.</span></div> : null}</section>{showEditor ? <ReferenceModal title={selectedId ? 'Edit snippet' : 'Add snippet'} description="Say a trigger word and Flow will expand it into the saved text." onClose={() => setShowEditor(false)}><form className="reference-modal-form snippet-modal-form" onSubmit={save}><label htmlFor="snippet-trigger">Snippet</label><input id="snippet-trigger" value={trigger} onChange={(event) => setTrigger(event.target.value)} placeholder="e.g. intro email" required /><label htmlFor="snippet-expansion">Expansion</label><div className="rich-text-editor"><textarea id="snippet-expansion" value={expansion} onChange={(event) => setExpansion(event.target.value)} rows={8} placeholder="Write the text Flow should insert." required /><span className="character-count">{expansion.length} characters</span></div><div className="form-actions"><Button variant="quiet" onClick={() => setShowEditor(false)}>Cancel</Button><Button type="submit" variant="primary" icon={Check}>{selectedId ? 'Save snippet' : 'Add snippet'}</Button></div></form></ReferenceModal> : null}</div>
}

type StyleTabId = 'personal' | 'work' | 'email' | 'other' | 'cleanup'

const StylePage = ({ styles, settings, onRefresh }: { styles: StyleProfile[]; settings: PublicSettings; onRefresh: () => void }) => {
  const [activeTab, setActiveTab] = useState<StyleTabId>('personal')
  const [selectedStyles, setSelectedStyles] = useState(settings.styleByCategory)
  const [editingCleanup, setEditingCleanup] = useState<CleanupLevel | null>(null)
  useEffect(() => setSelectedStyles(settings.styleByCategory), [settings.styleByCategory])
  const tabCopy: Record<StyleTabId, { label: string; title: string; description: string; apps: string[] }> = {
    personal: { label: 'Personal messages', title: 'This style applies in personal messengers', description: 'Style formatting only applies in English. More languages coming soon.', apps: ['WhatsApp', 'iMessage', 'Telegram', 'Signal', '+'] },
    work: { label: 'Work messages', title: 'This style applies in workplace messengers', description: 'Style formatting only applies in English. More languages coming soon.', apps: ['Slack', 'Teams', 'LinkedIn', '+'] },
    email: { label: 'Email', title: 'This style applies in all major email apps', description: 'Style formatting only applies in English. More languages coming soon.', apps: ['Gmail', 'Superhuman', 'Outlook', 'Apple Mail'] },
    other: { label: 'Other', title: 'This style applies everywhere else', description: 'Choose the writing style Flow should use in other apps.', apps: ['Docs', 'Notion', 'Browser', '+'] },
    cleanup: { label: 'Auto cleanup', title: 'Auto Cleanup applies to all your dictations', description: 'Cleanup removes verbal clutter while keeping the original dictation available for recovery.', apps: [] },
  }
  const cleanupCards: Array<{ id: CleanupLevel; name: string; description: string; example: string }> = [
    { id: 'none', name: 'None', description: 'Transcribes exactly what you said, including mistakes', example: '“I uh wanted to send the notes today”' },
    { id: 'light', name: 'Light', description: 'Cleans up filler words and grammar', example: '“I wanted to send the notes today.”' },
    { id: 'medium', name: 'Medium', description: 'Edits for clarity and conciseness', example: '“I’ll send the notes today.”' },
  ]
  const tab = tabCopy[activeTab]
  const availableStyles = activeTab === 'cleanup' ? [] : styles.filter((style) => style.category === activeTab)
  const cards = activeTab === 'cleanup'
    ? cleanupCards
    : availableStyles
  const selectedId = activeTab === 'cleanup' ? '' : selectedStyles[activeTab]
  const chooseStyle = (id: string) => {
    if (activeTab === 'cleanup' || !styles.some((style) => style.id === id && style.category === activeTab)) return
    const styleByCategory = { ...selectedStyles, [activeTab]: id }
    setSelectedStyles(styleByCategory)
    void api.settings.save({ defaultStyle: id, styleByCategory }).then(onRefresh)
  }
  const chooseCleanup = (level: CleanupLevel) => void api.settings.save({ cleanupLevel: level, llmProvider: level === 'none' ? 'none' : 'groq' }).then(onRefresh)
  const saveCleanupPrompt = async (value: string): Promise<CommandResult> => {
    if (!editingCleanup) return { ok: false, error: 'Choose a cleanup level first.' }
    const response = await api.settings.save({ cleanupPrompts: { ...(settings.cleanupPrompts ?? emptySettings.cleanupPrompts), [editingCleanup]: value } })
    if (response.ok) {
      setEditingCleanup(null)
      onRefresh()
    }
    return response
  }

  return <div className="page page-style reference-style-page">
    <div className="style-tabs" role="tablist" aria-label="Style contexts">
      {(Object.keys(tabCopy) as StyleTabId[]).map((id) => <button className={activeTab === id ? 'is-active' : ''} type="button" role="tab" aria-selected={activeTab === id} key={id} onClick={() => setActiveTab(id)}>{tabCopy[id].label}{id === 'cleanup' ? <span className="style-tab-beta">Beta</span> : null}</button>)}
    </div>
    <section className={`style-promo ${activeTab === 'cleanup' ? 'is-cleanup' : ''}`}>
      <h2>{tab.title}</h2>
      <p>{tab.description}</p>
      {tab.apps.length > 0 ? <div className="style-app-icons">{tab.apps.map((appName) => <span key={appName} title={appName}>{appName}</span>)}</div> : <div className="cleanup-recovery-note"><ShieldCheck size={17} /> The original dictation is never lost. Use the transcript menu to recover it.</div>}
    </section>
    {activeTab === 'cleanup' ? <div className="style-grid cleanup-grid">{cleanupCards.map((card) => <article className={`style-card cleanup-card ${settings.cleanupLevel === card.id ? 'is-selected' : ''}`} key={card.id}><button className="style-card-select" type="button" aria-pressed={settings.cleanupLevel === card.id} onClick={() => chooseCleanup(card.id)}><h3>{card.name}</h3><p>{card.description}</p><div className="style-example">{card.example}</div><span className="cleanup-card-label">{settings.cleanupLevel === card.id ? 'Selected' : 'Select cleanup'}</span></button><button className="cleanup-prompt-edit" type="button" onClick={() => setEditingCleanup(card.id)}>Edit prompt</button></article>)}</div> : <div className="style-grid">{cards.map((card) => <button className={`style-card ${card.id === selectedId ? 'is-selected' : ''}`} type="button" key={card.id} aria-pressed={card.id === selectedId} onClick={() => chooseStyle(card.id)}><h3>{card.name}</h3><p>{card.description}</p><div className="style-example">{card.example}</div><span className="style-avatar">J</span></button>)}</div>}
    {cards.length === 0 ? <EmptyState icon={TextAa} title="No styles yet." body="Create a style profile to shape how Flow formats your dictation." action="Refresh" onAction={onRefresh} /> : null}
    {editingCleanup ? <PromptEditorModal title={`Edit ${editingCleanup} cleanup prompt`} description="Change the instruction used by the optional cleanup stage for this level." value={(settings.cleanupPrompts ?? emptySettings.cleanupPrompts)[editingCleanup]} onClose={() => setEditingCleanup(null)} onSave={saveCleanupPrompt} /> : null}
  </div>
}

const HowItWorksModal = ({ onClose, onTryItOut }: { onClose: () => void; onTryItOut: () => void }) => {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}><section className="video-modal" role="dialog" aria-modal="true" aria-labelledby="transform-video-title"><div className="video-modal-header"><div><span className="detail-kicker">Transforms · Beta</span><h2 id="transform-video-title">How to use Transforms</h2><p>Build a reusable instruction and enable it when you want to rewrite dictated text.</p></div><IconButton label="Close transform guide" icon={X} onClick={onClose} /></div><div className="video-stage how-it-works-copy"><ol><li>Create a transform with a clear name and instruction.</li><li>Enable it from the transform card when you want it available.</li><li>Use the configured transform shortcut while text is selected.</li></ol><span>Transforms work anywhere you write when the selected transform is enabled.</span></div><div className="video-modal-footer"><Button variant="quiet" onClick={onClose}>Close</Button><Button variant="primary" onClick={onTryItOut}>Create a transform</Button></div></section></div>
}

const TransformsPage = ({ data, onRefresh }: { data: BootstrapPayload; onRefresh: () => void }) => {
  const transforms = data.transforms
  const [showForm, setShowForm] = useState(false)
  const [showHowItWorks, setShowHowItWorks] = useState(false)
  const [editingTransform, setEditingTransform] = useState<TransformProfile | null>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [instructions, setInstructions] = useState('')
  const [shortcut, setShortcut] = useState('')
  const save = async (event: FormEvent) => { event.preventDefault(); const response = await api.transforms.save({ id: `custom-${Date.now()}`, name, description, instructions, shortcut, enabled: true }); if (response.ok) { setName(''); setDescription(''); setInstructions(''); setShortcut(''); setShowForm(false); onRefresh() } }
  const builtInCards = [
    { id: 'polish', name: 'Polish' },
    { id: 'prompt-engineer', name: 'Prompt Engineer' },
  ]
  const builtInTransforms = builtInCards.map((card) => ({ card, transform: transforms.find((candidate) => candidate.id === card.id) }))
  const customTransforms = transforms.filter((transform) => !transform.builtIn)
  const reset = async () => { await Promise.all(customTransforms.map((transform) => api.transforms.delete(transform.id))); onRefresh() }
  const savePrompt = async (value: string): Promise<CommandResult> => {
    if (!editingTransform) return { ok: false, error: 'Choose a transform first.' }
    const response = await api.transforms.save({ ...editingTransform, instructions: value })
    if (response.ok) {
      setEditingTransform(null)
      onRefresh()
    }
    return response
  }
  const saveShortcut = async (transform: TransformProfile, value: string): Promise<CommandResult> => {
    const response = await api.transforms.save({ ...transform, shortcut: value })
    if (response.ok) onRefresh()
    return response
  }
  const shortcutEditor = (transform: TransformProfile) => {
    const registration = data.transformShortcutRegistrations[transform.id]
    const status = registration?.registered
      ? 'Active globally'
      : registration?.error
        ? registration.error
        : transform.shortcut && !transform.enabled
          ? 'Enable this Transform to activate it'
          : transform.shortcut
            ? 'Not registered'
            : 'Not assigned'
    return <div className="transform-shortcut-editor"><div className="transform-shortcut-heading"><span>Window shortcut</span>{transform.shortcut ? <button type="button" onClick={() => void saveShortcut(transform, '')}>Clear</button> : null}</div><ShortcutRecorder label={transform.shortcut ? `Change ${transform.name} shortcut` : `Set ${transform.name} shortcut`} action="handsFree" value={transform.shortcut} onChange={(value) => saveShortcut(transform, value)} /><span className={`transform-shortcut-status ${registration?.error ? 'is-error' : registration?.registered ? 'is-ready' : ''}`}><span className="shortcut-status-dot" />{status}</span></div>
  }
  return <div className="page page-transforms reference-transforms-page">
     <div className="transform-options"><span>Enable a transform from its card when you want it available.</span><span className="transform-key-help">Edit the prompt from its card before using it.</span></div>
    <section className="transform-promo"><h2>Transform works anywhere you write</h2><p>Apply a Transform to rewrite, clean up, or restructure text after you dictate.</p><div><Button variant="secondary" onClick={() => setShowForm((value) => !value)}>Try it out</Button><button type="button" onClick={() => setShowHowItWorks(true)}>How it works</button></div></section>
    <div className="transform-heading"><h2>My Transforms</h2><button type="button" onClick={() => void reset()}>↶ &nbsp; Reset to defaults</button><Button variant="primary" onClick={() => setShowForm(true)}>Create New</Button></div>
    {showForm ? <form className="transform-form" onSubmit={save}><div className="field-grid"><div><label htmlFor="transform-name">Name</label><input id="transform-name" value={name} onChange={(event) => setName(event.target.value)} placeholder="e.g. Make it concise" required /></div><div><label htmlFor="transform-description">Description</label><input id="transform-description" value={description} onChange={(event) => setDescription(event.target.value)} placeholder="One-line explanation" /></div></div><label htmlFor="transform-instructions">Prompt</label><textarea id="transform-instructions" value={instructions} onChange={(event) => setInstructions(event.target.value)} rows={4} placeholder="Describe how Flow should rewrite the selected text." required /><div className="transform-new-shortcut"><span>Window shortcut (optional)</span>{shortcut ? <strong>{formatShortcut(shortcut)}</strong> : null}<ShortcutRecorder label={shortcut ? 'Change shortcut' : 'Set shortcut'} action="handsFree" value="" onChange={async (value) => { setShortcut(value); return { ok: true } }} />{shortcut ? <button type="button" onClick={() => setShortcut('')}>Clear</button> : null}</div><div className="form-actions"><Button variant="primary" type="submit" icon={Check}>Save</Button><Button variant="quiet" onClick={() => setShowForm(false)}>Cancel</Button></div></form> : null}
    <div className="transform-card-grid">
       {builtInTransforms.map(({ card, transform }) => transform ? <div className="transform-card" key={transform.id}><h3>{card.name}</h3><p>{transform.description}</p>{shortcutEditor(transform)}<div className="transform-card-actions"><Button variant="quiet" onClick={() => setEditingTransform(transform)}>Edit prompt</Button><Toggle label={card.name} checked={transform.enabled} onChange={(value) => { void api.transforms.save({ ...transform, enabled: value }).then(onRefresh) }} /></div></div> : null)}
       {customTransforms.map((transform) => <div className="transform-card" key={transform.id}><h3>{transform.name}</h3><p>{transform.description || 'Custom rewrite instruction'}</p>{shortcutEditor(transform)}<div className="transform-card-actions"><Button variant="quiet" onClick={() => setEditingTransform(transform)}>Edit prompt</Button><Toggle label={transform.name} checked={transform.enabled} onChange={(value) => { void api.transforms.save({ ...transform, enabled: value }).then(onRefresh) }} /><IconButton label={`Delete ${transform.name}`} icon={Trash} onClick={async () => { await api.transforms.delete(transform.id); onRefresh() }} /></div></div>)}
      <button className="transform-card create-transform-card" type="button" onClick={() => setShowForm(true)}><span className="transform-plus">＋</span><h3>Create your own</h3><p>Upload your own prompt</p></button>
    </div>
    {showHowItWorks ? <HowItWorksModal onClose={() => setShowHowItWorks(false)} onTryItOut={() => { setShowHowItWorks(false); setShowForm(true) }} /> : null}
    {editingTransform ? <PromptEditorModal title={`Edit ${editingTransform.name} prompt`} description="Customize the instruction that Flow sends to the transform stage." value={editingTransform.instructions} onClose={() => setEditingTransform(null)} onSave={savePrompt} /> : null}
  </div>
}

const ScratchpadPage = ({ value, onRefresh }: { value: string; onRefresh: () => void }) => {
  const [draft, setDraft] = useState(value)
  const [search, setSearch] = useState('')
  useEffect(() => setDraft(value), [value])
  const save = async () => { await api.scratchpad.save(draft); onRefresh() }
  const hasNote = Boolean(draft.trim())
  const matchesSearch = !search.trim() || draft.toLowerCase().includes(search.trim().toLowerCase())
  return <div className="page page-scratchpad reference-scratchpad-page">
     <div className="scratchpad-reference-toolbar"><div className="scratchpad-beta-label"><strong>Scratchpad</strong><span>Beta</span></div><span className="scratchpad-private-label">Private workspace</span></div>
    <section className="scratchpad-reference-hero"><div><h2>For quick thoughts you want to come back to</h2><p>Scratchpad keeps a private note close by while you work. Dictate into it without inserting anything into another app.</p></div><Button variant="primary" icon={Plus} onClick={() => setDraft('')}>Start new note</Button></section>
    <div className="scratchpad-recents-heading"><div><h3>Recents</h3><span>{hasNote ? '1 note' : 'No saved notes'}</span></div><div className="scratchpad-recents-actions"><label className="search-field"><MagnifyingGlass size={17} /><span className="sr-only">Search notes</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search" /></label><IconButton label="Refresh notes" icon={ArrowsClockwise} onClick={onRefresh} /><IconButton label="New note" icon={Plus} onClick={() => setDraft('')} /></div></div>
     <div className="scratchpad-reference-layout"><aside className="scratchpad-note-list">{hasNote && matchesSearch ? <div className="scratchpad-note-row is-selected" role="status"><strong>Scratchpad note</strong><span>{draft.trim().slice(0, 72)}{draft.trim().length > 72 ? '…' : ''}</span></div> : <div className="scratchpad-no-notes">No notes found</div>}</aside><section className="scratchpad-sheet"><textarea value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="Start writing or dictate something here." aria-label="Scratchpad text" /><div className="scratchpad-footer"><span><ShieldCheck size={14} /> Never inserted into another application</span><span>{draft.trim() ? `${draft.trim().split(/\s+/).length} words` : 'Empty workspace'}</span><Button variant="secondary" icon={FloppyDisk} onClick={save}>Save note</Button></div></section></div>
  </div>
}

const SettingsPage = ({ data, onRefresh, onThemePreview }: { data: BootstrapPayload; onRefresh: () => void; onThemePreview: (theme: PublicSettings['theme']) => void }) => {
  const [draft, setDraft] = useState(data.settings)
  const [key, setKey] = useState('')
  const [activeSection, setActiveSection] = useState<SettingsSectionId>('system')
  const [shortcutListening, setShortcutListening] = useState<ShortcutActionId | null>(null)
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const [draftDirty, setDraftDirty] = useState(false)
  useEffect(() => { if (!draftDirty) setDraft(data.settings) }, [data.settings, draftDirty])
  const update = <K extends keyof PublicSettings>(keyName: K, value: PublicSettings[K]) => { void persist({ [keyName]: value } as Partial<PublicSettings>) }
  const persist = async (patch: Partial<PublicSettings>) => {
    const previous = draft
    setDraft((current) => ({ ...current, ...patch }))
    setSaveState('saving')
    const response = await api.settings.save(patch)
    setSaveState(response.ok ? 'saved' : 'error')
    if (response.ok) { setDraftDirty(false); onRefresh() } else setDraft(previous)
    return response
  }
  const save = async () => { await persist(draft) }
  const selectTheme = (theme: PublicSettings['theme']) => {
    onThemePreview(theme)
    void persist({ theme }).then((response) => { if (!response.ok) onThemePreview(draft.theme) })
  }
  const renderActiveSection = () => {
    switch (activeSection) {
      case 'system':
        return <SettingsSection id="system" title="System" description="">
          <SettingGroup title="App settings">
            <SettingRow label="Launch app at login" description=""><Toggle label="Launch app at login" checked={draft.launchAtLogin} onChange={(value) => void persist({ launchAtLogin: value })} /></SettingRow>
            <SettingRow label="Show Flow Bar at all times" description=""><Toggle label="Show Flow Bar at all times" checked={draft.showPill} onChange={(value) => void persist({ showPill: value })} /></SettingRow>
            <SettingRow label="Flow Bar position" description="Keep the pill out of the part of the screen where you work."><select value={draft.pillPosition} onChange={(event) => update('pillPosition', event.target.value as PublicSettings['pillPosition'])}><option value="left">Left</option><option value="center">Center</option><option value="right">Right</option></select></SettingRow>
            <SettingRow label="Show app in dock" description=""><Toggle label="Show app in dock" checked={draft.showInDock} onChange={(value) => void persist({ showInDock: value })} /></SettingRow>
          </SettingGroup>
        </SettingsSection>
      case 'general':
        const updateBindings = (action: ShortcutActionId, bindings: string[]) => persist({
          shortcutBindings: { ...draft.shortcutBindings, [action]: bindings },
        })
        const editor = (action: ShortcutActionId) => <ShortcutActionEditor
          key={action}
          action={action}
          bindings={draft.shortcutBindings[action]}
          registered={data.shortcutRegistrations[action].registered}
          unavailable={data.shortcutRegistrations[action].unavailable}
          listeningAction={shortcutListening}
          onListeningAction={setShortcutListening}
          onChange={(bindings) => updateBindings(action, bindings)}
        />
        return <SettingsSection id="general" title="General" description="">
          <SettingGroup title="Dictation">
            {editor('pushToTalk')}
            {editor('handsFree')}
          </SettingGroup>
          <SettingGroup title="Actions">
            {editor('pressEnter')}
            {editor('commandMode')}
            {editor('pasteLastTranscript')}
            {editor('copyLastTranscript')}
            {editor('openScratchpad')}
            {editor('cancel')}
          </SettingGroup>
          <SettingGroup title="Transform">
            {editor('transformViewChanges')}
            <div className="shortcut-editor-guidance"><Info size={16} /><span>Each action can have more than one binding. Click an existing binding’s × to remove it. Push to talk accepts modifier-only holds and mouse buttons; Command Mode accepts modifier-only chords; other keyboard actions use a modified key or a function key. Single and double-tap mouse gestures are supported. The combinations shown in another app are not installed unless you record them here.</span></div>
          </SettingGroup>
          <SettingGroup title="Capture">
            <SettingRow label="Microphone" description="Used by the browser capture surface."><span className="setting-value">{data.settings.microphoneLabel || 'System default microphone'}</span></SettingRow>
            <SettingRow label="Dictation language" description="Language sent to the transcription provider."><select value={draft.language} aria-label="Dictation language" onChange={(event) => update('language', event.target.value)}><option value="en">English</option><option value="hi">Hindi</option><option value="es">Spanish</option><option value="fr">French</option><option value="de">German</option></select></SettingRow>
          </SettingGroup>
        </SettingsSection>
      case 'ai':
        return <SettingsSection id="ai" title="Providers" description="">
          <SettingRow label="Transcription provider" description=""><select value={draft.transcriptionProvider} onChange={(event) => update('transcriptionProvider', event.target.value as PublicSettings['transcriptionProvider'])}><option value="groq">Groq cloud</option><option value="local">Local command</option></select></SettingRow>
          <SettingRow label="Transcription model" description=""><select value={draft.transcriptionModel} onChange={(event) => update('transcriptionModel', event.target.value)}><option value="whisper-large-v3-turbo">whisper-large-v3-turbo</option><option value="whisper-large-v3">whisper-large-v3</option></select></SettingRow>
          <SettingRow label="Groq API key" description=""><div className="secret-field"><input type="password" value={key} onChange={(event) => setKey(event.target.value)} placeholder={data.hasGroqKey ? 'Replace saved key' : 'Paste key to save'} /><Button variant="secondary" onClick={async () => { const response = await api.settings.setGroqKey(key); if (response.ok) { setKey(''); onRefresh() } }}>Save</Button>{data.hasGroqKey ? <Button variant="quiet" onClick={async () => { await api.settings.clearGroqKey(); onRefresh() }}>Remove</Button> : null}</div></SettingRow>
          <SettingRow label="Local model command" description=""><input value={draft.localCommand} onChange={(event) => update('localCommand', event.target.value)} /></SettingRow>
          <SettingRow label="Local model folder" description=""><input value={draft.localWorkingDirectory} onChange={(event) => update('localWorkingDirectory', event.target.value)} /></SettingRow>
          <SettingRow label="LLM cleanup provider" description="Light or Medium cleanup sends a separate text request to Groq after transcription."><select value={draft.cleanupLevel === 'none' ? 'none' : 'groq'} onChange={(event) => { const provider = event.target.value as PublicSettings['llmProvider']; void persist({ llmProvider: provider, cleanupLevel: provider === 'none' ? 'none' : draft.cleanupLevel === 'none' ? 'light' : draft.cleanupLevel }) }}><option value="none">Off</option><option value="groq">Groq text cleanup</option></select></SettingRow>
          <SettingRow label="LLM model" description=""><input value={draft.llmModel} onChange={(event) => update('llmModel', event.target.value)} /></SettingRow>
        </SettingsSection>
      case 'privacy':
        return <SettingsSection id="privacy" title="Privacy" description="">
          <div className="privacy-setting"><ShieldCheck size={21} /><div><strong>Privacy</strong><span>Audio and transcripts stay on this device unless a cloud provider is selected.</span></div></div>
          <SettingGroup title="Data retention">
            <SettingRow label="Retention" description="Control how long source recordings and transcript history remain available."><select value={draft.retention} onChange={(event) => update('retention', event.target.value as PublicSettings['retention'])}><option value="forever">Keep forever</option><option value="24h">Delete after 24 hours</option><option value="never">Never store transcript text</option></select></SettingRow>
          </SettingGroup>
        </SettingsSection>
      case 'appearance':
        return <SettingsSection id="appearance" title="Appearance" description=""><SettingRow label="Theme" description=""><div className="theme-choice" role="radiogroup" aria-label="Color theme"><button type="button" className={draft.theme === 'light' ? 'is-selected' : ''} aria-pressed={draft.theme === 'light'} onClick={() => selectTheme('light')}><Sun size={16} /> Light</button><button type="button" className={draft.theme === 'dark' ? 'is-selected' : ''} aria-pressed={draft.theme === 'dark'} onClick={() => selectTheme('dark')}><Moon size={16} /> Dark</button><button type="button" className={draft.theme === 'system' ? 'is-selected' : ''} aria-pressed={draft.theme === 'system'} onClick={() => selectTheme('system')}><Desktop size={16} /> System</button></div></SettingRow></SettingsSection>
    }
  }
  const statusText = saveState === 'saving' ? 'Saving…' : saveState === 'error' ? 'Could not save this setting.' : saveState === 'saved' ? 'Saved locally.' : 'Changes are stored locally.'
  return <div className="page page-settings"><div className="settings-layout"><nav className="settings-index" aria-label="Settings sections">{['Capture', 'Application'].map((group) => <div className="settings-index-group" key={group}><span>{group}</span>{settingsSections.filter((section) => section.group === group).map((section) => <button type="button" className={activeSection === section.id ? 'is-selected' : ''} aria-current={activeSection === section.id ? 'page' : undefined} key={section.id} onClick={() => setActiveSection(section.id)}><span>{section.label}</span><CaretRight size={13} /></button>)}</div>)}</nav><div className="settings-content">{renderActiveSection()}<div className="settings-save"><Button variant="primary" icon={FloppyDisk} onClick={() => void save()} disabled={saveState === 'saving'}>Save settings</Button><span>{statusText}</span></div></div></div></div>
}

const SettingsSection = ({ id, title, description, children }: { id: string; title: string; description: string; children: ReactNode }) => <section className="settings-section" id={id}><div className="settings-section-header"><h2>{title}</h2>{description ? <p>{description}</p> : null}</div>{children}</section>
const SettingGroup = ({ title, children }: { title: string; children: ReactNode }) => <div className="setting-group"><h3>{title}</h3><div className="setting-group-card">{children}</div></div>
const SettingRow = ({ label, description, children }: { label: string; description: string; children: ReactNode }) => <div className="setting-row"><div><strong>{label}</strong>{description ? <span>{description}</span> : null}</div><div className="setting-control">{children}</div></div>
const Toggle = ({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) => <button className={`toggle ${checked ? 'is-on' : ''}`} type="button" aria-label={label} aria-pressed={checked} onClick={() => onChange(!checked)}><span /></button>
const EmptyState = ({ icon: Icon, title, body, action, onAction }: { icon: NavIcon; title: string; body: string; action: string; onAction: () => void }) => <div className="empty-state"><div className="empty-glyph"><Icon size={27} /></div><div><h3>{title}</h3><p>{body}</p><Button variant="secondary" onClick={onAction}>{action}</Button></div></div>

type CommandModeState = { sourceText: string; text?: string; instructions?: string; message?: string }

const CommandModeModal = ({ state, onClose }: { state: CommandModeState; onClose: () => void }) => {
  const [instructions, setInstructions] = useState(state.instructions ?? '')
  const [resultText, setResultText] = useState(state.text ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(state.message ?? '')
  useEffect(() => {
    setInstructions(state.instructions ?? '')
    setResultText(state.text ?? '')
    setError(state.message ?? '')
  }, [state])
  const run = async () => {
    setBusy(true)
    setError('')
    const response = await api.command.run(state.sourceText, instructions)
    setBusy(false)
    if (response.ok && response.text) setResultText(response.text)
    else setError(response.error ?? 'Command Mode failed.')
  }
  const apply = async () => {
    const response = await api.command.apply(resultText)
    if (response.ok) onClose()
    else setError(response.error ?? 'The Transform result could not be inserted.')
  }
  const askPerplexity = async () => {
    const response = await api.command.askPerplexity(state.sourceText, instructions)
    if (!response.ok) setError(response.error ?? 'Perplexity could not be opened.')
  }
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}><section className="command-mode-modal" role="dialog" aria-modal="true" aria-labelledby="command-mode-title"><div className="reference-modal-heading"><div><span className="detail-kicker">Command Mode</span><h2 id="command-mode-title">Ask about selected text</h2><p>Run a private FlowerWhisp Transform, or explicitly send the selection to Perplexity.</p></div><IconButton label="Close Command Mode" icon={X} onClick={onClose} /></div><div className="command-mode-grid"><label><span>Selected text</span><textarea value={state.sourceText} readOnly rows={7} placeholder="Select text in another app, then trigger Command Mode." /></label><label><span>{resultText ? 'Transform result' : 'What should happen?'}</span>{resultText ? <textarea value={resultText} onChange={(event) => setResultText(event.target.value)} rows={7} /> : <textarea value={instructions} onChange={(event) => setInstructions(event.target.value)} rows={7} placeholder="Polish this, explain it, make it concise…" autoFocus />}</label></div>{error ? <p className="command-mode-error" role="alert">{error}</p> : null}<div className="form-actions">{resultText ? <><Button variant="primary" icon={Check} onClick={() => void apply()}>Apply at selection</Button><Button variant="secondary" onClick={() => setResultText('')}>Back</Button></> : <><Button variant="primary" icon={Sparkle} onClick={() => void run()} disabled={busy || !state.sourceText.trim() || !instructions.trim()}>{busy ? 'Running…' : 'Ask Flow'}</Button><Button variant="secondary" onClick={() => void askPerplexity()} disabled={!state.sourceText.trim() || !instructions.trim()}>Ask Perplexity</Button></>}<Button variant="quiet" onClick={onClose}>Close</Button></div></section></div>
}

const OverlayErrorNotice = ({ message, onDismiss }: { message: string; onDismiss: () => void }) => (
  <div className="overlay-root overlay-error-root" role="alert" aria-live="assertive">
    <section className="overlay-error-popover" aria-labelledby="overlay-error-title" title={message}>
      <div className="overlay-error-copy">
        <strong id="overlay-error-title">Transcription error</strong>
        <p>{message}</p>
        <span>You can retry this transcript in the app.</span>
      </div>
      <button className="overlay-error-dismiss" type="button" aria-label="Dismiss transcription error" onClick={onDismiss}>
        <svg className="overlay-error-countdown" viewBox="0 0 32 32" aria-hidden="true">
          <circle className="overlay-error-countdown-track" cx="16" cy="16" r="13" pathLength="1" />
          <circle className="overlay-error-countdown-progress" cx="16" cy="16" r="13" pathLength="1" />
        </svg>
        <X size={12} weight="bold" aria-hidden="true" />
      </button>
    </section>
  </div>
)

const OverlayPill = ({ overlay }: { overlay: OverlayState }) => {
  const busy = ['starting', 'recording', 'stopping', 'transcribing', 'processing', 'inserting'].includes(overlay.phase)
  const recording = overlay.phase === 'recording'
  const cancelable = overlay.phase === 'starting' || recording
  const processing = ['starting', 'stopping', 'transcribing', 'processing', 'inserting'].includes(overlay.phase)
  const ready = overlay.phase === 'ready'
  const error = overlay.phase === 'error'
  const resting = ['idle', 'success', 'cancelled'].includes(overlay.phase)
  const [clockNow, setClockNow] = useState(() => Date.now())
  const timerStart = useRef<{ sessionId: string; startedAt: number } | null>(null)

  useEffect(() => {
    if (!busy || !overlay.sessionId) {
      timerStart.current = null
      return undefined
    }
    if (timerStart.current?.sessionId !== overlay.sessionId) {
      timerStart.current = { sessionId: overlay.sessionId, startedAt: Date.now() - overlay.elapsedMs }
    }
    const timer = window.setInterval(() => setClockNow(Date.now()), 100)
    return () => window.clearInterval(timer)
  }, [busy, overlay.sessionId])

  const liveElapsedMs = busy && timerStart.current?.sessionId === overlay.sessionId
    ? Math.max(overlay.elapsedMs, clockNow - timerStart.current.startedAt)
    : overlay.elapsedMs

  if (error) {
    const errorMessage = overlay.error?.trim() || overlay.message?.trim() || 'The transcription failed.'
    return <OverlayErrorNotice key={errorMessage} message={errorMessage} onDismiss={() => void api.dictation.cancel()} />
  }
  if (resting) return <div className="overlay-root is-resting"><button className="overlay-idle-mark" type="button" aria-label="Start dictation" onPointerEnter={() => api.pill.setHovered(true)} onPointerLeave={() => api.pill.setHovered(false)} onClick={() => void api.dictation.start({ mode: 'toggle' })}><Microphone size={15} weight="fill" /><span>Start dictation</span></button></div>
  const stateLabel = `${phaseLabel[overlay.phase]}${overlay.message ? `: ${overlay.message}` : ''}`
  return <div className={`overlay-root ${busy ? 'is-busy' : ''} ${ready ? 'is-ready' : ''}`}><div className={`overlay-pill ${recording ? 'is-recording' : ''} ${processing ? 'is-processing' : ''} ${ready ? 'is-ready' : ''}`} aria-label={stateLabel} aria-live="polite" data-phase={overlay.phase}><span className="sr-only">{stateLabel}</span><div className="overlay-copy"><div className="overlay-state"><span className={`overlay-dot ${busy ? 'is-live' : ''}`} /><span className="overlay-label">{phaseLabel[overlay.phase]}</span><span className="overlay-mode">{overlay.mode === 'hold' ? 'hold' : 'toggle'}</span><span className="overlay-time">{formatDuration(liveElapsedMs)}</span></div><p>{overlay.message}</p></div>{cancelable ? <IconButton label="Cancel dictation" icon={X} onClick={() => void api.dictation.cancel()} /> : null}{recording ? <PillGraph level={overlay.level} elapsedMs={liveElapsedMs} /> : null}{processing ? <span className="overlay-processing" aria-label="Processing" /> : null}{recording && overlay.mode === 'toggle' ? <IconButton label="Finish dictation" icon={Check} onClick={() => void api.dictation.stop()} /> : null}</div></div>
}

export function App() {
  const isOverlay = new URLSearchParams(window.location.search).get('window') === 'overlay'
  const [page, setPage] = useState<PageId>('dictation')
  const [data, setData] = useState<BootstrapPayload>(emptyBootstrap)
  const [overlay, setOverlay] = useState<OverlayState>(emptyOverlay)
  const [themePreview, setThemePreview] = useState<PublicSettings['theme'] | null>(null)
  const [notice, setNotice] = useState<{ message: string; tone: 'success' | 'error' | 'neutral' } | null>(null)
  const [notificationsOpen, setNotificationsOpen] = useState(false)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [playingId, setPlayingId] = useState<string | null>(null)
  const [commandMode, setCommandMode] = useState<CommandModeState | null>(null)
  const playingAudioRef = useRef<HTMLAudioElement | null>(null)
  const captureRef = useRef<{ sessionId: string; recorder: MediaRecorder; stream: MediaStream; chunks: Blob[]; startedAt: number; audioContext: AudioContext | null; levelTimer: number | null; cancelled: boolean } | null>(null)
  const pendingStopSessionsRef = useRef(new Set<string>())
  const pendingCancelSessionsRef = useRef(new Set<string>())

  useEffect(() => {
    document.title = 'Flow'
    const rendererWindow = isOverlay ? 'overlay' : 'main'
    document.documentElement.dataset.window = rendererWindow
    document.body.dataset.window = rendererWindow
    return () => {
      delete document.documentElement.dataset.window
      delete document.body.dataset.window
    }
  }, [isOverlay])

  const notify = useCallback((message: string, tone: 'success' | 'error' | 'neutral' = 'success') => setNotice({ message, tone }), [])
  useEffect(() => {
    if (!notice) return undefined
    const timeout = window.setTimeout(() => setNotice(null), 4_200)
    return () => window.clearTimeout(timeout)
  }, [notice])

  useEffect(() => () => {
    playingAudioRef.current?.pause()
    playingAudioRef.current = null
  }, [])

  const refresh = useCallback(async () => {
    const next = await api.bootstrap()
    setData(next)
    setOverlay(next.overlay)
  }, [])

  useEffect(() => {
    void refresh()
    const offState = api.on('dictation:state', (payload) => {
      if (payload && typeof payload === 'object') setOverlay(payload as OverlayState)
    })
    const offOverlay = api.on('overlay:state', (payload) => {
      if (isOverlay && payload && typeof payload === 'object') setOverlay(payload as OverlayState)
    })
    const offLevel = api.on('overlay:level', (payload) => {
      if (!payload || typeof payload !== 'object') return
      const candidate = payload as { level?: unknown; elapsedMs?: unknown }
      if (typeof candidate.level === 'number') setOverlay((current) => ({ ...current, level: candidate.level as number, elapsedMs: typeof candidate.elapsedMs === 'number' ? candidate.elapsedMs : current.elapsedMs }))
    })
    const offToast = api.on('toast', (payload) => {
      if (!payload || typeof payload !== 'object') return
      const candidate = payload as { kind?: string; page?: PageId; shortcut?: string; error?: string }
      if (candidate.kind === 'refresh' || candidate.kind === 'scratchpad-updated') void refresh()
      if (candidate.kind === 'navigate' && candidate.page) setPage(candidate.page)
      if (candidate.kind === 'shortcut-unavailable') { void refresh(); notify(candidate.error ?? `Could not register ${candidate.shortcut ?? 'the shortcut'}. Choose another combination.`, 'error') }
      if (candidate.kind === 'shortcut-ready') { void refresh(); notify(`Global dictation shortcut ready: ${(candidate.shortcut ?? '').replaceAll('Control', 'Ctrl').replaceAll('Super', 'Win')}`, 'neutral') }
      if (candidate.kind === 'shortcuts-ready') { void refresh(); notify('Shortcut actions updated.', 'neutral') }
      if (candidate.kind === 'action-error') notify(candidate.error ?? 'The shortcut action could not be completed.', 'error')
      if (candidate.kind === 'action-ready') notify(candidate.error ?? 'Shortcut action completed.', 'neutral')
    })
    const offCommand = api.on('command:open', (payload) => {
      if (!payload || typeof payload !== 'object') return
      const candidate = payload as CommandModeState
      setCommandMode({ sourceText: typeof candidate.sourceText === 'string' ? candidate.sourceText : '', message: typeof candidate.message === 'string' ? candidate.message : undefined })
    })
    const offChanges = api.on('command:view-changes', (payload) => {
      if (!payload || typeof payload !== 'object') return
      const candidate = payload as CommandModeState
      if (typeof candidate.sourceText === 'string' && typeof candidate.text === 'string') setCommandMode(candidate)
    })
    const offCancelAction = api.on('action:cancel', () => {
      setCommandMode(null)
      setNotificationsOpen(false)
      setNotice(null)
    })
    if (!isOverlay && new URLSearchParams(window.location.search).get('smoke') === '1') void api.app.health()
    return () => { offState(); offOverlay(); offLevel(); offToast(); offCommand(); offChanges(); offCancelAction() }
  }, [isOverlay, notify, refresh])

  const cleanupCapture = useCallback(() => {
    const capture = captureRef.current
    if (!capture) return
    capture.stream.getTracks().forEach((track) => track.stop())
    if (capture.levelTimer !== null) window.clearInterval(capture.levelTimer)
    void capture.audioContext?.close()
    captureRef.current = null
  }, [])

  const startCapture = useCallback(async (payload: unknown) => {
    if (!payload || typeof payload !== 'object') return
    const candidate = payload as { sessionId?: unknown }
    if (typeof candidate.sessionId !== 'string') return
    cleanupCapture()
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true } })
      const mimeType = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg'].find((type) => MediaRecorder.isTypeSupported(type))
      if (!mimeType) throw new Error('This runtime does not support an audio recording format.')
      const recorder = new MediaRecorder(stream, { mimeType })
      const capture = { sessionId: candidate.sessionId, recorder, stream, chunks: [] as Blob[], startedAt: Date.now(), audioContext: null as AudioContext | null, levelTimer: null as number | null, cancelled: false }
      captureRef.current = capture
      recorder.ondataavailable = (event) => { if (event.data.size > 0) capture.chunks.push(event.data) }
      recorder.onstop = () => {
        const wasCancelled = capture.cancelled
        const blob = new Blob(capture.chunks, { type: mimeType })
        const durationMs = Date.now() - capture.startedAt
        cleanupCapture()
        if (wasCancelled || blob.size === 0) return
        const reader = new FileReader()
        reader.onloadend = () => { if (typeof reader.result === 'string') void api.audio.submit({ sessionId: capture.sessionId, dataUrl: reader.result, mimeType, durationMs }).then((response) => { if (!response.ok && response.error) notify(response.error, 'error') }) }
        reader.readAsDataURL(blob)
      }
      recorder.onerror = () => { api.audio.reportError(capture.sessionId, 'The microphone recorder stopped unexpectedly.'); cleanupCapture() }
      recorder.start(250)
      if (pendingCancelSessionsRef.current.delete(capture.sessionId)) {
        capture.cancelled = true
        window.setTimeout(() => { if (captureRef.current === capture && recorder.state === 'recording') recorder.stop() }, 0)
      } else if (pendingStopSessionsRef.current.delete(capture.sessionId)) {
        // The global shortcut can be pressed again before getUserMedia has
        // finished. Defer the stop until MediaRecorder has actually entered
        // its recording state so the second press is never lost.
        window.setTimeout(() => { if (captureRef.current === capture && recorder.state === 'recording') recorder.stop() }, 0)
      }
      const audioContext = new AudioContext()
      capture.audioContext = audioContext
      const analyser = audioContext.createAnalyser()
      analyser.fftSize = 512
      analyser.smoothingTimeConstant = 0.28
      audioContext.createMediaStreamSource(stream).connect(analyser)
      const samples = new Uint8Array(analyser.fftSize)
      const frequencies = new Uint8Array(analyser.frequencyBinCount)
      try { await audioContext.resume() } catch { /* Chromium may keep a background context suspended; capture still remains valid. */ }
      const tick = () => {
        if (!captureRef.current || captureRef.current.sessionId !== capture.sessionId) return
        analyser.getByteTimeDomainData(samples)
        analyser.getByteFrequencyData(frequencies)
        let sum = 0
        let frequencySum = 0
        for (const sample of samples) {
          const normalized = (sample - 128) / 128
          sum += normalized * normalized
        }
        for (const sample of frequencies) frequencySum += sample
        const rmsLevel = Math.sqrt(sum / samples.length) * 5.2
        const frequencyLevel = (frequencySum / Math.max(1, frequencies.length)) / 255 * 2.1
        api.audio.reportLevel(capture.sessionId, Math.min(1, Math.max(rmsLevel, frequencyLevel)))
      }
      tick()
      capture.levelTimer = window.setInterval(tick, 80)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Microphone permission was denied.'
      api.audio.reportError(candidate.sessionId, message)
      notify(message, 'error')
    }
  }, [cleanupCapture, notify])

  const stopCapture = useCallback((payload: unknown) => {
    const capture = captureRef.current
    if (!payload || typeof payload !== 'object') return
    const candidate = payload as { sessionId?: unknown }
    if (typeof candidate.sessionId !== 'string') return
    if (!capture) {
      pendingStopSessionsRef.current.add(candidate.sessionId)
      return
    }
    if (candidate.sessionId !== capture.sessionId) return
    if (capture.recorder.state === 'recording') capture.recorder.stop()
  }, [])

  const cancelCapture = useCallback((payload: unknown) => {
    const capture = captureRef.current
    if (!payload || typeof payload !== 'object') return
    const candidate = payload as { sessionId?: unknown }
    if (typeof candidate.sessionId !== 'string') return
    if (!capture) {
      pendingCancelSessionsRef.current.add(candidate.sessionId)
      return
    }
    if (candidate.sessionId !== capture.sessionId) return
    capture.cancelled = true
    if (capture.recorder.state === 'recording') capture.recorder.stop()
  }, [])

  useEffect(() => {
    if (isOverlay) return () => undefined
    const offStart = api.on('recording:start', (payload) => void startCapture(payload))
    const offStop = api.on('recording:stop', stopCapture)
    const offCancel = api.on('recording:cancel', cancelCapture)
    return () => { offStart(); offStop(); offCancel(); cleanupCapture() }
  }, [cancelCapture, cleanupCapture, isOverlay, startCapture, stopCapture])

  const start = async (mode: DictationMode = 'toggle') => { const response = await api.dictation.start({ mode }); if (!response.ok) notify(response.error ?? 'Could not start dictation.', 'error') }
  const stop = async () => { const response = await api.dictation.stop(); if (!response.ok) notify(response.error ?? 'Could not finish dictation.', 'error') }
  const cancel = async () => { await api.dictation.cancel() }
  const copy = async (text = overlay.result) => { const response = await api.dictation.copy(text); if (response.ok) { notify(response.message ?? 'Copied for paste.'); void refresh() } else notify(response.error ?? 'Could not copy transcript.', 'error') }
  const scratchpad = async (text = overlay.result) => { const response = await api.dictation.sendToScratchpad(text); if (response.ok) { notify(response.message ?? 'Added to Scratchpad.'); setPage('scratchpad'); void refresh() } else notify(response.error ?? 'Could not update Scratchpad.', 'error') }
  const deleteRecord = async (id: string) => { const response = await api.history.delete(id); if (response.ok) { notify(response.message ?? 'Deleted.'); void refresh() } else notify(response.error ?? 'Could not delete dictation.', 'error') }
  const playRecord = async (record: DictationRecord) => {
    if (!record.audioAvailable) {
      notify('This transcript was created before audio playback was enabled, so no recording is available.', 'neutral')
      return
    }
    if (playingId === record.id) {
      playingAudioRef.current?.pause()
      playingAudioRef.current = null
      setPlayingId(null)
      return
    }
    playingAudioRef.current?.pause()
    setPlayingId(null)
    const response = await api.history.audio(record.id)
    if (!response.ok || !response.dataUrl) {
      const fallback = await api.history.play(record.id)
      notify(fallback.ok ? fallback.message ?? 'Playing recording in the system audio player.' : response.error ?? fallback.error ?? 'The retained recording could not be opened.', fallback.ok ? 'neutral' : 'error')
      return
    }
    const audio = new Audio(response.dataUrl)
    audio.onended = () => {
      if (playingAudioRef.current === audio) {
        playingAudioRef.current = null
        setPlayingId(null)
      }
    }
    audio.onerror = () => {
      if (playingAudioRef.current === audio) {
        playingAudioRef.current = null
        setPlayingId(null)
      }
      notify('The retained recording could not be played.', 'error')
    }
    playingAudioRef.current = audio
    try {
      await audio.play()
      setPlayingId(record.id)
    } catch {
      playingAudioRef.current = null
      const fallback = await api.history.play(record.id)
      notify(fallback.ok ? fallback.message ?? 'Playing recording in the system audio player.' : fallback.error ?? 'The retained recording could not be played.', fallback.ok ? 'neutral' : 'error')
    }
  }
  const transcriptAction = async (record: DictationRecord, action: TranscriptAction) => {
    if (action === 'undo') {
      const response = await api.history.undo(record.id)
      if (response.ok) { notify(response.message ?? 'AI edits were undone.'); void refresh() }
      else notify(response.error ?? 'Could not undo the AI edit.', 'error')
      return
    }
    if (action === 'retry') {
      const response = await api.history.retry(record.id)
      if (response.ok) { notify(response.message ?? 'Retried transcript.'); void refresh() }
      else notify(response.error ?? 'Could not retry transcript.', 'error')
      return
    }
    const response = await api.history.extract(record.id)
    if (response.ok) notify(response.message ?? 'Audio extracted as FLAC.')
    else if (response.error !== 'Audio extraction canceled.') notify(response.error ?? 'Could not extract audio.', 'error')
  }
  const retryRecovery = async (id: string) => {
    const response = await api.recovery.retry(id)
    if (response.ok) notify(response.message ?? 'Transcript recovered and copied.')
    else notify(response.error ?? 'Could not retry the recovered recording.', 'error')
    void refresh()
  }
  const discardRecovery = async (id: string) => {
    const response = await api.recovery.discard(id)
    if (response.ok) notify(response.message ?? 'Recovered audio discarded.', 'neutral')
    else notify(response.error ?? 'Could not discard the recovered recording.', 'error')
    void refresh()
  }

  if (isOverlay) return <OverlayPill overlay={overlay} />

  const renderPage = () => {
    switch (page) {
      case 'dictation': return <DictationPage data={data} overlay={overlay} onStart={() => void start()} onOpenStyle={() => setPage('style')} onStop={() => void stop()} onCancel={() => void cancel()} onCopy={() => void copy()} onScratchpad={() => void scratchpad()} onDelete={deleteRecord} onPlay={playRecord} playingId={playingId} onAction={transcriptAction} onRetryRecovery={(id) => void retryRecovery(id)} onDiscardRecovery={(id) => void discardRecovery(id)} />
      case 'insights': return <InsightsPage data={data} />
      case 'dictionary': return <DictionaryPage entries={data.dictionary} onRefresh={() => void refresh()} />
      case 'snippets': return <SnippetsPage snippets={data.snippets} onRefresh={() => void refresh()} />
      case 'style': return <StylePage styles={data.styles} settings={data.settings} onRefresh={() => void refresh()} />
      case 'transforms': return <TransformsPage data={data} onRefresh={() => void refresh()} />
      case 'scratchpad': return <ScratchpadPage value={data.scratchpad} onRefresh={() => void refresh()} />
      case 'settings': return <SettingsPage data={data} onRefresh={() => void refresh()} onThemePreview={setThemePreview} />
    }
  }

  return <div className={`app-shell theme-${themePreview ?? data.settings.theme} ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}><a className="skip-link" href="#main-content">Skip to content</a><AppChrome notificationsOpen={notificationsOpen} onNotifications={() => setNotificationsOpen((current) => !current)} sidebarCollapsed={sidebarCollapsed} onToggleSidebar={() => setSidebarCollapsed((current) => !current)} /><Sidebar page={page} setPage={setPage} collapsed={sidebarCollapsed} /><main className="main-canvas" id="main-content" tabIndex={-1}><div className="main-inner"><PageHeader page={page} />{notice ? <Notice message={notice.message} tone={notice.tone} onDismiss={() => setNotice(null)} /> : null}{renderPage()}</div></main>{commandMode ? <CommandModeModal state={commandMode} onClose={() => setCommandMode(null)} /> : null}</div>
}
