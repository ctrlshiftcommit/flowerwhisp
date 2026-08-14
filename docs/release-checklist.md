# Release checklist

This checklist is a gate, not a claim that the current repository has passed it.

## Source and quality

- [ ] Confirm the version and changelog entry.
- [ ] `git diff --check` passes.
- [ ] Restore, Release build, and tests pass on `windows-latest` and a local
      Windows 10 19041+ x64 machine.
- [ ] Review dependency licenses and generated SBOM.
- [ ] Run CodeQL, dependency review, and secret scanning with no unresolved
      findings.

## Runtime and privacy

- [ ] Exercise hold and toggle shortcuts with a real microphone.
- [ ] Verify Groq STT/polish allowlists and text-only polish payload.
- [ ] Verify local sidecar handshake, transcription, cancellation, shutdown,
      and structured errors with a user-owned official Whisper runtime.
- [ ] Verify target-change/elevation insertion outcomes.
- [ ] Verify forever, 24-hour, and never-store cleanup; inspect export for the
      exclusions in schemas/export.schema.json.
- [ ] Verify light, dark, high-contrast, keyboard, and reduced-motion behavior.

## Artifact and publication

- [ ] Package only the intended x64 Windows artifact; do not include credentials,
      logs, runtime/model files, caches, or machine paths.
- [ ] Confirm pull-request workflow produced a clearly labeled unsigned MSIX;
      the signed workflow must sign only the MSIX/AppX package, never arbitrary
      publish-directory executables.
- [ ] Configure signing secrets in GitHub's secret store and confirm the guarded
      workflow fails closed when they are absent.
- [ ] Verify signature and checksums independently of the build runner.
- [ ] Attach a clearly labeled changelog and known-limitations note.
- [ ] Publish only after the repository owner confirms the remote/release state;
      this repository does not claim public visibility.
