#!/usr/bin/env python3
"""Local-only smoke test. Starts its own container on a random loopback port, then removes it."""
import json
import subprocess
import sys
import time
import urllib.request
import urllib.error

image = sys.argv[1] if len(sys.argv) > 1 else "salah-app:local"
def docker(*args):
    return subprocess.check_output(["docker", *args], text=True).strip()

container = docker("run", "-d", "--memory", "1g", "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
                   "-e", "DISPLAY_NAME=Salah", "-p", "127.0.0.1::8080", image)
try:
    port = docker("port", container, "8080/tcp").split(":")[-1]
    base = f"http://127.0.0.1:{port}"
    def request(path, method="GET", body=None):
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(base + path, data=data, method=method,
                                     headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=5) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None, response.headers
    for attempt in range(60):
        try:
            if request("/health")[0] == 200:
                break
        except (OSError, urllib.error.URLError):
            time.sleep(1)
    else:
        raise RuntimeError("Container did not become healthy")
    assert docker("exec", container, "id", "-u") == "10001"
    assert request("/api/name")[1] == {"name": "Salah"}
    assert request("/api/whoami")[1]["serverId"] == "local"
    assert request("/api/messages", "POST", {"text": "Container message"})[0] == 202
    assert request("/api/messages/receive", "POST")[1]["text"] == "Container message"
    assert request("/api/messages/receive", "POST")[0] == 204
    assert request("/api/notes/smoke", "PUT", {"text": "Saved from Docker"})[0] == 200
    status, note, headers = request("/api/notes/smoke")
    assert status == 200 and note["text"] == "Saved from Docker"
    assert headers.get("X-Request-Id")
    docker("stop", "--time", "30", container)
    logs = docker("logs", container)
    assert '"event":"app_stopping"' in logs and '"event":"app_stopped"' in logs
    assert docker("inspect", "--format", "{{.State.ExitCode}}", container) in ("0", "143")
    print("PASS: health, identity, queue, notes, non-root user and SIGTERM shutdown")
except Exception:
    print(docker("logs", container), file=sys.stderr)
    raise
finally:
    subprocess.run(["docker", "rm", "-f", container], check=False, stdout=subprocess.DEVNULL)
