# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

Windows desktop via an Electron shell is the required runtime; the renderer is a local web surface and the app is not intended to be deployed as a website.

## Stack

Greenfield Electron + React + TypeScript + Vite. The user explicitly excluded WinUI, C++, and Rust. The desktop shell owns privileged capabilities through a narrow preload bridge.

## Users

Inferred from the supplied build brief: people who write across Windows desktop applications and want to speak naturally instead of repeatedly typing. Their recurring job is moving a polished transcript into the text field that already has focus without opening a large recorder.

## Product Purpose

FlowerWhisp is a quiet desktop dictation utility. A global shortcut starts or toggles a recording, a tiny floating pill communicates state, the audio is transcribed and optionally cleaned up, and the result can be copied for manual paste or kept in the app-owned Scratchpad. Success means the workflow feels immediate, unobtrusive, recoverable, and useful across the day.

## Positioning

Inferred from the brief: the product's meaningful mechanism is an invisible-first dictation loop — global shortcut, tiny always-on-top pill, separate raw/cleaned/final text, and provider-independent processing — rather than a large voice dashboard or a website-only microphone demo.

## Operating Context

Windows users leave the utility running while working in browsers, editors, chat clients, mail, documents, and AI tools. The main window manages settings and history; the tray and overlay support background use. The active application and its focus are sacred during dictation; the strict Electron baseline does not claim arbitrary external-window insertion.

## Capabilities and Constraints

- Electron-only implementation with React renderer, secure main/preload boundary, local persistence, system tray, and a Windows-first desktop window.
- Global toggle shortcut is implemented through Electron's globalShortcut. Hold-to-dictate is represented as a separate mode and must not be falsely described as proven system-wide without a key-up capable OS hook.
- Microphone capture uses browser MediaRecorder in the renderer and sends audio to the main-process provider pipeline.
- Transcription and LLM cleanup are separate provider interfaces. Groq is supported when configured; a configured local command/model folder is supported through a main-process adapter.
- Dictionary replacements are deterministic and happen before cleanup. Raw transcription is never discarded.
- External insertion is intentionally not claimed in the strict Electron baseline. The app offers explicit clipboard copy for manual paste and a real app-owned Scratchpad path; no native helper or focus-stealing SendInput path is bundled.
- API credentials remain in the main process and use Electron safeStorage where available; secrets must not be logged, committed, or returned to the renderer.
- Open decisions: the exact local transcriber command and whether a future native key-up helper is acceptable remain configuration/runtime concerns.

## Brand Commitments

- Product name: FlowerWhisp.
- Visual reference: the supplied Whisper Flow brief. The interface is minimal, editorial, premium, calm, spacious, warm ivory, near-black, muted gray, and restrained accent color; it avoids neon cyberpunk, giant recording controls, excessive gradients, and noisy dashboard treatment.
- The tiny dictation pill, sidebar hierarchy, style cards, settings rows, transforms, and insights layout are product-defining references. Do not replace them with generic component-library defaults.
- Interface copy is concrete and operational. Never use emoji as interface icons.

## Evidence on Hand

- Full product and acceptance brief is maintained outside this repository.
- Separate official Whisper checkout exists at `D:\Github\openai whisper Transcriber`; it is outside this repository and must remain untouched.
- No live screenshot asset was available in the workspace; visual decisions use the pasted brief's explicit description and are labeled accordingly.
- No live API key, deployed runtime, or target application for external insertion is available for local verification.

## Product Principles

1. Get out of the user's way: the overlay is tiny and the management UI is secondary.
2. Preserve truth: raw, cleaned, and inserted text remain recoverable.
3. Keep providers replaceable: transcription and cleanup are independent systems.
4. Make privacy legible: local data stays local unless the user selects cloud services.
5. Give every failure a recovery path rather than silently losing a dictation.

## Accessibility & Inclusion

The renderer must support keyboard navigation, visible focus, readable contrast, labeled controls, reduced-motion preferences, responsive resizing, non-ASCII text, and state communication that is not color-only. Audio and network failures need actionable text near the affected control.
