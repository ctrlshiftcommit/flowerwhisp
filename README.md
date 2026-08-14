# FlowerWhisp

FlowerWhisp is a local-first Windows dictation workspace with a quiet "Signal
Ledger" for Dictations, Insights, Dictionary, Snippets, Styles, Transforms,
Scratchpad, and Settings. It is source-available under the Apache License 2.0.

## Status: read this first

This repository is an early, buildable product implementation. The WinUI shell,
domain contracts, provider policy, WASAPI capture, SQLite persistence,
shortcut state machine/native hook, insertion fallback, and local Whisper
NDJSON host are present in source. The sidecar can import an official OpenAI
Whisper checkout, load one model for the process, transcribe a user-owned WAV,
return language/segments, and handle cancellation/shutdown. Without a
configured checkout it remains a parser/handshake diagnostic host and reports
`model_unavailable` for transcription.

The recording lifecycle is wired from the native shortcut hook and pill through
WASAPI capture, transcription, optional polishing, insertion, SQLite history,
and local usage aggregates. The configured official D: drive `small`/CUDA
handshake was attempted but timed out during model initialization after five
minutes; local model runtime is therefore not live-verified in this snapshot.
Launching the packaged WinUI app is also blocked on the current machine by
Windows Developer Mode requirements.

Current verification evidence: the .NET test suite passes 22/22 tests and the
standalone local-host handshake/error/shutdown protocol test passes. JSON schema
files parse successfully. These checks do not substitute for a completed CUDA
model startup or packaged-app launch.

No public release, signed package, hosted service, account system, or production
runtime verification is claimed here. See [CHANGELOG.md](CHANGELOG.md) for the
current release stage and [docs/release-checklist.md](docs/release-checklist.md)
for the gates required before publishing.

## Product boundaries

- Windows 10 19041+ and Windows 11, x64; WinUI 3 and .NET 10.
- Native `Ctrl+Win` hold-to-dictate and `Ctrl+Win+Space` toggle mode are the
  intended shortcuts.
- Groq transcription is restricted to the free-plan models
  `whisper-large-v3-turbo` (default) and `whisper-large-v3`.
- Optional text-only Groq polishing is restricted to the documented allowlist;
  no audio is sent to the polishing endpoint.
- Groq says inference content is not retained by default, but reliability or
  abuse investigations can temporarily retain inputs/outputs for up to 30 days;
  Zero Data Retention is available in Groq organization Data Controls.
- Local official OpenAI Whisper is supported through the user-configured sidecar
  boundary. Runtime, model, cache, and machine paths are user-owned.
- Whole-record retention is explicit: forever, 24 hours, or never. Privacy-safe
  usage aggregates may remain when whole-record retention is disabled.
- There is no paywall, account/auth flow, trial, teams/collaboration feature,
  referral feature, Notetaker, Batch, Flex, or paid-plan Groq model.

## Development

The repository uses the project-local .NET SDK under `.tools/dotnet` when it is
available. Keep build and NuGet caches on D: (or another user-selected volume):

```powershell
$env:DOTNET_CLI_HOME = "$pwd\.tools\dotnet-home"
$env:NUGET_PACKAGES = "$pwd\.tools\nuget-packages"
$env:NUGET_HTTP_CACHE_PATH = "$pwd\.tools\nuget-http"
$env:TEMP = "$pwd\.tools\temp"; $env:TMP = $env:TEMP
& .tools\dotnet\dotnet.exe restore FlowerWhisp.slnx
& .tools\dotnet\dotnet.exe build FlowerWhisp.slnx --configuration Debug --arch x64 --no-restore
& .tools\dotnet\dotnet.exe test tests\FlowerWhisp.Tests\FlowerWhisp.Tests.csproj --no-restore
```

If the project-local SDK is not present, install the .NET 10 SDK and use
`dotnet` instead. Do not commit SDKs, model files, credentials, or generated
packages. See [docs/developer-setup.md](docs/developer-setup.md) for a fuller
setup and [docs/troubleshooting.md](docs/troubleshooting.md) for known limits.

## Architecture and privacy

The app is split into Core contracts, Application orchestration, Infrastructure
providers/persistence, and Windows platform services. The local host speaks
newline-delimited JSON (NDJSON) protocol version 1 over private stdin/stdout;
it never opens a network port. Read [docs/architecture.md](docs/architecture.md),
[docs/protocol.md](docs/protocol.md), and
[docs/privacy-data-flow.md](docs/privacy-data-flow.md) before changing a
boundary.

Credentials are stored through the Windows secret seam and are excluded from
history, exports, logs, and prompts. Review [PRIVACY.md](PRIVACY.md) and the
export contract in [schemas/export.schema.json](schemas/export.schema.json).

## Contributing and security

Please read [CONTRIBUTING.md](CONTRIBUTING.md), [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md),
and [SECURITY.md](SECURITY.md). CI restores, builds, tests, performs dependency
review and CodeQL analysis on Windows runners. A release workflow is guarded so
it fails closed when signing secrets are absent; pull-request artifacts, when
added, must be clearly labeled unsigned.

## License

FlowerWhisp is licensed under the Apache License 2.0. See [LICENSE](LICENSE) and
[NOTICE](NOTICE). Third-party notices are maintained in
[docs/third-party-notices.md](docs/third-party-notices.md).
