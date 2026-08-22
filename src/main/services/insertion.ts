import { clipboard } from 'electron'

export interface CopyOutcome {
  outcome: 'copied'
  message: string
}

export const copyForManualPaste = (text: string): CopyOutcome => {
  const normalized = text.trim()
  if (!normalized) throw new Error('There is no transcript to copy.')
  clipboard.writeText(normalized)
  return {
    outcome: 'copied',
    message: 'Copied to the clipboard. Press Ctrl+V in the target application.',
  }
}
