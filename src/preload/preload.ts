import { contextBridge, ipcRenderer } from 'electron'

import type { AppEventChannel, FlowerWhispApi, PublicSettings } from '../shared/ipc'

const on = (channel: AppEventChannel, listener: (payload: unknown) => void): (() => void) => {
  const handler = (_event: Electron.IpcRendererEvent, payload: unknown) => listener(payload)
  ipcRenderer.on(channel, handler)
  return () => ipcRenderer.removeListener(channel, handler)
}

const api: FlowerWhispApi = {
  app: {
    health: () => ipcRenderer.invoke('app:health'),
    quit: () => ipcRenderer.invoke('app:quit'),
    minimize: () => ipcRenderer.invoke('window:minimize'),
    toggleMaximize: () => ipcRenderer.invoke('window:toggle-maximize'),
    close: () => ipcRenderer.invoke('window:close'),
  },
  bootstrap: () => ipcRenderer.invoke('app:bootstrap'),
  dictation: {
    start: (options) => ipcRenderer.invoke('dictation:start', options),
    stop: () => ipcRenderer.invoke('dictation:stop'),
    cancel: () => ipcRenderer.invoke('dictation:cancel'),
    copy: (text) => ipcRenderer.invoke('dictation:copy', text),
    sendToScratchpad: (text) => ipcRenderer.invoke('dictation:scratchpad', text),
  },
  audio: {
    submit: (payload) => ipcRenderer.invoke('audio:submit', payload),
    reportLevel: (sessionId, level) => ipcRenderer.send('audio:level', { sessionId, level }),
    reportError: (sessionId, message) => ipcRenderer.send('audio:error', { sessionId, message }),
  },
  pill: {
    setHovered: (hovered) => ipcRenderer.send('pill:hovered', hovered),
  },
  settings: {
    save: (patch: Partial<PublicSettings>) => ipcRenderer.invoke('settings:save', patch),
    setShortcutRecording: (recording: boolean) => ipcRenderer.invoke('settings:shortcut-recording', recording),
    setGroqKey: (value) => ipcRenderer.invoke('settings:set-key', value),
    clearGroqKey: () => ipcRenderer.invoke('settings:clear-key'),
  },
  history: {
    delete: (id) => ipcRenderer.invoke('history:delete', id),
    copy: (id) => ipcRenderer.invoke('history:copy', id),
    audio: (id) => ipcRenderer.invoke('history:audio', id),
    play: (id) => ipcRenderer.invoke('history:play', id),
    undo: (id) => ipcRenderer.invoke('history:undo', id),
    retry: (id) => ipcRenderer.invoke('history:retry', id),
    extract: (id) => ipcRenderer.invoke('history:extract', id),
  },
  recovery: {
    retry: (id) => ipcRenderer.invoke('recovery:retry', id),
    discard: (id) => ipcRenderer.invoke('recovery:discard', id),
  },
  dictionary: {
    save: (entry) => ipcRenderer.invoke('dictionary:save', entry),
    delete: (id) => ipcRenderer.invoke('dictionary:delete', id),
  },
  snippets: {
    save: (snippet) => ipcRenderer.invoke('snippets:save', snippet),
    delete: (id) => ipcRenderer.invoke('snippets:delete', id),
  },
  transforms: {
    save: (transform) => ipcRenderer.invoke('transforms:save', transform),
    delete: (id) => ipcRenderer.invoke('transforms:delete', id),
  },
  scratchpad: {
    read: () => ipcRenderer.invoke('scratchpad:read'),
    save: (value) => ipcRenderer.invoke('scratchpad:save', value),
  },
  command: {
    run: (sourceText, instructions) => ipcRenderer.invoke('command:run', sourceText, instructions),
    apply: (text) => ipcRenderer.invoke('command:apply', text),
    askPerplexity: (sourceText, question) => ipcRenderer.invoke('command:perplexity', sourceText, question),
  },
  on,
}

contextBridge.exposeInMainWorld('flowerWhisp', api)
