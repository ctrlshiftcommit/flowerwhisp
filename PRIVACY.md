# Privacy

FlowerWhisp is designed local-first. Audio and text stay on the device unless
the user chooses a remote Groq transcription or text-polishing provider.

## Data choices

- **Keep forever** stores a raw transcript, final text, backend, polish mode,
  retention choice, duration, and optional detected language in user-local
  persistence.
- **Delete after 24 hours** permits that same record shape temporarily; the
  retention service removes records past the cutoff.
- **Never store** skips whole-record history. Daily usage aggregates may still
  contain dictation count, audio seconds, and character count.
- Credentials are held by the Windows secret seam, not history, exports, logs,
  or prompts.

## Network boundaries

Groq receives audio only for the transcription request selected by the user.
The optional polish request receives text only. The local Whisper sidecar uses
private stdin/stdout NDJSON and does not open a network port. Runtime/model/cache
files are outside the repository and are user-owned.

Groq documents that inference inputs and outputs are not retained by default,
but may be temporarily logged for platform reliability or suspected-abuse
investigations for up to 30 days. Organization administrators can enable Zero
Data Retention in Groq Data Controls. Usage metadata is collected separately
and does not contain customer inputs or outputs. Review Groq's current data
controls before sending sensitive audio or text:
https://console.groq.com/docs/your-data

## Export boundary

The versioned export contract excludes credentials, logs, runtime and model
files, caches, machine paths, and device identifiers. See
[schemas/export.schema.json](schemas/export.schema.json). Export is a contract
for the application layer; the current scaffold does not claim a finished UI
export command.

## User controls and deletion

Users should be able to change retention, delete individual records, apply a
retention cleanup, and remove stored credentials. Deleting a record does not
retroactively erase a remote provider's processing; provider terms and network
transport remain applicable.

This document describes the source-level contract, not legal advice or a
guarantee of a completed production data-control surface.
