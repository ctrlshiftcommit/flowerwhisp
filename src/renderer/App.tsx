import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent as ReactKeyboardEvent, ReactNode } from 'react'
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
  Keyboard,
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
  Snippet,
  StyleProfile,
  TransformProfile,
} from '../shared/ipc'
import { DEFAULT_CLEANUP_PROMPTS } from '../shared/promptDefaults'
import { isShortcutModifier, isValidShortcut, SHORTCUT_REQUIREMENT } from '../shared/shortcuts'

type NavIcon = PhosphorIcon

const emptySettings: PublicSettings = {
  transcriptionProvider: 'groq',
  transcriptionModel: 'whisper-large-v3-turbo',
  llmProvider: 'none',
  llmModel: 'openai/gpt-oss-20b',
  language: 'en',
  cleanupLevel: 'light',
  cleanupPrompts: { ...DEFAULT_CLEANUP_PROMPTS },
  defaultStyle: 'personal-casual',
  toggleShortcut: 'Control+Super+Space',
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

const emptyBootstrap = (): BootstrapPayload => ({
  settings: { ...emptySettings },
  records: [],
  dictionary: [],
  snippets: [],
  styles: [],
  transforms: [],
  usage: [],
  scratchpad: '',
  hasGroqKey: false,
  shortcutRegistered: false,
  registeredShortcut: '',
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

type SettingsSectionId = 'system' | 'general' | 'ai' | 'dictation' | 'privacy' | 'appearance'

const settingsSections: Array<{ id: SettingsSectionId; label: string; group: string }> = [
  { id: 'system', label: 'System', group: 'Application' },
  { id: 'general', label: 'General', group: 'Capture' },
  { id: 'ai', label: 'Providers', group: 'Capture' },
  { id: 'dictation', label: 'Audio', group: 'Capture' },
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
  return part
}

const formatShortcut = (value: string): string => value.split('+').filter(Boolean).map(shortcutPartLabel).join(' + ')

const shortcutKeyAliases: Record<string, string> = {
  LaunchApp1: 'F23',
  LaunchApp2: 'F24',
  LaunchApplication1: 'F23',
  LaunchApplication2: 'F24',
  Copilot: 'F23',
}

const shortcutFromEvent = (event: KeyboardEvent): string => {
  const isWindowsKey = event.metaKey
    || event.key === 'Meta'
    || event.key === 'OS'
    || event.code === 'MetaLeft'
    || event.code === 'MetaRight'
    || event.getModifierState?.('OS') === true
  const parts = [
    event.ctrlKey ? 'Control' : '',
    event.altKey ? 'Alt' : '',
    event.shiftKey ? 'Shift' : '',
    isWindowsKey ? 'Super' : '',
  ].filter(Boolean)
  const modifierKeys = new Set(['Control', 'Alt', 'Shift', 'Meta', 'OS', 'Super'])
  const key = event.key
  if (!modifierKeys.has(key)) {
    const namedKeys: Record<string, string> = {
      ' ': 'Space',
      Escape: 'Escape',
      Enter: 'Enter',
      Tab: 'Tab',
      Backspace: 'Backspace',
      Delete: 'Delete',
      Insert: 'Insert',
      Home: 'Home',
      End: 'End',
      PageUp: 'PageUp',
      PageDown: 'PageDown',
      ArrowUp: 'Up',
      ArrowDown: 'Down',
      ArrowLeft: 'Left',
      ArrowRight: 'Right',
    }
    const alias = shortcutKeyAliases[key] ?? shortcutKeyAliases[event.code]
    const normalizedFromCode = event.code.startsWith('Key') && event.code.length === 4
      ? event.code.slice(3).toUpperCase()
      : event.code.startsWith('Digit') && event.code.length === 6
        ? event.code.slice(5)
        : ''
    const normalized = namedKeys[key] ?? alias ?? normalizedFromCode ?? (key.length === 1 ? key.toUpperCase() : key.toUpperCase())
    if (normalized && normalized !== 'UNIDENTIFIED') parts.push(normalized)
  }
  return parts.join('+')
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

const ShortcutRecorder = ({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => Promise<CommandResult> }) => {
  const [listening, setListening] = useState(false)
  const [pending, setPending] = useState('')
  const [error, setError] = useState('')
  const shortcutRecordingActive = useRef(false)
  const begin = async () => {
    if (shortcutRecordingActive.current) return
    const response = await api.settings.setShortcutRecording(true)
    if (!response.ok) {
      setError(response.error ?? 'Could not pause the active shortcut.')
      return
    }
    shortcutRecordingActive.current = true
    setPending('')
    setError('')
    setListening(true)
  }
  const finish = useCallback(() => {
    if (shortcutRecordingActive.current) {
      shortcutRecordingActive.current = false
      void api.settings.setShortcutRecording(false)
    }
    setListening(false)
    setPending('')
  }, [])
  const commit = useCallback(async (next: string) => {
    const response = await onChange(next)
    if (!response.ok) {
      setError(response.error ?? 'That shortcut could not be saved. Try another combination.')
      return
    }
    finish()
  }, [finish, onChange])
  useEffect(() => {
    if (!listening) return undefined

    const onWindowKeyDown = (event: KeyboardEvent) => {
      if (event.repeat) return
      event.preventDefault()
      event.stopPropagation()
      if (event.key === 'Escape') {
        finish()
        return
      }
      const next = shortcutFromEvent(event)
      if (!next) return
      const complete: boolean = isValidShortcut(next)
      setPending(next)
      if (!complete) {
        const hasModifier = next.split('+').some(isShortcutModifier)
        setError(hasModifier ? 'Keep holding the modifier and press one letter, number, function, or special key.' : SHORTCUT_REQUIREMENT)
        return
      }
      setError('')
      void commit(next)
    }

    window.addEventListener('keydown', onWindowKeyDown, true)
    return () => {
      window.removeEventListener('keydown', onWindowKeyDown, true)
      if (shortcutRecordingActive.current) {
        shortcutRecordingActive.current = false
        void api.settings.setShortcutRecording(false)
      }
    }
  }, [commit, finish, listening])

  const display = pending || value
  return (
    <button
      className={`shortcut-recorder ${listening ? 'is-listening' : ''}`}
      type="button"
      aria-label={`${label}. ${listening ? 'Press the key combination now.' : 'Click to change shortcut.'}`}
      aria-pressed={listening}
      onClick={(event) => {
        event.currentTarget.blur()
        void begin()
      }}
    >
      <span className="shortcut-recorder-keys" aria-hidden="true">
        {display.split('+').filter(Boolean).map((part) => <kbd key={part}>{shortcutPartLabel(part)}</kbd>)}
        {!display ? <span className="shortcut-recorder-empty">No shortcut</span> : null}
      </span>
      <span className="shortcut-recorder-hint">{listening ? 'Press keys…' : 'Click to change'}</span>
      {error ? <span className="shortcut-recorder-error" role="alert">{error}</span> : null}
    </button>
  )
}

const WaveBars = ({ level = 0, compact = false }: { level?: number; compact?: boolean }) => {
  const targetLevel = useRef(Math.max(0, Math.min(1, level)))
  const currentLevel = useRef(targetLevel.current)
  const [displayLevel, setDisplayLevel] = useState(targetLevel.current)

  useEffect(() => {
    targetLevel.current = Math.max(0, Math.min(1, level))
  }, [level])

  useEffect(() => {
    let frame = 0
    const tick = () => {
      const next = currentLevel.current + (targetLevel.current - currentLevel.current) * 0.2
      currentLevel.current = next
      setDisplayLevel(next)
      frame = window.requestAnimationFrame(tick)
    }
    frame = window.requestAnimationFrame(tick)
    return () => window.cancelAnimationFrame(frame)
  }, [])

  const count = compact ? 9 : 13
  return <div className={`wave-bars ${compact ? 'wave-bars-compact' : ''}`} aria-label={displayLevel > 0.03 ? 'Microphone signal detected' : 'Microphone waiting'}>
    {Array.from({ length: count }, (_, index) => {
      const shape = 0.35 + Math.abs(Math.sin(index * 1.55)) * 0.65
      const height = compact ? 5 + displayLevel * (16 * shape) : 6 + displayLevel * (30 * shape)
      const normalized = Math.max(0, Math.min(1, height / (compact ? 22 : 36)))
      return <span key={index} style={{ height: `${height.toFixed(2)}px`, transform: `scaleY(${0.72 + normalized * 0.28})`, opacity: `${0.52 + normalized * 0.48}` }} />
    })}
  </div>
}

const PillGraph = ({ level = 0, elapsedMs = 0 }: { level?: number; elapsedMs?: number }) => {
  const targetLevel = useRef(Math.max(0, Math.min(1, level)))
  const displayLevel = useRef(targetLevel.current)
  const animationPhase = useRef(0)
  const [frame, setFrame] = useState({ level: displayLevel.current, phase: 0 })

  useEffect(() => {
    targetLevel.current = Math.max(0, Math.min(1, level))
  }, [level])

  useEffect(() => {
    let raf = 0
    const tick = () => {
      displayLevel.current += (targetLevel.current - displayLevel.current) * 0.22
      animationPhase.current += 0.075
      setFrame({ level: displayLevel.current, phase: animationPhase.current })
      raf = window.requestAnimationFrame(tick)
    }
    raf = window.requestAnimationFrame(tick)
    return () => window.cancelAnimationFrame(raf)
  }, [])

  return <div className="pill-visualizer" aria-label={frame.level > 0.03 ? 'Live microphone level' : 'Microphone waiting'}><div className="pill-graph" aria-hidden="true">{Array.from({ length: 12 }, (_, index) => { const envelope = 0.32 + Math.pow(Math.abs(Math.sin(index * 0.72 + frame.phase)), 1.45) * 0.68; const breathingFloor = 0.14 + Math.abs(Math.sin(index * 0.54 + frame.phase * 1.8)) * 0.12; const visibleLevel = Math.min(1, Math.max(breathingFloor, frame.level * 1.55)); const height = 4 + visibleLevel * envelope * 23; return <span key={index} style={{ height: `${height.toFixed(2)}px`, opacity: `${0.72 + Math.min(0.28, visibleLevel * envelope)}`, animationDelay: `${index * -65}ms` }} /> })}</div><span className="pill-time">{formatDuration(elapsedMs)}</span></div>
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

const SummaryRail = ({ data }: { data: BootstrapPayload }) => {
  const totalWords = data.usage.reduce((sum, day) => sum + day.words, 0)
  const totalDuration = data.usage.reduce((sum, day) => sum + day.durationMs, 0)
  const wpm = totalDuration > 0 ? Math.round(totalWords / (totalDuration / 60_000)) : 0
  const streak = data.usage.length
  return (
    <div className="summary-rail" aria-label="Usage summary">
      <div className="summary-item summary-item-lead">
        <span className="summary-value">{totalWords.toLocaleString()}</span>
        <span className="summary-label">total words</span>
      </div>
      <div className="summary-item">
        <span className="summary-value">{wpm || '—'}</span>
        <span className="summary-label">wpm</span>
      </div>
      <div className="summary-item">
        <span className="summary-value">{streak || '—'}</span>
        <span className="summary-label">day streak</span>
      </div>
      <div className="summary-note">
        <span className="summary-label">Today</span>
        <span>{data.usage[0] ? `${data.usage[0].dictations} dictations` : '—'}</span>
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
                  <span>{record.cleanupLevel === 'none' ? 'raw' : `${record.cleanupLevel} cleanup`}</span>
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

const DictationPage = ({ data, overlay, onStart, onOpenStyle, onStop, onCancel, onCopy, onScratchpad, onDelete, onPlay, playingId, onAction }: { data: BootstrapPayload; overlay: OverlayState; onStart: () => void; onOpenStyle: () => void; onStop: () => void; onCancel: () => void; onCopy: (text?: string) => void; onScratchpad: () => void; onDelete: (id: string) => void; onPlay: (record: DictationRecord) => void; playingId: string | null; onAction: (record: DictationRecord, action: TranscriptAction) => void }) => (
  <div className="page page-dictation">
    <CaptureBand overlay={overlay} onStart={onStart} onOpenStyle={onOpenStyle} onStop={onStop} onCancel={onCancel} onCopy={onCopy} onScratchpad={onScratchpad} />
    <SummaryRail data={data} />
    <Ledger records={data.records} onCopy={(record) => onCopy(record.finalText)} onDelete={onDelete} onPlay={onPlay} playingId={playingId} onAction={onAction} />
  </div>
)

const InsightsPage = ({ data }: { data: BootstrapPayload }) => {
  const totalWords = data.usage.reduce((sum, day) => sum + day.words, 0)
  const totalDuration = data.usage.reduce((sum, day) => sum + day.durationMs, 0)
  const wordsPerMinute = totalDuration > 0 ? Math.round(totalWords / (totalDuration / 60_000)) : 0
  const correctedWords = data.records.reduce((sum, record) => sum + record.aiFixCount, 0)
  const dictionaryFixes = data.records.reduce((sum, record) => sum + record.dictionaryFixCount, 0)
  const appsUsed = new Set(data.records.map((record) => record.application).filter(Boolean)).size
  const streak = data.usage.length
  const usageRows = [
    { icon: Sparkle, percent: 0, label: 'AI PROMPTS', count: 0 },
    { icon: ClipboardText, percent: 0, label: 'DOCUMENTS', count: 0 },
    { icon: ArrowsClockwise, percent: 0, label: 'OTHER TASKS', count: 0 },
    { icon: Quotes, percent: 0, label: 'PERSONAL MESSAGES', count: 0 },
    { icon: EnvelopeIcon, percent: 0, label: 'EMAILS', count: 0 },
    { icon: ClipboardText, percent: 0, label: 'WORK MESSAGES', count: 0 },
  ]
  const heatmap = Array.from({ length: 35 }, (_, index) => (index >= 31 && index % 3 === 0 ? 2 : 0))
  return (
    <div className="page page-insights">
      <div className="insights-tab-row"><span className="insights-tab is-active">Your usage</span></div>
      <div className="insights-metrics">
        <section className="metric-card metric-wpm"><strong>{wordsPerMinute || '—'}</strong><span>WORDS PER MINUTE <Info size={17} /></span><div className="gauge"><div className="gauge-arc" /><span>Top<br /><b>0.1%</b></span></div></section>
        <section className="metric-card metric-fixes"><strong>{(correctedWords + dictionaryFixes).toLocaleString()}</strong><span>FIXES MADE BY FLOW</span><hr /><p>{correctedWords.toLocaleString()} words corrected <Info size={17} /></p><p>{dictionaryFixes.toLocaleString()} dictionary fixes <Info size={17} /></p></section>
        <section className="metric-card metric-total"><strong>{totalWords.toLocaleString()}</strong><span>TOTAL WORDS DICTATED</span><hr /><p><Desktop size={18} /> Desktop</p><p>{totalWords.toLocaleString()} words</p></section>
      </div>
      <div className="insights-lower">
        <section className="usage-card"><div className="insights-card-heading"><h2>Desktop usage</h2><span>TOTAL APPS USED | {appsUsed}</span></div>{usageRows.map(({ icon: Icon, percent, label, count }) => <div className="usage-row" key={label}><Icon size={22} /><span className="usage-bar" style={{ width: `${Math.max(12, percent)}%` }}>{percent}%</span><strong>{count} {label}</strong></div>)}</section>
        <section className="streak-card"><div className="insights-card-heading"><h2>{streak || 1} day streak</h2><span>LONGEST STREAK | {streak || 1} DAYS</span></div><div className="streak-months"><span>‹</span><span>Apr</span><span>May</span><span>Jun</span><span>Jul</span><span>Aug</span><span>›</span></div><div className="heatmap">{['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((day, row) => <div className="heatmap-row" key={day}><span>{day}</span>{heatmap.slice(row * 5, row * 5 + 5).map((level, index) => <i className={`heatmap-cell level-${level}`} key={`${day}-${index}`} />)}</div>)}</div><div className="heatmap-legend"><span>More</span><i className="level-3" /><i className="level-2" /><i className="level-1" /><i className="level-0" /><span>Less</span></div></section>
      </div>
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
  const [selectedId, setSelectedId] = useState(settings.defaultStyle || styles[0]?.id || '')
  const [editingCleanup, setEditingCleanup] = useState<CleanupLevel | null>(null)
  const styleCopies: Record<Exclude<StyleTabId, 'cleanup'>, Array<{ name: string; description: string; example: string }>> = {
    personal: [
      { name: 'Formal.', description: 'Caps + Punctuation', example: 'Hey, are you free for lunch tomorrow? Let’s do 12 if that works for you.' },
      { name: 'Casual', description: 'Caps + Less punctuation', example: 'Hey are you free for lunch tomorrow? Let’s do 12 if that works for you' },
      { name: 'very casual', description: 'No Caps + Less punctuation', example: 'hey are you free for lunch tomorrow? let’s do 12 if that works for you' },
    ],
    work: [
      { name: 'Formal', description: 'Caps + Punctuation', example: 'Hi team, I’ll share the revised plan by Thursday afternoon.' },
      { name: 'Casual', description: 'Caps + Less punctuation', example: 'Hey team I’ll share the revised plan by Thursday afternoon' },
      { name: 'Excited', description: 'More exclamations', example: 'Hi team! I’ll share the revised plan by Thursday afternoon!' },
    ],
    email: [
      { name: 'Formal', description: 'Clear and polished', example: 'Thank you for the update. I will review the proposal and reply by Friday.' },
      { name: 'Casual', description: 'Warm and conversational', example: 'Thanks for the update! I’ll take a look and get back to you by Friday.' },
      { name: 'Concise', description: 'Shorter email phrasing', example: 'Thanks — I’ll review this and reply by Friday.' },
    ],
    other: [
      { name: 'Formal', description: 'Caps + Punctuation', example: 'Please review the attached notes and let me know if anything is missing.' },
      { name: 'Casual', description: 'Caps + Less punctuation', example: 'Please review the attached notes and let me know if anything is missing' },
      { name: 'very casual', description: 'No Caps + Less punctuation', example: 'please review the attached notes and let me know if anything is missing' },
    ],
  }
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
    : styleCopies[activeTab].map((copy, index) => ({ ...copy, id: availableStyles[index]?.id ?? styles[index]?.id ?? `${activeTab}-${index}` }))
  const chooseStyle = (id: string) => {
    setSelectedId(id)
    if (styles.some((style) => style.id === id)) void api.settings.save({ defaultStyle: id }).then(onRefresh)
  }
  const chooseCleanup = (level: CleanupLevel) => void api.settings.save({ cleanupLevel: level }).then(onRefresh)
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

const TransformsPage = ({ transforms, onRefresh }: { transforms: TransformProfile[]; onRefresh: () => void }) => {
  const [showForm, setShowForm] = useState(false)
  const [showHowItWorks, setShowHowItWorks] = useState(false)
  const [editingTransform, setEditingTransform] = useState<TransformProfile | null>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [instructions, setInstructions] = useState('')
  const save = async (event: FormEvent) => { event.preventDefault(); const response = await api.transforms.save({ id: `custom-${Date.now()}`, name, description, instructions, shortcut: 'Control+Alt+L', enabled: true }); if (response.ok) { setName(''); setDescription(''); setInstructions(''); setShowForm(false); onRefresh() } }
  const builtInCards = [
    { id: 'polish', name: 'Polish', description: 'Improve clarity and conciseness', fallbackShortcut: 'Super+Alt+C' },
    { id: 'prompt-engineer', name: 'Prompt Engineer', description: '**Title** (1 concise line…', fallbackShortcut: 'Super+Alt+X' },
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
  return <div className="page page-transforms reference-transforms-page">
     <div className="transform-options"><span>Enable a transform from its card when you want it available.</span><span className="transform-key-help">Use the configured transform shortcut to view changes.</span></div>
    <section className="transform-promo"><h2>Transform works anywhere you write</h2><p>Apply a Transform to rewrite, clean up, or restructure text after you dictate.</p><div><Button variant="secondary" onClick={() => setShowForm((value) => !value)}>Try it out</Button><button type="button" onClick={() => setShowHowItWorks(true)}>How it works</button></div></section>
    <div className="transform-heading"><h2>My Transforms</h2><button type="button" onClick={() => void reset()}>↶ &nbsp; Reset to defaults</button><Button variant="primary" onClick={() => setShowForm(true)}>Create New</Button></div>
    {showForm ? <form className="transform-form" onSubmit={save}><div className="field-grid"><div><label htmlFor="transform-name">Name</label><input id="transform-name" value={name} onChange={(event) => setName(event.target.value)} placeholder="e.g. Make it concise" required /></div><div><label htmlFor="transform-description">Description</label><input id="transform-description" value={description} onChange={(event) => setDescription(event.target.value)} placeholder="One-line explanation" /></div></div><label htmlFor="transform-instructions">Prompt</label><textarea id="transform-instructions" value={instructions} onChange={(event) => setInstructions(event.target.value)} rows={4} placeholder="Describe how Flow should rewrite the selected text." required /><div className="form-actions"><Button variant="primary" type="submit" icon={Check}>Save</Button><Button variant="quiet" onClick={() => setShowForm(false)}>Cancel</Button></div></form> : null}
    <div className="transform-card-grid">
      {builtInTransforms.map(({ card, transform }) => transform ? <div className="transform-card" key={transform.id}><div className="transform-shortcut">{formatShortcut(transform.shortcut || card.fallbackShortcut).split(' + ').map((part) => <kbd key={part}>{part}</kbd>)}</div><h3>{card.name}</h3><p>{card.description}</p><div className="transform-card-actions"><Button variant="quiet" onClick={() => setEditingTransform(transform)}>Edit prompt</Button><Toggle label={card.name} checked={transform.enabled} onChange={(value) => { void api.transforms.save({ ...transform, enabled: value }).then(onRefresh) }} /></div></div> : null)}
      {customTransforms.map((transform) => <div className="transform-card" key={transform.id}><div className="transform-shortcut">{formatShortcut(transform.shortcut).split(' + ').map((part) => <kbd key={part}>{part}</kbd>)}</div><h3>{transform.name}</h3><p>{transform.description || 'Custom rewrite instruction'}</p><div className="transform-card-actions"><Button variant="quiet" onClick={() => setEditingTransform(transform)}>Edit prompt</Button><Toggle label={transform.name} checked={transform.enabled} onChange={(value) => { void api.transforms.save({ ...transform, enabled: value }).then(onRefresh) }} /><IconButton label={`Delete ${transform.name}`} icon={Trash} onClick={async () => { await api.transforms.delete(transform.id); onRefresh() }} /></div></div>)}
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

const ShortcutEditorModal = ({ draft, onClose, onChange }: { draft: PublicSettings; onClose: () => void; onChange: (key: 'toggleShortcut', value: string) => Promise<CommandResult> }) => {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}><section className="shortcut-modal" role="dialog" aria-modal="true" aria-labelledby="shortcut-modal-title"><div className="reference-modal-heading"><div><span className="detail-kicker">Settings</span><h2 id="shortcut-modal-title">Shortcuts</h2><p>Change the shortcuts that this build actually supports.</p></div><IconButton label="Close shortcuts" icon={X} onClick={onClose} /></div><div className="shortcut-editor-list"><div className="shortcut-editor-row"><div><strong>Push to talk</strong><span>Hold Middle Click and speak inside the Flow window.</span></div><span className="shortcut-token">Middle Click</span></div><div className="shortcut-editor-row"><div><strong>Hands-free mode</strong><span>Toggle dictation on and off from anywhere.</span></div><ShortcutRecorder label="Hands-free mode shortcut" value={draft.toggleShortcut} onChange={(value) => onChange('toggleShortcut', value)} /></div><div className="shortcut-editor-guidance"><Keyboard size={16} /><span>{SHORTCUT_REQUIREMENT} Windows Copilot keys are captured as F23 when Windows exposes them to the app.</span></div></div><div className="shortcut-modal-footer"><span>Press Esc to close</span><Button variant="primary" onClick={onClose}>Done</Button></div></section></div>
}

const SettingsPage = ({ data, onRefresh, onThemePreview }: { data: BootstrapPayload; onRefresh: () => void; onThemePreview: (theme: PublicSettings['theme']) => void }) => {
  const [draft, setDraft] = useState(data.settings)
  const [key, setKey] = useState('')
  const [activeSection, setActiveSection] = useState<SettingsSectionId>('system')
  const [showShortcutEditor, setShowShortcutEditor] = useState(false)
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
            <SettingRow label="Show app in dock" description=""><Toggle label="Show app in dock" checked={draft.showInDock} onChange={(value) => void persist({ showInDock: value })} /></SettingRow>
          </SettingGroup>
        </SettingsSection>
      case 'general':
        const requestedShortcut = draft.toggleShortcut
        const shortcutFallback = data.shortcutRegistered && data.registeredShortcut !== requestedShortcut
        const shortcutDescription = shortcutFallback
          ? `Requested ${formatShortcut(requestedShortcut)} · active fallback ${formatShortcut(data.registeredShortcut)}`
          : 'Use the global toggle to start or finish a dictation.'
        const shortcutStatus = data.shortcutRegistered
          ? shortcutFallback ? `Active fallback: ${formatShortcut(data.registeredShortcut)}` : 'Registered globally'
          : 'Not registered yet'
        return <SettingsSection id="general" title="General" description="">
          <SettingRow label="Toggle dictation" description={shortcutDescription}><div className="shortcut-setting-control"><ShortcutRecorder label="Toggle dictation shortcut" value={requestedShortcut} onChange={(value) => persist({ toggleShortcut: value })} /><span className={`shortcut-status ${data.shortcutRegistered ? 'is-ready' : 'is-unavailable'}`} role="status"><span className="shortcut-status-dot" />{shortcutStatus}</span></div></SettingRow>
          <SettingRow label="Shortcuts" description="Push to talk, hands-free mode, and transcript actions."><Button variant="secondary" icon={Keyboard} onClick={() => setShowShortcutEditor(true)}>Edit shortcuts</Button></SettingRow>
          <SettingRow label="Hold to dictate" description="Available inside FlowerWhisp only; system-wide key-up support is not available in this Electron build."><span className="setting-value"><kbd>Middle Click</kbd><span>Dictation page</span></span></SettingRow>
          <SettingRow label="Microphone" description="Used by the browser capture surface."><span className="setting-value">{data.settings.microphoneLabel || 'System default microphone'}</span></SettingRow>
          <SettingRow label="Dictation language" description="Language sent to the transcription provider."><select value={draft.language} aria-label="Dictation language" onChange={(event) => update('language', event.target.value)}><option value="en">English</option><option value="hi">Hindi</option><option value="es">Spanish</option><option value="fr">French</option><option value="de">German</option></select></SettingRow>
        </SettingsSection>
      case 'ai':
        return <SettingsSection id="ai" title="Providers" description="">
          <SettingRow label="Transcription provider" description=""><select value={draft.transcriptionProvider} onChange={(event) => update('transcriptionProvider', event.target.value as PublicSettings['transcriptionProvider'])}><option value="groq">Groq cloud</option><option value="local">Local command</option></select></SettingRow>
          <SettingRow label="Transcription model" description=""><select value={draft.transcriptionModel} onChange={(event) => update('transcriptionModel', event.target.value)}><option value="whisper-large-v3-turbo">whisper-large-v3-turbo</option><option value="whisper-large-v3">whisper-large-v3</option></select></SettingRow>
          <SettingRow label="Groq API key" description=""><div className="secret-field"><input type="password" value={key} onChange={(event) => setKey(event.target.value)} placeholder={data.hasGroqKey ? 'Replace saved key' : 'Paste key to save'} /><Button variant="secondary" onClick={async () => { const response = await api.settings.setGroqKey(key); if (response.ok) { setKey(''); onRefresh() } }}>Save</Button>{data.hasGroqKey ? <Button variant="quiet" onClick={async () => { await api.settings.clearGroqKey(); onRefresh() }}>Remove</Button> : null}</div></SettingRow>
          <SettingRow label="Local model command" description=""><input value={draft.localCommand} onChange={(event) => update('localCommand', event.target.value)} /></SettingRow>
          <SettingRow label="Local model folder" description=""><input value={draft.localWorkingDirectory} onChange={(event) => update('localWorkingDirectory', event.target.value)} /></SettingRow>
          <SettingRow label="LLM cleanup provider" description=""><select value={draft.llmProvider} onChange={(event) => update('llmProvider', event.target.value as PublicSettings['llmProvider'])}><option value="none">Off</option><option value="groq">Groq text cleanup</option></select></SettingRow>
          <SettingRow label="LLM model" description=""><input value={draft.llmModel} onChange={(event) => update('llmModel', event.target.value)} /></SettingRow>
        </SettingsSection>
      case 'dictation':
        return <SettingsSection id="dictation" title="Audio" description="">
          <SettingRow label="Cleanup level" description=""><select value={draft.cleanupLevel} onChange={(event) => update('cleanupLevel', event.target.value as CleanupLevel)}><option value="none">None</option><option value="light">Light</option><option value="medium">Medium</option></select></SettingRow>
          <SettingRow label="Default style" description=""><select value={draft.defaultStyle} onChange={(event) => update('defaultStyle', event.target.value)}>{data.styles.map((style) => <option value={style.id} key={style.id}>{style.name}</option>)}</select></SettingRow>
          <SettingRow label="Retention" description=""><select value={draft.retention} onChange={(event) => update('retention', event.target.value as PublicSettings['retention'])}><option value="forever">Keep forever</option><option value="24h">Delete after 24 hours</option><option value="never">Never store transcript text</option></select></SettingRow>
        </SettingsSection>
      case 'privacy':
        return <SettingsSection id="privacy" title="Privacy" description=""><div className="privacy-setting"><ShieldCheck size={21} /><div><strong>Privacy</strong><span>Audio and transcripts stay on this device unless a cloud provider is selected.</span></div></div></SettingsSection>
      case 'appearance':
        return <SettingsSection id="appearance" title="Appearance" description=""><SettingRow label="Theme" description=""><div className="theme-choice" role="radiogroup" aria-label="Color theme"><button type="button" className={draft.theme === 'light' ? 'is-selected' : ''} aria-pressed={draft.theme === 'light'} onClick={() => selectTheme('light')}><Sun size={16} /> Light</button><button type="button" className={draft.theme === 'dark' ? 'is-selected' : ''} aria-pressed={draft.theme === 'dark'} onClick={() => selectTheme('dark')}><Moon size={16} /> Dark</button><button type="button" className={draft.theme === 'system' ? 'is-selected' : ''} aria-pressed={draft.theme === 'system'} onClick={() => selectTheme('system')}><Desktop size={16} /> System</button></div></SettingRow></SettingsSection>
    }
  }
  const statusText = saveState === 'saving' ? 'Saving…' : saveState === 'error' ? 'Could not save this setting.' : saveState === 'saved' ? 'Saved locally.' : 'Changes are stored locally.'
  return <><div className="page page-settings"><div className="settings-layout"><nav className="settings-index" aria-label="Settings sections">{['Capture', 'Application'].map((group) => <div className="settings-index-group" key={group}><span>{group}</span>{settingsSections.filter((section) => section.group === group).map((section) => <button type="button" className={activeSection === section.id ? 'is-selected' : ''} aria-current={activeSection === section.id ? 'page' : undefined} key={section.id} onClick={() => setActiveSection(section.id)}><span>{section.label}</span><CaretRight size={13} /></button>)}</div>)}</nav><div className="settings-content">{renderActiveSection()}<div className="settings-save"><Button variant="primary" icon={FloppyDisk} onClick={() => void save()} disabled={saveState === 'saving'}>Save settings</Button><span>{statusText}</span></div></div></div></div>{showShortcutEditor ? <ShortcutEditorModal draft={draft} onClose={() => setShowShortcutEditor(false)} onChange={(keyName, value) => persist({ [keyName]: value } as Partial<PublicSettings>)} /> : null}</>
}

const SettingsSection = ({ id, title, description, children }: { id: string; title: string; description: string; children: ReactNode }) => <section className="settings-section" id={id}><div className="settings-section-header"><h2>{title}</h2>{description ? <p>{description}</p> : null}</div>{children}</section>
const SettingGroup = ({ title, children }: { title: string; children: ReactNode }) => <div className="setting-group"><h3>{title}</h3><div className="setting-group-card">{children}</div></div>
const SettingRow = ({ label, description, children }: { label: string; description: string; children: ReactNode }) => <div className="setting-row"><div><strong>{label}</strong>{description ? <span>{description}</span> : null}</div><div className="setting-control">{children}</div></div>
const Toggle = ({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) => <button className={`toggle ${checked ? 'is-on' : ''}`} type="button" aria-label={label} aria-pressed={checked} onClick={() => onChange(!checked)}><span /></button>
const EmptyState = ({ icon: Icon, title, body, action, onAction }: { icon: NavIcon; title: string; body: string; action: string; onAction: () => void }) => <div className="empty-state"><div className="empty-glyph"><Icon size={27} /></div><div><h3>{title}</h3><p>{body}</p><Button variant="secondary" onClick={onAction}>{action}</Button></div></div>

const OverlayPill = ({ overlay }: { overlay: OverlayState }) => {
  const busy = ['starting', 'recording', 'stopping', 'transcribing', 'processing', 'inserting'].includes(overlay.phase)
  const recording = overlay.phase === 'recording'
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

  if (resting) return <div className="overlay-root is-resting" role="status" aria-label="Flow is ready"><div className="overlay-pill is-idle"><span className="overlay-idle-dot" /><span className="sr-only">Flow is ready</span></div></div>
  const stateLabel = `${phaseLabel[overlay.phase]}${overlay.message ? `: ${overlay.message}` : ''}`
  return <div className={`overlay-root ${busy ? 'is-busy' : ''} ${ready ? 'is-ready' : ''} ${error ? 'is-error' : ''}`}><div className={`overlay-pill ${recording ? 'is-recording' : ''} ${processing ? 'is-processing' : ''} ${ready ? 'is-ready' : ''} ${error ? 'is-error' : ''}`} aria-label={stateLabel} aria-live="polite" data-phase={overlay.phase}><span className="sr-only">{stateLabel}</span><div className="overlay-copy"><div className="overlay-state"><span className={`overlay-dot ${busy ? 'is-live' : ''}`} /><span className="overlay-label">{phaseLabel[overlay.phase]}</span><span className="overlay-mode">{overlay.mode === 'hold' ? 'hold' : 'toggle'}</span><span className="overlay-time">{formatDuration(liveElapsedMs)}</span></div><p>{overlay.message}</p></div>{busy ? <IconButton label="Cancel dictation" icon={X} onClick={() => void api.dictation.cancel()} /> : null}{recording ? <PillGraph level={overlay.level} elapsedMs={liveElapsedMs} /> : null}{processing ? <span className="overlay-processing" aria-label="Processing" /> : null}{recording && overlay.mode === 'toggle' ? <IconButton label="Finish dictation" icon={Check} onClick={() => void api.dictation.stop()} /> : null}{ready ? <><IconButton label="Copy transcript" icon={Copy} onClick={() => void api.dictation.copy(overlay.result)} /><IconButton label="Send transcript to Scratchpad" icon={NotePencil} onClick={() => void api.dictation.sendToScratchpad(overlay.result)} /></> : null}{error ? <IconButton label="Dismiss error" icon={X} onClick={() => void api.dictation.cancel()} /> : null}</div></div>
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
  const playingAudioRef = useRef<HTMLAudioElement | null>(null)
  const captureRef = useRef<{ sessionId: string; recorder: MediaRecorder; stream: MediaStream; chunks: Blob[]; startedAt: number; audioContext: AudioContext | null; levelTimer: number | null; cancelled: boolean } | null>(null)

  useEffect(() => {
    document.title = 'Flow'
    document.body.dataset.window = isOverlay ? 'overlay' : 'main'
    return () => { delete document.body.dataset.window }
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
      const candidate = payload as { kind?: string; page?: PageId; shortcut?: string }
      if (candidate.kind === 'refresh' || candidate.kind === 'scratchpad-updated') void refresh()
      if (candidate.kind === 'navigate' && candidate.page) setPage(candidate.page)
      if (candidate.kind === 'shortcut-unavailable') notify(`Could not register ${candidate.shortcut ?? 'the shortcut'}. Use the Start button or tray.`, 'error')
      if (candidate.kind === 'shortcut-ready') notify(`Global toggle ready: ${(candidate.shortcut ?? '').replaceAll('Control', 'Ctrl').replaceAll('Super', 'Win')}`, 'neutral')
    })
    if (!isOverlay && new URLSearchParams(window.location.search).get('smoke') === '1') void api.app.health()
    return () => { offState(); offOverlay(); offLevel(); offToast() }
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
    if (!capture || !payload || typeof payload !== 'object') return
    const candidate = payload as { sessionId?: unknown }
    if (candidate.sessionId !== capture.sessionId) return
    capture.recorder.stop()
  }, [])

  const cancelCapture = useCallback((payload: unknown) => {
    const capture = captureRef.current
    if (!capture || !payload || typeof payload !== 'object') return
    const candidate = payload as { sessionId?: unknown }
    if (candidate.sessionId !== capture.sessionId) return
    capture.cancelled = true
    capture.recorder.stop()
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

  if (isOverlay) return <OverlayPill overlay={overlay} />

  const renderPage = () => {
    switch (page) {
      case 'dictation': return <DictationPage data={data} overlay={overlay} onStart={() => void start()} onOpenStyle={() => setPage('style')} onStop={() => void stop()} onCancel={() => void cancel()} onCopy={() => void copy()} onScratchpad={() => void scratchpad()} onDelete={deleteRecord} onPlay={playRecord} playingId={playingId} onAction={transcriptAction} />
      case 'insights': return <InsightsPage data={data} />
      case 'dictionary': return <DictionaryPage entries={data.dictionary} onRefresh={() => void refresh()} />
      case 'snippets': return <SnippetsPage snippets={data.snippets} onRefresh={() => void refresh()} />
      case 'style': return <StylePage styles={data.styles} settings={data.settings} onRefresh={() => void refresh()} />
      case 'transforms': return <TransformsPage transforms={data.transforms} onRefresh={() => void refresh()} />
      case 'scratchpad': return <ScratchpadPage value={data.scratchpad} onRefresh={() => void refresh()} />
      case 'settings': return <SettingsPage data={data} onRefresh={() => void refresh()} onThemePreview={setThemePreview} />
    }
  }

  return <div className={`app-shell theme-${themePreview ?? data.settings.theme} ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}><a className="skip-link" href="#main-content">Skip to content</a><AppChrome notificationsOpen={notificationsOpen} onNotifications={() => setNotificationsOpen((current) => !current)} sidebarCollapsed={sidebarCollapsed} onToggleSidebar={() => setSidebarCollapsed((current) => !current)} /><Sidebar page={page} setPage={setPage} collapsed={sidebarCollapsed} /><main className="main-canvas" id="main-content" tabIndex={-1}><div className="main-inner"><PageHeader page={page} />{notice ? <Notice message={notice.message} tone={notice.tone} onDismiss={() => setNotice(null)} /> : null}{renderPage()}</div></main></div>
}
