# Changelog

All notable changes will be documented here. The project has not published a
stable release.

## Unreleased

### Added

- Initial WinUI/.NET 10 FlowerWhisp product shell and Signal Ledger surfaces.
- Core shortcut, provider-policy, retention, WASAPI capture, SQLite persistence,
  native hook, insertion fallback, and orchestration seams.
- Version 1 local Whisper NDJSON host with official-checkout model loading,
  language/segment responses, cancellation, restart handling, and shutdown.
- Export/privacy contracts plus open-source governance, CI/security, issue
  templates, and guarded release scaffolding.

### Known limitations

- Without a configured external Whisper checkout/model, the sidecar reports
  `model_unavailable` by design.
- The configured official D: drive `small`/CUDA handshake timed out after five
  minutes during model initialization; local model runtime is not live-verified
  in this snapshot.
- The recording-to-insertion source path is wired, but has not been physically
  accepted across the full Windows/application/DPI matrix.
- Packaged app launch was blocked by Windows Developer Mode on the verification
  machine. No signed package or public release is claimed.
