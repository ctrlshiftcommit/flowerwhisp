# FlowerWhisp button and control audit

Date: 2026-08-22 overnight pass

## Outcome

The renderer was audited page by page and the latest source was rebuilt and packaged. Visible controls now either perform a real local action, call an implemented Electron IPC path, or have been removed when this build does not support the promised behavior.

The fresh runtime shows the generic `Welcome back` state and does not contain a user name. The original profile was read-only during this pass and remains at:

`C:\Users\tusha\AppData\Roaming\FlowerWhisp\state\flowerwhisp.json`

Verified original-profile totals: 10 records, 269 usage words, 11 dictations, and 1 usage day. The zeroed counters seen in the audit runtime are from an isolated clean profile, not a reset of the original data.

## Removed or made static

- Sidebar Invite your team and Help actions, plus the unused Account header action.
- Insights Download on mobile action and the fake Your usage button.
- Dictionary and Snippets Import actions; Snippets’ fake scope tabs and formatting toolbar.
- Scratchpad Add to Flow Bar and shortcut-link controls; the note row is now a status display.
- Shortcut rows that were only visual placeholders; the settings cleanup now exposes one editable global dictation shortcut and removes the fake Middle Click/fallback rows.
- Settings controls with no backend behavior: microphone Change, App Language, sound toggles, and other dead settings.
- Favorite transcript state/action. The transcript action menu still contains the requested Undo AI edit, Retry transcript, Extract audio, and Delete transcript actions.
- The unreachable Snippets promo dismiss state.

## Repaired or verified

- Sidebar collapse/expand and the Notifications popover.
- Dictionary add-word modal, search/scope tabs, team-scope persistence, promo close, and delete action.
- Snippet create/edit/delete flow and the promo Add new snippet action.
- Style selection, cleanup selection, cleanup prompt editors, and user-editable Polish/Prompt Engineer instructions.
- Transform guide, transform creation, built-in prompt editors, enable toggles, and custom-transform deletion. The guide copy was corrected to use a readable light-on-dark palette.
- Scratchpad new-note, save, refresh, search, and private-workspace behavior.
- Appearance Light/Dark/System controls and the real system settings toggles.
- Shortcut validation: a modifier plus exactly one non-modifier key is required. The safe first-run default is now `Ctrl+Shift+Space`; a live isolated test successfully registered `Ctrl+Shift+Tab`.
- The always-mounted pill: the idle marker is tiny and low-opacity, while the active pill shows timer and waveform controls. The earlier live test reached `0:10` with the waveform visible before the expected no-Groq-key error.

## Verification evidence

- `npm.cmd run typecheck` — passed.
- `npm.cmd test -- --run` — 3 test files and 20 tests passed.
- `git diff --check` — passed; only normal CRLF conversion warnings were reported by Git.
- `npm.cmd run build` — passed. Main bundle 70.0 kB, preload 3.2 kB, renderer JavaScript 343.54 kB, renderer CSS 76.23 kB.
- `npm.cmd run smoke` — passed after adding host-only `--disable-gpu --in-process-gpu --no-sandbox` switches to the smoke harness. Evidence reported renderer loaded, preload bridge present, context isolation enabled, Node integration disabled, and sandbox configured true.
- Directory package — passed with electron-builder 26.15.3 / Electron 37.10.3 at:

  `C:\Users\tusha\AppData\Local\Temp\FlowerWhisp-audit-20260822-v4\win-unpacked`

- The exact packaged `app.asar` was loaded through the installed Electron host. It rendered the current UI, exposed the preload bridge, showed the generic welcome page, and produced a final readable Transform guide screenshot. The packaged app’s `Flow is ready` idle pill was also captured.

## Limits of this host

- This machine has a managed Chromium/GPU failure. A normal host launch hit `GPU process isn't usable`, and the branded `FlowerWhisp.exe` was blocked by Windows Application Control. The package itself completed successfully; the runtime visual pass used the installed Electron host with software-rendering switches.
- No Groq API key is configured, and the managed host did not expose a microphone to the final isolated runtime. Therefore transcription, real insertion at an external cursor, and audio playback could not be completed end to end in this pass. The app surfaced the missing-provider/microphone errors instead of creating a fake transcript.
- The package was written outside the repository. No `.exe` or release directory was added to the Git working tree.

Temporary audit data was kept in isolated profiles under `C:\Users\tusha\AppData\Local\FlowerWhisp\overnight-audit-v2` and `C:\Users\tusha\AppData\Local\Temp\FlowerWhisp-runtime-v14`; it was not written to the original profile.
