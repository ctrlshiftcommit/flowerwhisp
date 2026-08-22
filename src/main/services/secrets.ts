import { mkdir, readFile, unlink, writeFile } from 'node:fs/promises'
import path from 'node:path'

import { safeStorage } from 'electron'

export class SecretStore {
  public constructor(private readonly rootPath: string) {}

  private get groqPath(): string {
    return path.join(this.rootPath, 'groq-api-key.bin')
  }

  public isAvailable(): boolean {
    return safeStorage.isEncryptionAvailable()
  }

  public async hasGroqKey(): Promise<boolean> {
    try {
      const encrypted = await readFile(this.groqPath)
      return encrypted.length > 0 && this.isAvailable()
    } catch {
      return false
    }
  }

  public async setGroqKey(value: string): Promise<void> {
    const trimmed = value.trim()
    if (!trimmed) {
      await this.clearGroqKey()
      return
    }
    if (!this.isAvailable()) {
      throw new Error('Secure credential storage is unavailable on this system.')
    }
    await mkdir(this.rootPath, { recursive: true })
    await writeFile(this.groqPath, safeStorage.encryptString(trimmed))
  }

  public async getGroqKey(): Promise<string | null> {
    if (!this.isAvailable()) return null
    try {
      return safeStorage.decryptString(await readFile(this.groqPath))
    } catch {
      return null
    }
  }

  public async clearGroqKey(): Promise<void> {
    await unlink(this.groqPath).catch(() => undefined)
  }
}

