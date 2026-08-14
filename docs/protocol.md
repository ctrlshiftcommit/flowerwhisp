# Local Whisper protocol v1

The local host uses newline-delimited JSON (one object per line) over the
child process's stdin/stdout. Stderr is diagnostic-only and must not contain
credentials or transcript text. Every request has a `requestId`; responses and
errors echo it when one was supplied. The machine-readable contract lives in
[schemas/local-whisper-protocol.schema.json](../schemas/local-whisper-protocol.schema.json).

## Requests

| Type | Required fields | Purpose | Source status |
| --- | --- | --- | --- |
| `handshake` | `type`, `requestId` | Negotiate protocol and report model/device readiness. | Implemented by checked-in host and C# seam. |
| `transcribe` | `type`, `requestId`, `audioPath`, optional `language` | Ask the configured local runtime to transcribe an absolute user-owned audio file. | Implemented by host and persistent C# process seam; actual model requires a configured checkout. |
| `cancel` | `type`, `requestId`, `targetRequestId` | Request cancellation of an in-flight transcription. | Implemented by the Python host; the C# seam tears down a canceled read and restarts safely. |
| `shutdown` | `type`, `requestId` | Ask the host to exit cleanly. | Implemented by checked-in host and C# seam. |

## Responses and errors

A successful handshake reports `protocolVersion`, `hostVersion`, `model`,
`device`, `multilingual`, and `ready`; a configured checkout may take time to
load the model before `ready` is true. A successful transcription is typed
`transcription` and reports `text`, optional `language`, `duration`, and
`segments` (`start`, `end`, `text`). Errors use `{ "type": "error", "requestId": ..., "code": ..., "message": ... }`.
Codes are stable, lower-snake-case identifiers such as `invalid_json`,
`not_ready`, `missing_audio`, `model_unavailable`, `cancelled`, and
`unknown_type`; messages are user-facing diagnostics and must not disclose
secrets or machine paths.

The C# process seam writes a temporary PCM16/16 kHz mono WAV, sends its absolute
path, validates response IDs, parses transcription segments, and removes the
temporary file. The Python host loads an official Whisper checkout once per
process when configured. Without a checkout it returns `model_unavailable` for
transcription while still supporting parser/handshake diagnostics.

The configured D: drive `small`/CUDA handshake was attempted but timed out
after five minutes during model initialization; that live model path remains
unverified in this snapshot.

## Safety rules

- Treat each line as untrusted JSON and reject malformed input without crashing
  the host loop.
- Match response IDs to pending requests; never use a response for another
  request.
- Keep the sidecar offline from the network unless a future, explicit design
  change says otherwise.
- Bound audio paths to user-selected files and avoid placing absolute machine
  paths in exports or diagnostics.
