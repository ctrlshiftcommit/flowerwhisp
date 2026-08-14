import json
import pathlib
import subprocess
import sys

host = pathlib.Path(__file__).with_name("host.py")
proc = subprocess.Popen([sys.executable, str(host)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
def call(payload):
    proc.stdin.write(json.dumps(payload) + "\n"); proc.stdin.flush(); return json.loads(proc.stdout.readline())
handshake = call({"type": "handshake", "requestId": "test-handshake"})
assert handshake["type"] == "handshake" and handshake["protocolVersion"] == 1 and handshake["ready"] is True
error = call({"type": "transcribe", "requestId": "test-transcribe", "audioPath": ""})
assert error["type"] == "error" and error["code"] == "missing_audio"
shutdown = call({"type": "shutdown", "requestId": "test-shutdown"})
assert shutdown["type"] == "shutdown" and shutdown["ok"] is True
assert proc.wait(timeout=5) == 0
print("local-whisper-host protocol handshake/error/shutdown: PASS")
