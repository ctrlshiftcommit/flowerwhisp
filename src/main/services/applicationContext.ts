import type { ApplicationContextSource, WritingPurpose } from '../../shared/writingContext'
import type { InsertionTarget } from './insertion'

export interface ApplicationDetection {
  applicationName: string
  purpose?: WritingPurpose
  source: ApplicationContextSource
  automaticCleanupAllowed: boolean
  classifierInput?: string
  cacheKey?: string
}

interface ApplicationRule {
  purpose: WritingPurpose
  applicationName: string
}

const PROCESS_RULES: Record<string, ApplicationRule> = {
  whatsapp: { purpose: 'personal', applicationName: 'WhatsApp' },
  telegram: { purpose: 'personal', applicationName: 'Telegram' },
  signal: { purpose: 'personal', applicationName: 'Signal' },
  discord: { purpose: 'personal', applicationName: 'Discord' },
  messenger: { purpose: 'personal', applicationName: 'Messenger' },
  slack: { purpose: 'work', applicationName: 'Slack' },
  teams: { purpose: 'work', applicationName: 'Microsoft Teams' },
  msteams: { purpose: 'work', applicationName: 'Microsoft Teams' },
  'ms-teams': { purpose: 'work', applicationName: 'Microsoft Teams' },
  zoom: { purpose: 'work', applicationName: 'Zoom' },
  outlook: { purpose: 'email', applicationName: 'Outlook' },
  olk: { purpose: 'email', applicationName: 'Outlook' },
  hxoutlook: { purpose: 'email', applicationName: 'Outlook' },
  thunderbird: { purpose: 'email', applicationName: 'Thunderbird' },
  superhuman: { purpose: 'email', applicationName: 'Superhuman' },
  mailbird: { purpose: 'email', applicationName: 'Mailbird' },
  notion: { purpose: 'other', applicationName: 'Notion' },
  obsidian: { purpose: 'other', applicationName: 'Obsidian' },
  notepad: { purpose: 'other', applicationName: 'Notepad' },
  wordpad: { purpose: 'other', applicationName: 'WordPad' },
  winword: { purpose: 'other', applicationName: 'Microsoft Word' },
  excel: { purpose: 'other', applicationName: 'Microsoft Excel' },
  powerpnt: { purpose: 'other', applicationName: 'Microsoft PowerPoint' },
  onenote: { purpose: 'other', applicationName: 'Microsoft OneNote' },
  code: { purpose: 'other', applicationName: 'Visual Studio Code' },
  cursor: { purpose: 'other', applicationName: 'Cursor' },
  devenv: { purpose: 'other', applicationName: 'Visual Studio' },
  explorer: { purpose: 'other', applicationName: 'File Explorer' },
  acrobat: { purpose: 'other', applicationName: 'Adobe Acrobat' },
  acrord32: { purpose: 'other', applicationName: 'Adobe Acrobat Reader' },
  chatgpt: { purpose: 'other', applicationName: 'ChatGPT' },
}

const BROWSER_NAMES: Record<string, string> = {
  chrome: 'Google Chrome',
  msedge: 'Microsoft Edge',
  firefox: 'Mozilla Firefox',
  brave: 'Brave',
  'brave-browser': 'Brave',
  opera: 'Opera',
  opera_gx: 'Opera GX',
  vivaldi: 'Vivaldi',
  arc: 'Arc',
}

const WEB_APPLICATION_RULES: Array<{ pattern: RegExp; rule: ApplicationRule }> = [
  { pattern: /\bgmail\b/i, rule: { purpose: 'email', applicationName: 'Gmail' } },
  { pattern: /\bsuperhuman\b/i, rule: { purpose: 'email', applicationName: 'Superhuman' } },
  { pattern: /\boutlook\b/i, rule: { purpose: 'email', applicationName: 'Outlook' } },
  { pattern: /\bslack\b/i, rule: { purpose: 'work', applicationName: 'Slack' } },
  { pattern: /\bmicrosoft teams\b|\bteams\b/i, rule: { purpose: 'work', applicationName: 'Microsoft Teams' } },
  { pattern: /\blinkedin\b/i, rule: { purpose: 'work', applicationName: 'LinkedIn' } },
  { pattern: /\bwhatsapp\b/i, rule: { purpose: 'personal', applicationName: 'WhatsApp' } },
  { pattern: /\btelegram\b/i, rule: { purpose: 'personal', applicationName: 'Telegram' } },
  { pattern: /\bsignal\b/i, rule: { purpose: 'personal', applicationName: 'Signal' } },
  { pattern: /\bdiscord\b/i, rule: { purpose: 'personal', applicationName: 'Discord' } },
  { pattern: /\bmessenger\b/i, rule: { purpose: 'personal', applicationName: 'Messenger' } },
  { pattern: /\bgoogle docs\b/i, rule: { purpose: 'other', applicationName: 'Google Docs' } },
  { pattern: /\bnotion\b/i, rule: { purpose: 'other', applicationName: 'Notion' } },
]

const ADDRESS_FIELD_PATTERN = /chrome_omniboxview|omnibox|urlbar|address(?: and search)? bar|search or enter (?:a )?(?:web )?address|location bar/i

const normalizeProcessName = (value: string | undefined): string => (value ?? '')
  .trim()
  .toLowerCase()
  .replace(/\.exe$/i, '')

const displayUnknownProcess = (processName: string): string => processName
  .replace(/[_-]+/g, ' ')
  .replace(/\b\w/g, (letter) => letter.toUpperCase())
  .trim() || 'Unknown application'

const detected = (
  rule: ApplicationRule,
  automaticCleanupAllowed: boolean,
): ApplicationDetection => ({
  ...rule,
  source: 'rule',
  automaticCleanupAllowed,
})

export const detectApplicationContext = (target: InsertionTarget | null | undefined): ApplicationDetection => {
  if (!target) {
    return {
      applicationName: 'Unknown application',
      purpose: 'other',
      source: 'fallback',
      automaticCleanupAllowed: true,
    }
  }

  const processName = normalizeProcessName(target.processName)
  const browserName = BROWSER_NAMES[processName]
  const focusDescriptor = `${target.focusClass ?? ''} ${target.automationId ?? ''}`
  const isAddressBar = Boolean(browserName) && (target.isBrowserAddressBar === true || ADDRESS_FIELD_PATTERN.test(focusDescriptor))

  if (browserName) {
    const webApplication = WEB_APPLICATION_RULES.find(({ pattern }) => pattern.test(target.windowTitle ?? ''))
    if (webApplication) return detected(webApplication.rule, !isAddressBar)
    return detected({ purpose: 'other', applicationName: browserName }, !isAddressBar)
  }

  const processRule = PROCESS_RULES[processName]
  if (processRule) return detected(processRule, true)

  if (!processName) {
    return {
      applicationName: 'Unknown application',
      purpose: 'other',
      source: 'fallback',
      automaticCleanupAllowed: true,
    }
  }

  return {
    applicationName: displayUnknownProcess(processName),
    source: 'fallback',
    automaticCleanupAllowed: true,
    classifierInput: processName,
    cacheKey: processName,
  }
}
