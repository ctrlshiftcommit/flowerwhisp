import { spawnSync } from "node:child_process"
import { mkdirSync } from "node:fs"
import { dirname, isAbsolute, join, relative, resolve, sep } from "node:path"
import { fileURLToPath } from "node:url"
import { tmpdir } from "node:os"

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..")
const requestedOutput = process.env.FLOWERWHISP_OUTPUT_DIR
const outputDirectory = resolve(
  requestedOutput || join(process.env.TEMP || tmpdir(), "FlowerWhisp-packages"),
)

const relativeOutput = relative(repoRoot, outputDirectory)
const outputIsInsideRepository =
  relativeOutput === "" ||
  (!isAbsolute(relativeOutput) &&
    !relativeOutput.startsWith(`..${sep}`) &&
    relativeOutput !== "..")

if (outputIsInsideRepository) {
  console.error(
    `Refusing to write packaged executables inside the repository: ${outputDirectory}`,
  )
  console.error(
    "Set FLOWERWHISP_OUTPUT_DIR to a folder outside the repository and run the command again.",
  )
  process.exit(1)
}

mkdirSync(outputDirectory, { recursive: true })

const builderCacheDirectory = resolve(
  process.env.ELECTRON_BUILDER_CACHE ||
    join(process.env.TEMP || tmpdir(), "FlowerWhisp-builder-cache"),
)
mkdirSync(builderCacheDirectory, { recursive: true })

const builderCommand = resolve(
  repoRoot,
  "node_modules",
  ".bin",
  process.platform === "win32" ? "electron-builder.cmd" : "electron-builder",
)
const target = process.argv[2]
const builderArguments = [
  "--win",
  ...(target ? [target] : []),
  `--config.directories.output=${outputDirectory}`,
]

console.log(`Packaging FlowerWhisp to: ${outputDirectory}`)

const result = spawnSync(builderCommand, builderArguments, {
  cwd: repoRoot,
  env: {
    ...process.env,
    ELECTRON_BUILDER_CACHE: builderCacheDirectory,
  },
  stdio: "inherit",
  shell: process.platform === "win32",
})

if (result.error) {
  console.error(result.error)
  process.exit(1)
}

process.exit(result.status ?? 1)
