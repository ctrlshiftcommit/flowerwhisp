#!/usr/bin/env python3
"""FlowerWhisp local Whisper NDJSON sidecar.

The sidecar is deliberately private (stdin/stdout only).  When configured with
an official OpenAI Whisper checkout it imports that checkout, loads one model for
the life of the process, and returns the normal Whisper language/segment shape.
Without a configured checkout it remains a parser/handshake host so installation
and protocol diagnostics do not require downloading a model.
"""
from __future__ import annotations

import importlib
import json
import os
import queue
import sys
import threading
import uuid
import wave
from pathlib import Path

PROTOCOL_VERSION = 1
HOST_VERSION = "1.1.0"

_write_lock = threading.Lock()
_work: queue.Queue[dict] = queue.Queue()
_active: dict[str, threading.Event] = {}
_active_lock = threading.Lock()
_stop = threading.Event()
_shutdown_requested = threading.Event()
_model_lock = threading.Lock()
_model = None
_model_error: str | None = None
_model_device = os.environ.get("FLOWERWHISP_DEVICE", "cpu")
_model_name = os.environ.get("FLOWERWHISP_MODEL", "small")
_handshake_seen = False


def send(payload: dict) -> None:
    # Never let a model/library print on stdout; only this function owns it.
    with _write_lock:
        sys.stdout.write(json.dumps(payload, separators=(",", ":")) + "\n")
        sys.stdout.flush()


def error(request_id: str | None, code: str, message: str) -> None:
    send({"type": "error", "requestId": request_id, "code": code, "message": message})


def _discover_checkout() -> Path | None:
    configured = os.environ.get("FLOWERWHISP_WHISPER_CHECKOUT") or os.environ.get("WHISPER_CHECKOUT")
    candidates: list[Path] = []
    if configured:
        candidates.append(Path(configured))
    candidates.extend(
        [
            Path.cwd(),
            Path(__file__).resolve().parents[2],
            Path(__file__).resolve().parent,
        ]
    )
    for candidate in candidates:
        try:
            candidate = candidate.expanduser().resolve()
        except OSError:
            continue
        if (candidate / "whisper" / "__init__.py").is_file():
            return candidate
    return None


def _select_device() -> str:
    requested = os.environ.get("FLOWERWHISP_DEVICE", "cpu").strip().lower()
    if requested and requested != "auto":
        return requested
    try:
        import torch

        return "cuda" if torch.cuda.is_available() else "cpu"
    except Exception:
        return "cpu"


def _load_model_once() -> tuple[object | None, str | None, str]:
    """Import/load once; callers may invoke this from many protocol requests."""
    global _model, _model_error, _model_device
    if _model is not None or _model_error is not None:
        return _model, _model_error, _model_device
    with _model_lock:
        if _model is not None or _model_error is not None:
            return _model, _model_error, _model_device
        checkout = _discover_checkout()
        require = os.environ.get("FLOWERWHISP_REQUIRE_MODEL", "").strip().lower() in {"1", "true", "yes"}
        # No configured checkout is a valid parser-only installation state.  It
        # keeps the host testable while making a configured state fail visibly.
        if checkout is None and not require:
            return None, None, _model_device
        try:
            if checkout is not None:
                sys.path.insert(0, str(checkout))
            whisper = importlib.import_module("whisper")
            _model_device = _select_device()
            model_dir = os.environ.get("FLOWERWHISP_MODEL_DIR")
            if not model_dir and checkout is not None:
                model_dir = str(checkout / ".models")
            if model_dir:
                Path(model_dir).expanduser().mkdir(parents=True, exist_ok=True)
            kwargs = {"device": _model_device}
            if model_dir:
                kwargs["download_root"] = str(Path(model_dir).expanduser().resolve())
            _model = whisper.load_model(_model_name, **kwargs)
            return _model, None, _model_device
        except Exception as exc:  # report through handshake, never crash silently
            _model_error = f"{type(exc).__name__}: {exc}"
            return None, _model_error, _model_device


def _audio_duration(path: Path) -> float | None:
    try:
        with wave.open(str(path), "rb") as source:
            rate = source.getframerate()
            return source.getnframes() / rate if rate else None
    except (OSError, EOFError, wave.Error):
        return None


