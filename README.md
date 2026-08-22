# FlowerWhisp

FlowerWhisp is a Windows-first Electron desktop dictation utility with a React renderer, a secure preload bridge, local history, editable cleanup prompts, reusable transforms, and a small recording pill.

## Package manager

This repository uses **npm**. The checked-in `package-lock.json` keeps installs reproducible.

```powershell
npm install
```

## Development

```powershell
npm run dev
```

Useful verification commands:

```powershell
npm test
npm run build
```

## Windows packaging

Build both Windows distribution formats:

```powershell
npm run package
```

The package scripts intentionally write executables outside this repository. By default, they use:

```text
%TEMP%\FlowerWhisp-packages
```

This produces:

- `FlowerWhisp-<version>-Setup.exe` — an installable Windows installer. It lets the user choose an installation directory and creates Start Menu and desktop shortcuts.
- `FlowerWhisp-<version>-Portable.exe` — a portable executable that can be run without an installation step.

To keep the build permanently in a folder such as Downloads, set an output directory outside the repository before packaging:

```powershell
$env:FLOWERWHISP_OUTPUT_DIR = "$env:USERPROFILE\Downloads\FlowerWhisp-packages"
npm run package
```

Build only one format when needed:

```powershell
npm run package:installer
npm run package:portable
npm run package:dir
```

Run the installer executable and choose the destination folder. For a quick local test, run the portable executable directly. Windows SmartScreen may display a warning because this development build is unsigned; code signing can be added later without changing the npm workflow.

The packaging script refuses to write inside the repository, and `.gitignore` also protects against accidental `.exe` and installer metadata files. Upload the source repository with `package.json` and `package-lock.json`; other developers can reproduce the executables with `npm install` followed by `npm run package`.
