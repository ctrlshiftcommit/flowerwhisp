# FlowerWhisp renderer and UI contract

This document maps the supplied Whisper Flow reference requirements to the
renderer components and state behavior that must be implemented later. The
component names below are contract names, not a claim that those components or
their runtime behavior already exist.

## Scope and boundaries

FlowerWhisp has two renderer surfaces:

1. The main React renderer manages settings, history, reusable productivity
   features, and transcript detail.
2. The floating pill is a separate, small always-on-top renderer window used
   during dictation.

The preload bridge is the only renderer path to privileged behavior. The
renderer may request capture and receive sanitized state, but it must not own
global shortcut registration, provider credentials, secure storage, native
text insertion, filesystem access, or arbitrary IPC.

| Concern | Renderer contract | Main / preload contract |
| --- | --- | --- |
| Microphone capture | Use browser `MediaRecorder`, expose level data to `WaveformBars`, release tracks on stop/cancel | Validate the capture request and forward audio to the selected provider pipeline |
| Global shortcuts | Render configured shortcuts and conflict results; never pretend a shortcut is active before the bridge confirms it | Register and release global shortcuts; report whether hold key-up support is available |
| Providers and models | Show sanitized provider/model descriptors and connection state | Own Groq/local provider calls, model availability, credentials, and sanitized errors |
| Persistence | Render records and settings returned by the bridge | Own local persistence, migrations, retention, and recovery of raw/cleaned/final text |
| Text insertion | Show insertion progress and fallback actions | Preserve focus and clipboard where possible; insert into the active application |
| Overlay window | Render the pill and respond to state events without stealing target focus | Create, position, show, hide, and destroy the always-on-top overlay window |

Secrets never cross into renderer state. A saved API key is represented only by
metadata such as “configured” or “not configured”.

## Shell and navigation contract

`AppShell` owns the persistent desktop layout. `Sidebar` owns navigation and
does not contain recording controls. `PageHeader` provides one page title, a
short operational description when needed, and the page-level action.

| Reference requirement | Renderer components | Contract |
| --- | --- | --- |
| Quiet left navigation rail | `AppShell`, `Sidebar`, `SidebarNavItem` | Keep navigation compact and left anchored. Use line icons plus text labels: Dictation, Notetaker, Insights, Dictionary, Snippets, Style, Transforms, Scratchpad, Settings, and Help. The selected row uses a subtle surface, not a loud accent block. |
| Editorial page hierarchy | `PageHeader`, `SectionHeading` | Use the serif page title, sans-serif controls, and asymmetrical content rhythm from `DESIGN.md`. Do not introduce a generic dashboard shell or a full-bleed hero. |
| Main management surface | `DictationPage`, `HistoryList`, `HistoryCard` | Landing content is a usable dictation/configuration surface with recent activity. Any setup prompt is conditional on missing configuration and is operational, not promotional. |
| Background utility behavior | `TrayStatus`, `Toast`, bridge event handlers | Closing the main window must not be represented as quitting the dictation engine. Renderer controls may request open, minimize, or quit through named bridge methods only. |

The main content region owns overflow. Sidebar labels, controls, and long
transcripts must remain reachable at the minimum window and at increased
display scaling.

## Reference-to-component map

The following names form the implementation seam. Shared components should be
used across pages instead of duplicating state and styling.

