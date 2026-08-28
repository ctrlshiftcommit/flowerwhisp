# FlowerWhisp Android UI audit

Date: 2026-08-28

## Scope

This audit covers the onboarding and permission journey, the primary dictation surface, the floating bubble, the missing insights surface, navigation, and the shared Compose visual language. Product behavior and native service contracts remain in scope but are not being replaced.

## Evidence

- The mobile module is a Kotlin/Jetpack Compose Android app under `mobile/`.
- The baseline `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` gates pass with the Android Studio JDK 17 runtime.
- The current source exposes Home, History, Library, and Settings only. There is no Insights destination and no dedicated dictation-oriented composition.
- Onboarding is a nine-step linear sequence with separate bubble, overlay, accessibility, microphone, tap, hold, and real-test screens. Special access and runtime permission states are not composed as one recoverable checklist.
- The Compose layer uses shared Material 3 cards, buttons, navigation components, and raw accent constants. The overlay Canvas has a separate hardcoded color language and a fixed compact shape, so the in-app bubble and always-on-top bubble do not read as one product.
- No Android device or emulator was available during the baseline inspection, so incumbent and final screenshot evidence must be captured when a target becomes available.

## Findings

### P1: Onboarding asks for access before establishing trust

The current nine-step flow makes users advance through mechanics before they understand the product. Overlay, accessibility, microphone, tap, hold, and test are presented as separate gates instead of one clear path to the first successful insertion. A denied permission is not given a focused repair state with a clear next action.

**Design response:** reduce the journey to five purposeful moments: welcome, access checklist, microphone, one real test, and ready. Each permission row shows live status. Explain why before opening system settings. Preserve the current step across recreation and allow a safe “finish later” path without pretending setup is complete.

### P1: The primary action is buried inside a readiness dashboard

Home currently mixes readiness, bubble status, capabilities, service messaging, and controls. The user’s most important action, dictation, does not have a strong visual stage. The page reads like settings rather than a tool used repeatedly throughout the day.

**Design response:** make the primary route a Dictate composition: one large stateful action, a compact live transcript/recovery area, the current capability signal, and a small recent-history glimpse. Keep repair actions close to the state that needs repair.

### P1: Insights are absent, so the app has no reflection loop

The data model already contains dictation history, durations, language, status, and word counts, but there is no route that turns those facts into useful feedback.

**Design response:** add an Insights route with derived, honest metrics: sessions, words, speaking time, average words per session, recent activity, and the most-used language when data exists. Show an explicit empty state rather than fabricated charts or motivational numbers.

### P1: Bubble states are visually generic and diverge across surfaces

The Compose bubble is a rounded Material surface with icon-and-label swaps. The overlay is a separate Canvas circle with hardcoded accent colors and limited recording feedback. Ready, recording, processing, success, and recovery do not share a coherent visual grammar.

**Design response:** treat the bubble as a single compact instrument. Use a graphite seed at rest, a warm clay listening mark with an amplitude waveform while recording, a quiet stepped processing state, and a resolved clay/cream completion mark. Keep the idle hit target at or above 48dp, expand only when content requires it, and retain clear accessible labels.

### P2: Component defaults flatten hierarchy

Repeated full-width cards, identical corner radii, and default Material button proportions make every element feel equally important. The interface has no strong canvas-to-surface-to-control rhythm.

**Design response:** use a small token set, custom surfaces, intentional grouping, separators, and typography-led hierarchy. Material behavior and semantics may remain, but stock component appearance should not define the product.

### P2: Permission handling needs explicit recovery states

Microphone is a runtime permission while overlay and accessibility are special settings. Treating them as equivalent forward steps obscures how the user recovers after denial, cancellation, or returning from Settings.

**Design response:** show `Ready`, `Needs access`, and `Open settings` states per capability. Refresh on resume, never request automatically on screen entry, and make the primary CTA depend on the first unresolved capability.

### P2: The design system has no approved accent after the old direction

The original contract used a botanical accent that no longer matches the user’s direction. Previous generated references used a cool accent and were rejected. Those directions are not implementation references.

**Design response:** use graphite, warm paper-white, neutral clay surfaces, and one restrained terracotta accent. Accent use is limited to primary action, active recording, focus, and resolved status. There are no cool blue or botanical accent tokens.

### P2: Brand identity must remain shared across desktop and mobile

The generated logo explorations were rejected. The desktop app already has an established waveform-flower mark at `assets/flowerwhisp.png`; introducing a second Android-only symbol would make the product feel fragmented.

**Design response:** reuse the desktop asset unchanged for Android launcher and in-app brand placement. UI controls remain custom and should not be derived from the logo artwork.

## Direction contract

**Design read:** quiet instrument for turning thought into text.

- Variance: 6/10. The shell is calm; the dictation instrument is distinctive.
- Motion: 6/10. State changes have physical continuity but never delay input.
- Density: 5/10. The repeated action is prominent; support details stay compact.
- Canvas: ink `#0C0B0A`.
- Surfaces: `#161412`, elevated `#201D19`, selected `#29231E`.
- Text: paper `#F5F0E7`, secondary `#BDB4A8`, muted `#8C847A`.
- Accent: clay `#D17A5A`, strong clay `#B85D43`, on-accent ink `#1C110D`.
- Semantic states: warm gold for warnings and resolved confirmations, soft coral for errors, and neutral graphite for idle.
- Shape: 24dp for meaningful panels, 16dp for grouped rows, 12dp for controls. Use full pills only for compact status and segmented controls.
- Type: Android system sans with measured line lengths, medium display weight, and clear tabular metrics.
- Motion: short state transitions, waveform movement derived from amplitude, and reduced-motion fallbacks that preserve every state distinction.

## Surface contracts

| Surface | User job | Visual anchor |
| --- | --- | --- |
| Welcome | Understand the promise and begin | pencil-line mark, one sentence, one CTA |
| Access | Make the bubble and insertion path available | live capability checklist |
| Microphone | Grant recording access with context | plain-language explanation and explicit action |
| Test | Complete the first successful dictation | oversized instrument and one recovery-safe result |
| Dictate | Start, monitor, and recover a dictation | stateful bubble/instrument, not a dashboard |
| Insights | Understand actual usage | honest metrics and recent activity |
| History | Find and reuse prior dictation | search-led list and clear recovery states |
| Settings | Tune behavior and privacy | grouped preferences, not a wall of cards |

## Acceptance criteria for the redesign

1. No implementation token or visible UI accent uses the rejected cool or botanical direction.
2. Onboarding communicates purpose before permissions, keeps permission state live after returning from Settings, and has an explicit finish-later path.
3. Dictate and Insights are discoverable top-level destinations.
4. In-app and overlay bubbles share state names, geometry intent, colors, and accessible descriptions.
5. Insights metrics are derived from persisted history and have a correct empty state.
6. Existing dictation, insertion, clipboard fallback, service, and settings behavior remains intact.
7. Build, lint, unit tests, and changed-screen runtime evidence are reported independently.
