# Workspace instructions

## Scope

This repository contains the FlowerWhisp desktop reference and the Android implementation under `mobile/`. For Android work, `mobile/` is the source of truth. Keep desktop code and unrelated changes untouched.

## Product contract

FlowerWhisp is a local-first, system-level dictation utility. The user selects a text field in another app, invokes the floating bubble, speaks, and receives polished text at the cursor without replacing the keyboard. If direct insertion fails, the app must expose a visible clipboard recovery path. Raw text and audio recovery data must not disappear after a provider failure.

The Android implementation owns the data layer, settings, permissions, foreground microphone service, accessibility insertion service, overlay bubble, provider adapters, and recovery UI. Preserve those boundaries while changing presentation.

## UI ownership

- Kotlin and Jetpack Compose own the mobile screens, navigation, animation, semantics, and state rendering.
- `MainViewModel` and the existing reducer-style callbacks remain the source of truth for screen state and side effects.
- Android platform code owns microphone permission, notification permission, overlay settings, accessibility settings, foreground service lifecycle, and clipboard fallback.
- Do not move platform services into a web view or JavaScript runtime as part of visual work.

## Visual direction

The approved redesign direction is a quiet editorial interface: near-black ink canvas, warm paper-white text, graphite outlines, and one restrained terracotta/clay accent. Do not introduce cool blue accents or botanical accents into the UI. Do not use gradients, glossy effects, generic dashboard cards, emoji icons, or stock Material styling as the visual source of truth.

The logo is shared with the desktop app. Use the existing `assets/flowerwhisp.png` waveform-flower mark for Android launcher and in-app brand placement; do not generate a second logo or replace it with a filled mascot.

Use native Android semantics and at least 48dp interactive targets. Support edge-to-edge insets, large text, reduced motion, and recovery states. Keep the bubble compact when idle and make recording, processing, success, and recovery states visually distinct.

## Change safety

Inspect `git status` before editing. Preserve unrelated user changes. Use `apply_patch` for source and documentation edits. Do not add secrets, API keys, device identifiers, or generated build output to source control.

## Verification

For UI changes, run the relevant JVM tests, lint, and debug APK assembly. If a device or emulator is available, install as an upgrade and inspect every changed route plus the overlay. Report source/build/install/runtime evidence separately. If no device is available, say so rather than treating a successful build as visual proof.