| Reference requirement | Concrete renderer components | Required behavior |
| --- | --- | --- |
| Tiny recording pill | `FloatingPill`, `PillAction`, `WaveformBars` | Separate overlay window, always on top, compact 52–56px visual height, dark surface, subtle shadow, no focus steal, no target resize. |
| Hold-to-dictate | `FloatingPill`, `WaveformBars`, `PillAction` | While the configured hold shortcut is down, show cancel plus live bars. Do not show a confirm action in hold mode. Key-up support is a main-process capability and must be reported honestly. |
| Toggle-to-dictate | `FloatingPill`, `WaveformBars`, `PillAction` | While toggled, show cancel, live bars, and confirm. The configured toggle shortcut or confirm action finalizes; cancel discards. This is a distinct mode, not a relabeled hold capture. |
| Audio activity | `WaveformBars`, `MicLevelStatus` | Bars are derived from the current microphone level, smoothed, and quiet. No random animation or traditional waveform editor. |
| Dictation configuration | `DictationPage`, `ProviderSelector`, `ModelSelector`, `LanguageSelector`, `CleanupLevelSelector`, `ShortcutSummary`, `MicrophoneSelector` | Keep provider, model, language, cleanup, shortcut, and microphone visible but secondary to the capture workflow. Transcription and LLM configuration remain separate. |
| Searchable transcript history | `HistoryList`, `HistoryCard`, `SearchInput`, `TranscriptDetail` | Each record can expose timestamp, transcript versions, application, duration, provider/model metadata, playback when retained, copy, favorite/flag, and overflow actions. Preserve raw text. |
| Raw versus processed truth | `TranscriptDetail`, `TranscriptVersionTabs`, `UndoCleanupAction` | Label `Raw`, `Cleaned`, and `Inserted` explicitly. “Undo AI edit” or equivalent returns to the recoverable source; it never fabricates a replacement. |
| Style presets and app rules | `StylePage`, `StyleTabs`, `StyleCard`, `AppStyleRuleList` | Cards show name, short description, example only when real/configured, and selected state. App-specific rules are edited separately from the global default. |
| Reusable transforms | `TransformsPage`, `TransformList`, `TransformCard`, `TransformEditor`, `ShortcutRecorder` | Support default and user-created transforms with name, description, instructions, shortcut, save, duplicate, edit, delete, and reset actions. No transform may silently change the dictation cleanup stage. |
| Deterministic dictionary | `DictionaryPage`, `DictionarySearch`, `DictionaryEntryList`, `DictionaryEntryEditor` | Show spoken form and authoritative replacement. Add, edit, delete, search, and enable/disable are explicit. Replacements happen before optional cleanup and are not delegated solely to an LLM. |
| Snippets | `SnippetsPage`, `SnippetList`, `SnippetEditor`, `SearchInput` | Show trigger and expansion; support create, edit, delete, search, enable/disable, and optional shortcut. Do not show fabricated examples as saved snippets. |
| Lightweight scratchpad | `ScratchpadPage`, `ScratchpadEditor`, `ScratchpadToolbar` | Support typing, dictation into the private workspace, search, save, copy, and delete without turning the page into a heavy document editor. |
| Structured notes | `NotetakerPage`, `NoteList`, `NoteDetail` | Show title, timestamp, transcript, duration, word count, and application when available; support search, copy, rename, delete, favorite, and playback only when audio was retained. |
| Measured insights | `InsightsPage`, `MetricStrip`, `UsageBreakdown`, `ActivityHeatmap` | Render WPM, total words, fixes, app/category usage, daily activity, streak, and heatmap only from persisted measurements. No demo values, decorative stats, or charts without data. |
| Settings rows | `SettingsPage`, `SettingsSection`, `SettingsRow`, `Toggle`, `Select`, `ShortcutRecorder`, `SecretField`, `FolderPicker`, `MicrophoneTest` | Group system, sound, general, AI, and dictation settings into readable rows. Advanced provider details stay here rather than in the capture pill. |
| Short onboarding | `OnboardingDialog`, `OnboardingStep` | Guide microphone permission, provider, provider configuration, shortcut, cleanup/style, and a test dictation. It can be skipped only when the required configuration is already known. |
| Shared feedback | `LoadingState`, `EmptyState`, `InlineError`, `Toast`, `Modal`, `ConfirmDialog` | Every async or empty surface has a deliberate message, an appropriate action, and keyboard/screen-reader semantics. |

## Dictation pipeline contract

The renderer presents one coherent flow while preserving each stage as a
separate status and data boundary:

