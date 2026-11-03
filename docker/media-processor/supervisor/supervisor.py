"""Survey-media processing supervisor (ADR-011 decision 4, form (a); survey-media spec 4.5, A-27, A-28).

Reads one framed request per connection (PROTOCOL.md), writes the source to a server-random file in a 0700 directory
of the tmpfs work area, starts one child per run with OS limits (prlimit: CPU seconds, address space, file size,
no core; one decoder thread; a wall clock enforced here), drains the child's output with a size cap without logging
it, answers with the result and deletes the run's files in a finally block and at start-up. Python standard library
only; no shell is used.
"""
import argparse
import json
import os
import secrets
import shutil
import signal
import socket
import socketserver
import struct
import subprocess
import sys
import tempfile
import threading
import time

MAGIC_REQUEST = b"SVM1"
MAGIC_REPLY = b"SVR1"
OP_DETECT = 0x01
OP_NORMALIZE = 0x02
OP_TEST_SLEEP = 0x71
OP_TEST_MEMORY = 0x72
OPS = {OP_DETECT: "detect", OP_NORMALIZE: "normalize"}
TEST_OPS = {OP_TEST_SLEEP: "test-sleep", OP_TEST_MEMORY: "test-memory"}

STATUS_OK = 0
STATUS_REJECTED = 1
STATUS_LIMIT_HIT = 2
STATUS_FAILED = 3
STATUS_REFUSED = 4
STATUS_BY_NAME = {"OK": STATUS_OK, "REJECTED": STATUS_REJECTED, "FAILED": STATUS_FAILED}

MAX_SOURCE_BYTES = 20 * 1024 * 1024  # spec A-2: 20 MiB per file
VARIANT_CAPS = {"FULL": 12 * 1024 * 1024, "PREVIEW": 2 * 1024 * 1024, "THUMBNAIL": 256 * 1024}  # spec A-28
VARIANT_KINDS = {"FULL": 1, "PREVIEW": 2, "THUMBNAIL": 3}
RESULT_CAP = 64 * 1024
CHILD_OUTPUT_CAP = 64 * 1024
BODY_TIMEOUT_SECONDS = 60
PIPELINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "pipeline.py")


class Settings:
    work = "/work"
    cpu_seconds = 60
    wall_seconds = 120
    memory_bytes = 1536 * 1024 * 1024
    file_bytes = 64 * 1024 * 1024
    max_runs = 3
    test_requests = False


def log(message):
    print(time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()) + " " + message, file=sys.stderr, flush=True)


def sweep(work):
    """Deletes every entry of the work area (start-up, and the --sweep-only check)."""
    for entry in os.scandir(work):
        if entry.is_dir(follow_symlinks=False):
            shutil.rmtree(entry.path, ignore_errors=True)
        else:
            try:
                os.unlink(entry.path)
            except OSError:
                pass


def recv_exact(conn, length):
    chunks = []
    remaining = length
    while remaining > 0:
        chunk = conn.recv(min(remaining, 1024 * 1024))
        if not chunk:
            raise ConnectionError("short frame")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def reply(conn, status, code, payload=b""):
    encoded = code.encode("ascii")[:255]
    conn.sendall(MAGIC_REPLY + bytes([status, len(encoded)]) + encoded + payload)


def drain(stream, cap):
    """Reads the child's output to the end, keeping nothing beyond the cap; it is never logged."""
    kept = 0
    while True:
        data = stream.read(8192)
        if not data:
            return
        kept = min(cap, kept + len(data))


def run_child(op_name, workdir, source):
    """Runs one pipeline child under the limits; returns (status, code, result dict or None)."""
    command = [
        "prlimit",
        "--cpu=%d" % Settings.cpu_seconds,
        "--as=%d" % Settings.memory_bytes,
        "--fsize=%d" % Settings.file_bytes,
        "--core=0",
        "--nofile=64",
        "--",
        sys.executable, "-I", PIPELINE, op_name, workdir, source,
    ]
    env = {
        "PATH": "/usr/bin:/bin",
        "LANG": "C.UTF-8",
        "HOME": workdir,
        "TMPDIR": workdir,
        "VIPS_CONCURRENCY": "1",
        "VIPS_BLOCK_UNTRUSTED": "1",
        "MALLOC_ARENA_MAX": "2",
        "PYTHONDONTWRITEBYTECODE": "1",
    }
    child = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                             env=env, cwd=workdir, start_new_session=True, close_fds=True)
    drainer = threading.Thread(target=drain, args=(child.stdout, CHILD_OUTPUT_CAP), daemon=True)
    drainer.start()
    try:
        child.wait(timeout=Settings.wall_seconds)
    except subprocess.TimeoutExpired:
        kill_group(child)
        return STATUS_LIMIT_HIT, "WALL_CLOCK", None
    finally:
        kill_group(child)
        drainer.join(timeout=5)
    if child.returncode != 0:
        return STATUS_LIMIT_HIT, "CHILD_EXIT", None
    result_path = os.path.join(workdir, "result.json")
    try:
        with open(result_path, "rb") as handle:
            result = json.loads(handle.read(RESULT_CAP))
    except (OSError, ValueError):
        return STATUS_LIMIT_HIT, "NO_RESULT", None
    return STATUS_BY_NAME.get(result.get("status"), STATUS_FAILED), str(result.get("code", "")), result


