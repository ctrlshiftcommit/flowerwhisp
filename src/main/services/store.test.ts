import { afterEach, describe, expect, it } from 'vitest'
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'

import type { PublicSettings } from '../../shared/ipc'
import { emptySnapshot, JsonStateStore } from './store'

const temporaryDirectories: string[] = []

afterEach(async () => {
  await Promise.all(temporaryDirectories.splice(0).map((directory) => rm(directory, { recursive: true, force: true })))
})

describe('style settings migration', () => {
  it('ships three real punctuation-and-casing profiles for every writing purpose', () => {
    const snapshot = emptySnapshot()

    expect(snapshot.styles).toHaveLength(12)
    for (const category of ['personal', 'work', 'email', 'other'] as const) {
      const styles = snapshot.styles.filter((style) => style.category === category)
      expect(styles).toHaveLength(3)
      expect(styles.some((style) => style.id === snapshot.settings.styleByCategory[category])).toBe(true)
      expect(styles.flatMap((style) => style.rules).join(' ')).not.toMatch(/change tone|more formal|shorten|rewrite/i)
    }
  })

  it('upgrades a legacy global selection without retaining stale built-in tone rules', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'flowerwhisp-store-'))
    temporaryDirectories.push(directory)
    const filePath = path.join(directory, 'state.json')
    const legacy = emptySnapshot()
    const legacySettings: Partial<PublicSettings> = { ...legacy.settings, defaultStyle: 'custom-work' }
    delete legacySettings.styleByCategory
    legacy.settings = legacySettings as PublicSettings
    legacy.styles = [
      {
        id: 'work-clear',
        name: 'Old work style',
        description: 'Old style',
        example: 'Old example',
        rules: ['Make the wording more formal.'],
        category: 'work',
      },
      {
        id: 'custom-work',
        name: 'Custom work casing',
        description: 'Custom profile',
        example: 'custom example',
        rules: ['Use sentence case.'],
        category: 'work',
      },
    ]
    await writeFile(filePath, JSON.stringify(legacy), 'utf8')

    const loaded = await new JsonStateStore(filePath).load()

    expect(loaded.settings.styleByCategory.work).toBe('custom-work')
    expect(loaded.styles.filter((style) => style.category === 'work')).toHaveLength(4)
    expect(loaded.styles.find((style) => style.id === 'work-clear')?.rules.join(' ')).not.toContain('more formal')
    expect(loaded.styles.find((style) => style.id === 'custom-work')?.rules).toEqual(['Use sentence case.'])
  })
})
