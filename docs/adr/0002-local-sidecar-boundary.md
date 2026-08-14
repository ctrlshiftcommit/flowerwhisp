# ADR 0002: local Whisper as a private sidecar

- Status: accepted
- Date: 2026-08-13

## Context

Users may want local official OpenAI Whisper without sending audio to Groq.
Bundling model/runtime files would add size, licensing, and update risk.

## Decision

Launch a user-configured local process and communicate with it using protocol
version 1 NDJSON over stdin/stdout. The host never opens a network port. The
boundary includes handshake, transcribe, cancel, shutdown, request IDs, and
structured errors. The host can import the official checkout, load one model
for the process, and return language, duration, and segments. Runtime/model/cache
paths remain user-owned and excluded from exports.

## Consequences

The app can keep local audio off the network, but must handle process lifetime,
version mismatch, cancellation, and missing runtimes explicitly. Without a
configured checkout, the checked-in Python host falls back to parser/handshake
diagnostics and reports `model_unavailable` for transcription. Configured CUDA
model startup remains a separately verified runtime task.
