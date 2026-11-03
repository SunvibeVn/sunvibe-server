"""Survey-media processing supervisor (ADR-011 decision 4, form (a); survey-media spec 4.5, A-27, A-28).

Reads one framed request per connection (PROTOCOL.md) and runs it in one of its run slots (--max-runs). Each slot has
its own uid (SLOT_UID_BASE + slot) and its own 0700 directory of the tmpfs work area, created here and given to that
uid, so concurrent runs never share a uid or a file (security review M-1, SV-069). The child of a run starts as the
slot's uid without capabilities (setpriv) under OS limits (prlimit: CPU seconds, address space, file size, no core;
one decoder thread; a wall clock enforced here); it receives the source on stdin and answers on stdout, so the
supervisor never opens a file a child wrote (N-2). Before a run, before its answer and at start-up the supervisor kills
every live process of the slot's uid itself, then a helper running as that uid empties its directory; a reset that does
not complete stops the supervisor (a fresh container follows). The supervisor runs as root with only SETUID, SETGID,
CHOWN and KILL (compose.yaml). Python standard library only; no shell is used.
"""
import argparse
import json
import os
import queue
import re
import signal
import socketserver
import stat
import struct
import subprocess
import sys
import threading
import time

MAGIC_REQUEST = b"SVM1"
MAGIC_REPLY = b"SVR1"
MAGIC_CHILD = b"SVP1"
OP_DETECT = 0x01
OP_NORMALIZE = 0x02
OP_TEST_SLEEP = 0x71
OP_TEST_MEMORY = 0x72
OP_TEST_DAEMON = 0x73
OP_TEST_BAD_OUTPUT = 0x74
OP_TEST_HOSTILE = 0x75
OP_TEST_DEEP_TREE = 0x76
OPS = {OP_DETECT: "detect", OP_NORMALIZE: "normalize"}
TEST_OPS = {OP_TEST_SLEEP: "test-sleep", OP_TEST_MEMORY: "test-memory", OP_TEST_DAEMON: "test-daemon",
            OP_TEST_BAD_OUTPUT: "test-bad-output", OP_TEST_HOSTILE: "test-hostile", OP_TEST_DEEP_TREE: "test-deep-tree"}

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
CHILD_OUTPUT_CAP = len(MAGIC_CHILD) + 4 + RESULT_CAP + sum(4 + cap for cap in VARIANT_CAPS.values())
CHILD_ERROR_CAP = 64 * 1024
BODY_TIMEOUT_SECONDS = 60
SLOT_UID_BASE = 10100
# processes and threads of one slot's uid (RLIMIT_NPROC is per uid): a run cannot take the container's pids
SLOT_PROCESSES = 16
CODE = re.compile(r"[A-Z0-9_]{1,32}")
HERE = os.path.dirname(os.path.abspath(__file__))
PIPELINE = os.path.join(HERE, "pipeline.py")
SLOT_TOOL = os.path.join(HERE, "slot.py")
CHILD_ENV = {
    "PATH": "/usr/bin:/bin",
    "LANG": "C.UTF-8",
    "VIPS_CONCURRENCY": "1",
    "VIPS_BLOCK_UNTRUSTED": "1",
    "MALLOC_ARENA_MAX": "2",
    "PYTHONDONTWRITEBYTECODE": "1",
}


class Settings:
    work = "/work"
    cpu_seconds = 60
    wall_seconds = 120
    memory_bytes = 1536 * 1024 * 1024
    file_bytes = 64 * 1024 * 1024
    max_runs = 3
    test_requests = False


class BadOutput(Exception):
    """The child's answer is not a well-formed result."""


def log(message):
    print(time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()) + " " + message, file=sys.stderr, flush=True)


def slot_uid(slot):
    return SLOT_UID_BASE + slot


def slot_dir(slot):
    return os.path.join(Settings.work, "slot-%d" % slot)


def as_slot(slot, command):
    """The command as the slot's uid and gid, without supplementary groups or capabilities."""
    uid = slot_uid(slot)
    return ["setpriv", "--reuid=%d" % uid, "--regid=%d" % uid, "--clear-groups", "--inh-caps=-all", "--"] + command


def prepare_slots():
    """Creates each slot's directory (0700, owned by its uid) and resets it; refuses to start on anything else."""
    os.chmod(Settings.work, 0o711)
    for slot in range(1, Settings.max_runs + 1):
        path = slot_dir(slot)
        try:
            os.mkdir(path, 0o700)
            os.chown(path, slot_uid(slot), slot_uid(slot))
        except FileExistsError:
            pass
        info = os.lstat(path)
        if not stat.S_ISDIR(info.st_mode) or info.st_uid != slot_uid(slot) or stat.S_IMODE(info.st_mode) != 0o700:
            raise SystemExit("work area entry slot-%d is not the slot's own 0700 directory" % slot)
        reset_slot(slot)


