# FlowerWhisp product contract

## North star

FlowerWhisp turns a short spoken thought into text at the current Windows
target, with a visible privacy choice and an unobtrusive history. The product
should feel like a paper ledger with a fast keyboard edge: calm, inspectable,
and useful without an account.

## Surfaces

The navigation contract is: Dictations, Insights, Dictionary, Snippets,
Styles, Transforms, Scratchpad, and Settings. Settings groups are intended to
cover General, Pill, Shortcuts, Audio, Providers, Privacy, Appearance, Data,
Updates, and About.

## Capture modes

- Hold: press `Ctrl+Win`, speak, release to finish and insert.
- Toggle: press `Ctrl+Win+Space` to reclassify the active hold capture as toggle;
  release the chord and press `Ctrl+Win+Space` again to accept and insert. The
  pill also exposes explicit accept and discard controls while toggle is live.
- Scratchpad: capture into a private workspace without targeting another app.

The shortcut state machine and application orchestration are connected to the
native hook, recording pill, capture provider, persistence, and insertion seam.
Installed interactive runtime verification remains a separate release gate.

## Provider policy

Groq speech-to-text is restricted to `whisper-large-v3-turbo` by default and
`whisper-large-v3` as the only alternative. Optional text-only polish uses
`openai/gpt-oss-20b` by default, with `openai/gpt-oss-120b` as the only current
alternative. Models approaching shutdown or absent from the authenticated
organization response are hidden. The app must not silently substitute a
paid-plan or unapproved model.

Local official OpenAI Whisper remains an opt-in, user-configured sidecar. The
checked-in host discovers an official checkout, loads one configured model for
the process, and returns transcript text, language, duration, and segments.
Without a checkout it remains useful for parser/handshake diagnostics and
returns `model_unavailable` for transcription. A configured D: drive `small`
CUDA handshake was attempted but timed out after five minutes during model initialization, so the
live model runtime remains unverified in this snapshot.

## Retention and aggregates

Whole records support KeepForever, DeleteAfter24Hours, and NeverStore. SQLite
is the current user-local persistence implementation, with the JSON repositories
retained as compatibility seams. A
NeverStore choice skips raw/final history while allowing privacy-safe daily
aggregates (count, audio seconds, character count). Aggregates never contain
transcript text, prompts, API credentials, model caches, or machine paths.

## Explicit non-goals

No paywall, login, account, trial, teams, collaboration, referral program,
Notetaker, Batch, Flex, paid-plan Groq model, or server-side transcript store.
