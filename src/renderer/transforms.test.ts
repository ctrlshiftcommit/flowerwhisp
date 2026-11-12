// @vitest-environment jsdom
import { act, createElement } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { BootstrapPayload, FlowerWhispApi } from '../shared/ipc'
import { emptySnapshot } from '../main/services/store'

const save = vi.fn()
let root: Root
let container: HTMLDivElement
const refresh = vi.fn()

beforeEach(async () => {
  Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true })
  window.flowerWhisp = { transforms: { save } } as unknown as FlowerWhispApi
  save.mockReset()
  container = document.createElement('div')
  document.body.append(container)
  root = createRoot(container)
  const { TransformsPage } = await import('./App')
  await act(async () => root.render(createElement(TransformsPage, {
    data: { ...emptySnapshot(), transformShortcutRegistrations: {} } as unknown as BootstrapPayload,
    onRefresh: refresh,
  })))
}, 30_000)

afterEach(async () => {
  await act(async () => root.unmount())
  container.remove()
})

const click = async (text: string) => {
  const button = [...container.querySelectorAll('button')].find((item) => item.textContent === text)!
  expect(button).toBeTruthy()
  await act(async () => button.click())
}

const enter = async (selector: string, value: string) => {
  const input = container.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!
  const prototype = input instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype
  await act(async () => {
    Object.getOwnPropertyDescriptor(prototype, 'value')!.set!.call(input, value)
    input.dispatchEvent(new Event('input', { bubbles: true }))
  })
}

it('creates a transform without requiring a shortcut', async () => {
  save.mockResolvedValue({ ok: true })
  await click('Create New')
  await enter('#transform-name', 'Short answer')
  await enter('#transform-instructions', 'Use one sentence.')
  await click('Save')
  expect(save).toHaveBeenCalledWith(expect.objectContaining({ name: 'Short answer', instructions: 'Use one sentence.', shortcut: '', enabled: true }))
  expect(container.querySelector('#transform-name')).toBeNull()
  expect(refresh).toHaveBeenCalled()
})

it('keeps a new transform draft and shows failed saves so they can be retried', async () => {
  save.mockResolvedValueOnce({ ok: false, error: 'Shortcut is unavailable.' }).mockResolvedValueOnce({ ok: true })
  await click('Create New')
  await enter('#transform-name', 'Short answer')
  await enter('#transform-instructions', 'Use one sentence.')
  await click('Save')
  expect(container.querySelector('[role="alert"]')?.textContent).toBe('Shortcut is unavailable.')
  expect(container.querySelector<HTMLInputElement>('#transform-name')?.value).toBe('Short answer')
  await click('Save')
  expect(container.querySelector('#transform-name')).toBeNull()
})

it('edits built-in prompts and recovers from rejected save requests', async () => {
  save.mockRejectedValueOnce(new Error('Disk write failed.')).mockResolvedValueOnce({ ok: true })
  await click('Edit prompt')
  await enter('#prompt-editor-text', 'My custom system instruction.')
  await click('Save prompt')
  expect(container.querySelector('[role="alert"]')?.textContent).toBe('Disk write failed.')
  await click('Save prompt')
  expect(save).toHaveBeenLastCalledWith(expect.objectContaining({ id: 'polish', builtIn: true, instructions: 'My custom system instruction.' }))
  expect(container.querySelector('[role="dialog"]')).toBeNull()
})