def uid_processes(uid):
    """The pids of the container's live processes whose real uid is uid (a zombie can do nothing; it waits to be
    reaped by its parent or the init)."""
    pids = []
    for name in os.listdir("/proc"):
        if not name.isdigit():
            continue
        try:
            with open("/proc/%s/status" % name, "rb") as handle:
                fields = dict(line.split(b":", 1) for line in handle if b":" in line)
            # a zombie whose other threads still run is alive (re-review n-1); a plain zombie waits to be reaped
            if fields[b"State"].split()[0] in (b"Z", b"X") and int(fields[b"Threads"].split()[0]) <= 1:
                continue
            if int(fields[b"Uid"].split()[0]) == uid:
                pids.append(int(name))
        except (OSError, ValueError, IndexError, KeyError):
            continue
    return pids


def kill_uid(uid):
    """Kills every process of uid from here (CAP_KILL), so no process of the slot can resist; True when none is left."""
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        pids = uid_processes(uid)
        if not pids:
            return True
        for pid in pids:
            try:
                os.kill(pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        time.sleep(0.02)
    return not uid_processes(uid)


def reset_slot(slot, answer_first=None):
    """
    Ends every process of the slot's uid (from the supervisor, which a run cannot signal), then empties its directory as
    that uid (only it can read the files) with no other process of the uid alive. A reset that does not complete ends
    the supervisor: the slot is never used again unreset, and the restart brings a fresh work area (security review M-1).
    answer_first, when given, answers the run before that, so a run that defeats its reset is still a limit hit for the
    API (the two-strike rule and the expensive cap, survey-media spec A-7, A-27).
    """
    try:
        clean = kill_uid(slot_uid(slot))
        if clean:
            done = subprocess.run(as_slot(slot, [sys.executable, "-I", SLOT_TOOL, "reset", slot_dir(slot)]),
                                  stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                  env=CHILD_ENV, close_fds=True, timeout=30, check=False)
            clean = done.returncode == 0 and kill_uid(slot_uid(slot))
    except Exception:
        clean = False
    if not clean:
        log("run slot %d could not be reset; the supervisor stops" % slot)
        if answer_first is not None:
            try:
                answer_first()
            except Exception:
                pass
        os._exit(70)


def sweep_only():
    """The --sweep-only check: every slot reset and every other entry of the work area deleted."""
    prepare_slots()
    slots = {"slot-%d" % slot for slot in range(1, Settings.max_runs + 1)}
    for entry in os.scandir(Settings.work):
        if entry.name in slots:
            continue
        if entry.is_dir(follow_symlinks=False):
            import shutil
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
    encoded = code.encode("ascii", "replace")[:255]
    conn.sendall(MAGIC_REPLY + bytes([status, len(encoded)]) + encoded + payload)


def read_capped(stream, cap, into):
    """Reads the stream to its end, keeping at most cap bytes in into (a list); the rest is read and dropped."""
    kept = 0
    while True:
        data = stream.read(65536)
        if not data:
            return
        if kept < cap:
            into.append(data[:cap - kept])
        kept += len(data)
        if kept > cap:
            into.append(None)


def feed(stream, body):
    try:
        stream.write(body)
        stream.close()
    except OSError:
        pass  # the child ended early: its exit status tells why


def run_child(slot, op_name, body):
    """Runs one pipeline child in the slot under the limits; returns (status, code, output bytes or None)."""
    command = as_slot(slot, [
        "prlimit",
        "--cpu=%d" % Settings.cpu_seconds,
        "--as=%d" % Settings.memory_bytes,
        "--fsize=%d" % Settings.file_bytes,
        "--core=0",
        "--nofile=64",
        "--nproc=%d" % SLOT_PROCESSES,
        "--",
        sys.executable, "-I", PIPELINE, op_name, slot_dir(slot),
    ])
    env = dict(CHILD_ENV, HOME=slot_dir(slot), TMPDIR=slot_dir(slot))
    child = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                             env=env, cwd="/", start_new_session=True, close_fds=True)
    output, errors = [], []
    readers = [threading.Thread(target=read_capped, args=(child.stdout, CHILD_OUTPUT_CAP, output), daemon=True),
               threading.Thread(target=read_capped, args=(child.stderr, CHILD_ERROR_CAP, errors), daemon=True),
               threading.Thread(target=feed, args=(child.stdin, body), daemon=True)]
    for reader in readers:
        reader.start()
    try:
        child.wait(timeout=Settings.wall_seconds)
    except subprocess.TimeoutExpired:
        reset_slot(slot)
        try:
            child.wait(timeout=10)
        except subprocess.TimeoutExpired:
            pass
        return STATUS_LIMIT_HIT, "WALL_CLOCK", None
    finally:
        for reader in readers:
            reader.join(timeout=5)
    if child.returncode != 0:
        return STATUS_LIMIT_HIT, "CHILD_EXIT", None
    if None in output:
        return STATUS_FAILED, "BAD_OUTPUT", None
    return STATUS_OK, "", b"".join(output)


