# Architecture

FlowerWhisp is organized as a small dependency flow:

```text
WinUI shell / ViewModels
        |
Application orchestration (capture -> transcribe -> optional polish -> insert)
        |
Core contracts and policy
        |
Infrastructure providers/persistence ---- Platform.Windows services
        |
local Whisper sidecar (private NDJSON stdin/stdout)
```

## Layers

- **Core** contains records, enums, provider allowlists, and service interfaces.
- **Application** coordinates a dictation and records only the configured
  retention shape plus privacy-safe aggregates.
- **Infrastructure** implements Groq HTTP providers, SQLite persistence (with
  JSON compatibility repositories), the retention service, and the local-host
  process boundary.
- **Platform.Windows** owns WASAPI capture, the native low-level keyboard hook,
  foreground-target/elevation checks, text insertion/clipboard fallback, and
  the current-user secret-store seam.
- **UI** is WinUI 3. The current shell is a navigation/design scaffold and is
  not evidence of complete runtime wiring.

## Boundary rules

1. Core must not depend on WinUI, HTTP, or a concrete file path.
2. Credentials enter providers through a secret boundary and never through
   records, exports, logs, or prompts.
3. Audio is sent to Groq only when the selected backend is Groq. Polish accepts
   text, not audio.
4. The local sidecar is a child process over stdin/stdout; it has no listening
   socket and must be killed on shutdown.
5. Export is a versioned, allowlisted shape rather than a dump of local files.

## Architecture decisions

See [ADR 0001](adr/0001-winui-dotnet.md) for WinUI/C# and
[ADR 0002](adr/0002-local-sidecar-boundary.md) for the sidecar boundary.