```text
configured shortcut
  -> starting
  -> MediaRecorder capture
  -> transcribing through selected provider
  -> raw transcript
  -> deterministic dictionary correction
  -> optional style / cleanup processing
  -> final text
  -> insertion into the active application
  -> local record and analytics update
```

The main process owns provider calls, cleanup, persistence, and insertion. The
renderer receives structured progress and sanitized errors. The raw transcript
is stored before cleanup and remains available even if a later stage fails.

## Explicit dictation state machine

`FloatingPill` and the main renderer consume one explicit state, not unrelated
booleans such as `isRecording` and `isProcessing`.

| State | Pill presentation | Main renderer behavior | Next actions |
| --- | --- | --- | --- |
| `idle` | Hidden | Show configuration/history normally | Start through a configured shortcut |
| `starting` | Appears quickly with a quiet activation cue | Disable duplicate start; show the selected mode | Cancel if capture cannot start; otherwise `recording` |
| `recording` + hold | Cancel plus live audio bars; no confirm | Show mode and elapsed time if space allows | Shortcut release stops; cancel discards |
| `recording` + toggle | Cancel plus live audio bars plus confirm | Keep target application untouched | Toggle shortcut or confirm stops; cancel discards |
| `stopping` | Stable pill; controls that would duplicate stop are disabled | Do not start a second capture | Forward audio once to transcription |
| `transcribing` | Bars become a compact processing cue | Show provider and model as sanitized labels | On success expose raw text; on failure keep recovery actions |
| `processing` | Compact stage label such as cleanup or transform | Keep raw text recoverable and do not imply insertion yet | Continue, retry if safe, or keep the raw/corrected result |
| `inserting` | Compact insertion cue | Keep the window responsive; do not change target focus | Success or insertion error with copy fallback |
| `success` | Brief confirmation, then hide | Add the record and measured analytics | Return to `idle` |
| `cancelled` | Hide without celebration | Do not insert or create a completed history record | Return to `idle` |
| `error` | Remains visible with short stage/error label | Show adjacent recovery guidance and preserve available text | Retry, open settings, copy raw, or dismiss |

The renderer must not claim that a hold shortcut is system-wide until the main
process reports a key-up-capable implementation. Configurable shortcut labels
are not proof that registration succeeded.

## Empty states

`EmptyState` is a real component, not a blank container. It uses one concise
explanation and one useful action where an action exists.

| Surface | Empty behavior |
| --- | --- |
| Dictation/history | “No dictations yet.” Explain that the configured shortcut creates the first record; offer `Open settings` when setup is incomplete. |
| Search results | “No matches.” Preserve the query and offer `Clear search`; do not replace the list with unrelated records. |
| Dictionary | “Your dictionary is empty.” Explain that entries correct recognized phrases; offer `Add word`. |
| Snippets | “No snippets yet.” Offer `Add snippet`; do not show unsaved examples as data. |
| Styles | “No custom styles yet.” Offer `Create style`; default styles may be shown only when actually configured. |
| Transforms | “No transforms yet.” Offer `Create transform`. |
| Scratchpad | “Start writing or dictate something.” Focus the editor when the user chooses to begin. |
| Notetaker | “No notes yet.” Offer `Start a note` or search when the feature is configured. |
| Insights | “Not enough activity yet.” Explain which real measurements will appear; never render made-up numbers or an empty chart frame. |
| Models/providers | “No available model” or “Provider not configured.” Offer `Open settings`; do not silently substitute another provider/model. |

## Error and recovery contract

Errors are rendered next to the affected action and are also available to
assistive technology. Messages are operational and sanitized; they never
include API keys, authorization headers, raw request bodies, or private paths.