def parse_output(data):
    """The child's answer: SVP1, the result JSON and the bytes of each variant it names (PROTOCOL.md)."""
    try:
        if data[:4] != MAGIC_CHILD:
            raise BadOutput()
        length = struct.unpack_from(">I", data, 4)[0]
        if length > RESULT_CAP:
            raise BadOutput()
        result = json.loads(data[8:8 + length])
        if not isinstance(result, dict):
            raise BadOutput()
        at = 8 + length
        blobs = []
        variants = result.get("variants", []) if result.get("status") == "OK" else []
        if not isinstance(variants, list) or len(variants) > len(VARIANT_KINDS):
            raise BadOutput()
        for variant in variants:
            size = struct.unpack_from(">I", data, at)[0]
            if size > VARIANT_CAPS[variant["kind"]] or at + 4 + size > len(data):
                raise BadOutput()
            blobs.append(data[at + 4:at + 4 + size])
            at += 4 + size
        if at != len(data):
            raise BadOutput()
        status, code = result.get("status"), result.get("code", "")
        # N-2: only a known status and a short upper-case code are used or logged
        if status not in STATUS_BY_NAME or not isinstance(code, str) or (code and not CODE.fullmatch(code)):
            raise BadOutput()
        return result, blobs
    except Exception:
        raise BadOutput()


def normalize_payload(result, blobs):
    """The OK payload of a normalize run: source dimensions and the variants within their caps."""
    try:
        parts = [struct.pack(">IIB", int(result["width"]), int(result["height"]), len(blobs))]
        for variant, data in zip(result["variants"], blobs):
            parts.append(struct.pack(">BIII", VARIANT_KINDS[variant["kind"]], int(variant["width"]),
                                     int(variant["height"]), len(data)))
            parts.append(data)
        return b"".join(parts)
    except Exception:
        raise BadOutput()


class Handler(socketserver.BaseRequestHandler):
    slots = None

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
        slot = Handler.slots.get()
        reset = False
        try:
            reset_slot(slot)
            status, code, output = run_child(slot, op_name, body)
            payload = b""
            if status == STATUS_OK:
                try:
                    result, blobs = parse_output(output)
                    status = STATUS_BY_NAME[result["status"]]
                    code = result.get("code", "")
                    if status == STATUS_OK and op_name in ("normalize", "test-bad-output"):
                        payload = normalize_payload(result, blobs)
                except BadOutput:
                    status, code, payload = STATUS_FAILED, "BAD_OUTPUT", b""
            # survey-media spec A-17: the run's processes and files are gone before it is answered
            reset_slot(slot, answer_first=lambda: reply(conn, STATUS_LIMIT_HIT, "RESET_FAILED"))
            reset = True
            reply(conn, status, code, payload)
            log("run op=%s slot=%d status=%d code=%s ms=%d" % (op_name, slot, status, code,
                                                             int((time.monotonic() - started) * 1000)))
        except OSError:
            pass
        finally:
            if not reset:
                reset_slot(slot)
            Handler.slots.put(slot)


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
                        help="test-only: accept the test requests; never in compose.yaml")
    parser.add_argument("--sweep-only", action="store_true", help="reset the work area and exit")
    args = parser.parse_args()
    Settings.work = args.work
    Settings.wall_seconds = args.wall_seconds
    Settings.cpu_seconds = args.cpu_seconds
    Settings.memory_bytes = args.memory_bytes
    Settings.max_runs = args.max_runs
    Settings.test_requests = args.enable_test_requests
    if args.sweep_only:
        sweep_only()
        return
    prepare_slots()
    Handler.slots = queue.Queue()
    for slot in range(1, Settings.max_runs + 1):
        Handler.slots.put(slot)
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
