"""Run-slot helper (security review M-1, SV-069), started by the supervisor as the slot's uid.

    slot.py reset DIR   deletes everything in DIR; exits 0 only when DIR is empty

The supervisor kills every process of the slot's uid before it starts this helper, so nothing of a run can interfere.
A run may leave files behind on purpose; the next run of the slot has the same uid, so they go before it starts.
Directories are made accessible to their owner first, so a run cannot keep an entry by removing its own permissions;
symbolic links are deleted, never followed; the walk is iterative, so no depth stops it.
"""
import os
import signal
import stat
import sys


def empty(directory):
    found = []
    pending = [directory]
    while pending:
        current = pending.pop()
        try:
            entries = list(os.scandir(current))
        except OSError:
            continue
        for entry in entries:
            try:
                if entry.is_dir(follow_symlinks=False):
                    os.chmod(entry.path, 0o700)
                    pending.append(entry.path)
                    found.append(entry.path)
                else:
                    os.unlink(entry.path)
            except OSError:
                pass
    # a directory is found before the directories inside it: remove in the reverse order
    for path in reversed(found):
        try:
            os.rmdir(path)
        except OSError:
            pass


def main():
    if len(sys.argv) != 3 or sys.argv[1] != "reset":
        sys.exit(2)
    try:
        # required with the supervisor's kill: it also reaches a process the supervisor's scan could not see
        os.kill(-1, signal.SIGKILL)
    except ProcessLookupError:
        pass
    directory = sys.argv[2]
    info = os.lstat(directory)
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid():
        sys.exit(3)
    os.chmod(directory, 0o700)
    try:
        # POSIX message queues are the one other place a run can leave data (the container has no shared memory)
        for entry in os.scandir("/dev/mqueue"):
            if entry.stat(follow_symlinks=False).st_uid == os.getuid():
                os.unlink(entry.path)
    except OSError:
        pass
    try:
        empty(directory)
    except Exception:
        sys.exit(4)
    sys.exit(0 if not os.listdir(directory) else 4)


if __name__ == "__main__":
    main()
