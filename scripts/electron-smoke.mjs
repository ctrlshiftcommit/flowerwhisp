import { createRequire } from 'node:module'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { spawn } from 'node:child_process'

const require = createRequire(import.meta.url)
const root = process.cwd()
const evidenceDir = path.join(root, 'artifacts', 'electron-smoke')
const evidencePath = path.join(evidenceDir, 'evidence.json')
const logPath = path.join(evidenceDir, 'process.log')

await rm(evidenceDir, { recursive: true, force: true })
await mkdir(evidenceDir, { recursive: true })

const electronPath = require('electron')
// Some managed Windows hosts expose a broken Chromium GPU runtime. Keep the
// app's normal launch path untouched, but make the smoke harness deterministic
// by exercising the same renderer through Chromium's software path.
const child = spawn(electronPath, ['--disable-gpu', '.'], {
  cwd: root,
  env: { ...process.env, FLOWERWHISP_SMOKE: '1' },
  stdio: ['ignore', 'pipe', 'pipe'],
  windowsHide: true,
})

let output = ''
const append = (value) => {
  output += String(value)
  if (output.length > 18_000) output = output.slice(-18_000)
}

child.stdout?.on('data', append)
child.stderr?.on('data', append)

let timedOut = false
const timeout = setTimeout(() => {
  timedOut = true
  child.kill()
}, 30_000)

const exitCode = await new Promise((resolve) => {
  child.once('error', () => resolve(1))
  child.once('close', (code) => resolve(code ?? 1))
})
clearTimeout(timeout)

await writeFile(logPath, output, 'utf8')

let evidence = null
try {
  evidence = JSON.parse(await readFile(evidencePath, 'utf8'))
} catch {
  evidence = null
}

const passed = !timedOut && exitCode === 0 && evidence?.rendererLoaded === true && evidence?.preloadBridge === true && evidence?.contextIsolation === true && evidence?.nodeIntegration === false && evidence?.sandbox === true
const report = { passed, exitCode, timedOut, evidence, logPath }
console.log(JSON.stringify(report, null, 2))

if (!passed) {
  console.error(output || 'Electron smoke did not produce runtime evidence.')
  process.exitCode = 1
}