def _run_transcription(request: dict, cancel: threading.Event) -> None:
    request_id = str(request.get("requestId"))
    raw_path = request.get("audioPath")
    if not isinstance(raw_path, str) or not raw_path:
        error(request_id, "missing_audio", "audioPath is required.")
        return
    path = Path(raw_path)
    if not path.is_absolute():
        error(request_id, "audio_not_absolute", "audioPath must be absolute.")
        return
    try:
        path = path.resolve(strict=True)
    except OSError as exc:
        error(request_id, "audio_not_found", str(exc))
        return
    if cancel.is_set():
        error(request_id, "cancelled", "Transcription was cancelled.")
        return
    model, load_error, device = _load_model_once()
    if model is None:
        error(request_id, "model_unavailable", load_error or "Configure a Whisper checkout and model before transcribing.")
        return
    language = request.get("language")
    if not isinstance(language, str) or language.lower() in {"", "auto"}:
        language = None
    kwargs = {"task": "transcribe", "verbose": False, "fp16": device != "cpu"}
    if language:
        kwargs["language"] = language
    try:
        try:
            result = model.transcribe(str(path), **kwargs)
        except TypeError:
            # Small fake models used by protocol tests may expose only audio and
            # language.  Official Whisper accepts the full kwargs above.
            result = model.transcribe(str(path), language=language) if language else model.transcribe(str(path))
    except Exception as exc:
        error(request_id, "transcription_failed", f"{type(exc).__name__}: {exc}")
        return
    if cancel.is_set() or _shutdown_requested.is_set():
        error(request_id, "cancelled", "Transcription was cancelled.")
        return
    segments = []
    for segment in result.get("segments", []) if isinstance(result, dict) else []:
        try:
            segments.append(
                {
                    "start": float(segment.get("start", 0.0)),
                    "end": float(segment.get("end", 0.0)),
                    "text": str(segment.get("text", "")),
                }
            )
        except (TypeError, ValueError):
            continue
    duration = result.get("duration") if isinstance(result, dict) else None
    try:
        duration = float(duration) if duration is not None else _audio_duration(path)
    except (TypeError, ValueError):
        duration = _audio_duration(path)
    send(
        {
            "type": "transcription",
            "requestId": request_id,
            "text": str(result.get("text", "")) if isinstance(result, dict) else str(result),
            "language": result.get("language") if isinstance(result, dict) else None,
            "duration": duration,
            "segments": segments,
        }
    )


def _worker() -> None:
    global _handshake_seen
    while not _stop.is_set():
        try:
            request = _work.get(timeout=0.1)
        except queue.Empty:
            continue
        kind = request.get("type")
        request_id = str(request.get("requestId") or uuid.uuid4())
        if kind == "handshake":
            requested_version = request.get("protocolVersion", PROTOCOL_VERSION)
            if requested_version != PROTOCOL_VERSION:
                error(request_id, "protocol_version", f"Unsupported protocol version: {requested_version}")
            else:
                model, load_error, device = _load_model_once()
                _handshake_seen = True
                payload = {
                    "type": "handshake",
                    "requestId": request_id,
                    "protocolVersion": PROTOCOL_VERSION,
                    "hostVersion": HOST_VERSION,
                    "model": _model_name,
                    "device": device,
                    "multilingual": True,
                    # Parser-only mode is ready for diagnostics; configured mode
                    # is ready only once the model was loaded successfully.
                    "ready": model is not None or load_error is None,
                }
                if load_error:
                    payload["ready"] = False
                    payload["code"] = "model_unavailable"
                    payload["message"] = load_error
                send(payload)
        elif kind == "transcribe":
            if not _handshake_seen:
                error(request_id, "not_ready", "Handshake required.")
            else:
                cancel = threading.Event()
                with _active_lock:
                    _active[request_id] = cancel
                try:
                    _run_transcription(request, cancel)
                finally:
                    with _active_lock:
                        _active.pop(request_id, None)
        elif kind == "shutdown":
            _shutdown_requested.set()
            with _active_lock:
                for event in _active.values():
                    event.set()
            send({"type": "shutdown", "requestId": request_id, "ok": True})
            _stop.set()
        elif kind == "cancel":
            target = str(request.get("targetRequestId") or request.get("requestId") or "")
            with _active_lock:
                event = _active.get(target)
            if event is not None:
                event.set()
                send({"type": "cancelled", "requestId": target, "ok": True})
            else:
                error(request_id, "unknown_request", f"No active request {target}.")
        else:
            error(request_id, "unknown_type", "Unsupported request type.")
        _work.task_done()


def main() -> int:
    worker = threading.Thread(target=_worker, name="flowerwhisp-whisper", daemon=True)
    worker.start()
    try:
        for line in sys.stdin:
            if not line.strip():
                continue
            try:
                request = json.loads(line)
            except json.JSONDecodeError as exc:
                error(None, "invalid_json", str(exc))
                continue
            if not isinstance(request, dict):
                error(None, "invalid_request", "A request must be a JSON object.")
                continue
            kind = request.get("type")
            if kind == "cancel":
                target = str(request.get("targetRequestId") or request.get("requestId") or "")
                with _active_lock:
                    event = _active.get(target)
                if event is not None:
                    event.set()
                    send({"type": "cancelled", "requestId": target, "ok": True})
                else:
                    error(request.get("requestId"), "unknown_request", f"No active request {target}.")
            elif kind == "shutdown":
                # Handle shutdown on the reader thread so an open stdin does not
                # keep the process alive after the acknowledgement is flushed.
                _shutdown_requested.set()
                with _active_lock:
                    for event in _active.values():
                        event.set()
                send({"type": "shutdown", "requestId": str(request.get("requestId") or uuid.uuid4()), "ok": True})
                _stop.set()
                break
            else:
                _work.put(request)
            if _stop.is_set():
                break
    finally:
        _shutdown_requested.set()
        with _active_lock:
            for event in _active.values():
                event.set()
        _stop.set()
        worker.join(timeout=1.0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