def kill_group(child):
    try:
        os.killpg(child.pid, signal.SIGKILL)
    except (ProcessLookupError, PermissionError):
        pass
    try:
        child.wait(timeout=5)
    except subprocess.TimeoutExpired:
        pass


def normalize_payload(workdir, result):
    """The OK payload of a normalize run: source dimensions and the three variants within their caps."""
    variants = result.get("variants", [])
    parts = [struct.pack(">IIB", int(result["width"]), int(result["height"]), len(variants))]
    for variant in variants:
        kind = variant["kind"]
        path = os.path.join(workdir, os.path.basename(variant["file"]))
        size = os.path.getsize(path)
        if size > VARIANT_CAPS[kind]:
            raise ValueError("variant over its cap")
        with open(path, "rb") as handle:
            data = handle.read(size)
        parts.append(struct.pack(">BIII", VARIANT_KINDS[kind], int(variant["width"]), int(variant["height"]),
                                 len(data)))
        parts.append(data)
    return b"".join(parts)


class Handler(socketserver.BaseRequestHandler):
    runs = None

    def handle(self):
        conn = self.request
        conn.settimeout(BODY_TIMEOUT_SECONDS)
        try:
            header = recv_exact(conn, 9)
        except (OSError, ConnectionError):
            return
        magic, op, length = header[:4], header[4], struct.unpack(">I", header[5:9])[0]
        if magic != MAGIC_REQUEST:
            reply(conn, STATUS_REFUSED, "BAD_MAGIC")
            return
        op_name = OPS.get(op) or (TEST_OPS.get(op) if Settings.test_requests else None)
        if op_name is None:
            reply(conn, STATUS_REFUSED, "BAD_OP")
            return
        if length > MAX_SOURCE_BYTES:
            # refused before any byte of the body is read
            reply(conn, STATUS_REFUSED, "FRAME_TOO_LARGE")
            return
        try:
            body = recv_exact(conn, length)
        except (OSError, ConnectionError):
            return
        started = time.monotonic()
        with Handler.runs:
            workdir = tempfile.mkdtemp(dir=Settings.work, prefix="run-")
            try:
                source = os.path.join(workdir, secrets.token_hex(16))
                descriptor = os.open(source, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                with os.fdopen(descriptor, "wb") as handle:
                    handle.write(body)
                status, code, result = run_child(op_name, workdir, source)
                payload = b""
                if status == STATUS_OK and op_name == "normalize":
                    try:
                        payload = normalize_payload(workdir, result)
                    except (OSError, ValueError, KeyError):
                        status, code, payload = STATUS_FAILED, "BAD_OUTPUT", b""
                reply(conn, status, code, payload)
                log("run op=%s status=%d code=%s ms=%d" % (op_name, status, code,
                                                          int((time.monotonic() - started) * 1000)))
            except OSError:
                pass
            finally:
                shutil.rmtree(workdir, ignore_errors=True)


class TcpServer(socketserver.ThreadingTCPServer):
    daemon_threads = True
    allow_reuse_address = True


class UnixServer(socketserver.ThreadingUnixStreamServer):
    daemon_threads = True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--listen", default="tcp:0.0.0.0:7300", help="tcp:HOST:PORT or unix:PATH")
    parser.add_argument("--work", default="/work")
    parser.add_argument("--wall-seconds", type=int, default=120)
    parser.add_argument("--cpu-seconds", type=int, default=60)
    parser.add_argument("--memory-bytes", type=int, default=1536 * 1024 * 1024)
    parser.add_argument("--max-runs", type=int, default=3)
    parser.add_argument("--enable-test-requests", action="store_true",
                        help="test-only: accept the SLEEP and MEMORY requests; never in compose.yaml")
    parser.add_argument("--sweep-only", action="store_true", help="delete the work area's files and exit")
    args = parser.parse_args()
    Settings.work = args.work
    Settings.wall_seconds = args.wall_seconds
    Settings.cpu_seconds = args.cpu_seconds
    Settings.memory_bytes = args.memory_bytes
    Settings.max_runs = args.max_runs
    Settings.test_requests = args.enable_test_requests
    sweep(Settings.work)
    if args.sweep_only:
        return
    Handler.runs = threading.BoundedSemaphore(Settings.max_runs)
    kind, _, address = args.listen.partition(":")
    if kind == "unix":
        if os.path.exists(address):
            os.unlink(address)
        server = UnixServer(address, Handler)
    else:
        host, _, port = address.rpartition(":")
        server = TcpServer((host, int(port)), Handler)
    log("supervisor listening on " + args.listen)
    signal.signal(signal.SIGTERM, lambda *_: threading.Thread(target=server.shutdown, daemon=True).start())
    server.serve_forever()


if __name__ == "__main__":
    main()
