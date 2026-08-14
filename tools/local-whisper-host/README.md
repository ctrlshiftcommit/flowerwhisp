# FlowerWhisp local Whisper host

The host speaks newline-delimited JSON over stdin/stdout. It never opens a
network port and does not route local audio to Groq.

1. Send `{"type":"handshake","requestId":"..."}`.
2. Send `{"type":"transcribe","requestId":"...","audioPath":"C:\\path\\audio.wav"}`.
3. Send `{"type":"shutdown","requestId":"..."}`.

When `FLOWERWHISP_WHISPER_CHECKOUT` (or `WHISPER_CHECKOUT`) points to an
official OpenAI Whisper checkout, the host imports Whisper, loads one model for
the process, and can transcribe an absolute user-owned WAV. Set
`FLOWERWHISP_MODEL`, `FLOWERWHISP_MODEL_DIR`, and optionally
`FLOWERWHISP_DEVICE=cuda` to control model/device/cache placement. Without a
checkout, the host remains in parser/handshake diagnostic mode and returns a
structured `model_unavailable` error for transcription. Runtime and model files
are intentionally not committed.
