# Troubleshooting

## Restore or build cannot find the SDK

Install .NET 10 or set the project-local `.tools/dotnet` path described in
developer-setup. Keep `DOTNET_CLI_HOME`, NuGet, and temporary directories on a
drive with space.

## Local host says `model_unavailable`

The host enters parser/handshake diagnostic mode when no official Whisper
checkout is configured, and returns this structured capability error for
transcription. Set `FLOWERWHISP_WHISPER_CHECKOUT` and a user-owned model
directory, then verify the handshake before transcribing. Do not commit the
runtime or model. A configured D: drive `small`/CUDA handshake timed out after
five minutes during an extended verification attempt, so that live model path
remains unverified. The C# boundary has finite startup and read deadlines (120
seconds by default) and continuously drains sidecar stderr;
on timeout, diagnostics expose only a bounded category/length summary and never
retain transcript or audio text.

## Groq model rejected

Only the production-model allowlists in `ProviderPolicy` are accepted. The
defaults are `whisper-large-v3-turbo` for STT and `openai/gpt-oss-20b` for
polish; current production polish models are GPT-OSS 20B and 120B. A provider
listing that contains no approved model must fail rather than silently
substitute another model. STT accepts `auto` or an ISO-639 language code and
sends an explicit `language` field only when one is selected. HTTP 429 failures
are surfaced as `GroqRateLimitException` with the safe `retry-after` and reset
window metadata when Groq provides it.

## Text was not inserted

The foreground target may have changed, the target may be elevated, or Windows
may reject synthetic input. The native insertion service first attempts Unicode
SendInput and then uses a clipboard/manual-paste fallback when UIPI blocks
direct input. The result is explicit; do not retry blindly into a different
target.

## Packaged app will not launch

The current verification machine requires Windows Developer Mode for the
packaged WinUI app. Enable it through Windows settings or use an approved
development certificate/install path before treating app launch as verified.

## Release workflow stops before signing

The release workflow intentionally fails closed when `SIGNING_CERT_BASE64` or
`SIGNING_CERT_PASSWORD` is absent. Configure those repository/environment
secrets through GitHub's secret store, never in YAML or issue comments.