| Failure | Renderer behavior | Recovery |
| --- | --- | --- |
| Microphone permission denied or device missing | `InlineError` beside `MicrophoneSelector`; stop capture safely and release tracks | `Open microphone settings`, choose another input, or retry permission |
| Cloud key missing | Mark the cloud provider as not configured; do not start a cloud request | `Open AI settings` |
| Cloud/network/provider failure | Keep the raw transcript if available and identify the failed stage/provider | `Retry` when marked retryable, `Keep raw text`, or `Open settings` |
| Local transcriber/model unavailable | Show the configured local path only if it is safe to display; do not imply local mode is ready | `Choose folder`, `Select model`, or switch provider intentionally |
| Cleanup/style failure | Never replace raw text with a guessed polished result | `Keep raw/corrected text`, retry cleanup, or disable cleanup |
| Text insertion unsupported | Keep final text visible and announce that insertion did not occur | `Copy final text`; preserve the original target focus where possible |
| Clipboard restore or target focus issue | Report insertion as incomplete rather than success | Copy final text and allow the user to retry from the history record |
| Bridge/bootstrap failure | Show a blocking but readable app error with no blank shell | Retry initialization or quit safely; never render fake provider/status data |
| Save/history failure | Show the transcript result and distinguish “inserted” from “not saved” | Retry save or copy/export only through an explicit user action |

An error does not erase a successful earlier stage. For example, a failed
cleanup still leaves the raw transcript, and a failed insertion still leaves
the final text available for copying.

## Settings and secure-control behavior

- `ShortcutRecorder` records keyboard modifiers and mouse buttons through a
  named bridge method, displays conflicts, rejects invalid combinations, and
  supports reset. It does not install the shortcut from the renderer.
- If the brief's example defaults are shown, use Middle Click for hold and
  Ctrl+Win+Space for toggle only as configurable examples. The renderer must
  display the confirmed configured values and must not hardcode either chord.
- `ProviderSelector` and `ModelSelector` are independent for transcription and
  LLM cleanup. A missing or removed model is an explicit unavailable state,
  not a silent fallback.
- `SecretField` shows only configured/not configured state after save. Testing
  a connection returns sanitized status and actionable error text.
- `FolderPicker` displays a user-selected local folder and validation status;
  the renderer never scans arbitrary paths on its own.
- `MicrophoneTest` owns visible permission, selected device, connection, and
  live-level feedback. It stops the test stream when leaving the setting.
- `Toggle` and `Select` show pending, saved, and failed states. A failed save
  does not visually commit the new value.

## Accessibility and responsive handoff

- Every route, control, icon button, dialog, and overlay action is keyboard
  reachable with a visible focus indicator.
- Use labels, descriptions, `aria-invalid`, `aria-describedby`, `aria-busy`, and
  live status announcements where the state changes would otherwise be missed.
- Do not communicate recording, provider, success, or error state by color
  alone. Use text, icon, position, or an announced status as well.
- Keep the content region scrollable at short heights; set `min-width: 0` on
  flexible children and allow long/non-ASCII transcript text to wrap.
- At narrow desktop widths, stack the secondary column and reduce/collapse the
  rail without turning the app into a mobile dashboard. The primary dictation,
  history, or settings task remains first and reachable.
- The overlay has no draggable region over its actions. If a custom frame is
  used, only inert space is draggable and every action is explicitly no-drag.
- Respect `prefers-reduced-motion`; the state machine and status text must work
  with transitions removed.

## Non-negotiable exclusions

Do not implement this contract as:

- a large centered recorder or a full-page microphone demo;
- a generic dashboard with equal decorative stat cards;
- a marketing landing page, referral prompt, trial claim, or fake activity;
- emoji, neon, excessive gradients, glassmorphism, or giant animations;
- a renderer that receives credentials or calls privileged Electron APIs
  directly;
- a flow that discards the original transcript or silently masks an error;
- a visual prototype described as installed, packaged, or runtime-verified.

The acceptance target is the small, recoverable desktop loop: configured
shortcut, tiny pill, real audio state, provider processing, preserved text
versions, safe insertion, and a useful recovery path when any stage fails.
