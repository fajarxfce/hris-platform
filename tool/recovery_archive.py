"""Bounded local archive I/O for owned recovery checkpoints (Linux only)."""

import os
from pathlib import Path, PurePosixPath
import selectors
import subprocess
import tarfile
import time

from database_recovery import RecoveryError, create_file

MAX_ENTRIES = 100_000


def capture_file(arguments, destination, log, *, limit, timeout, stdin=None):
    """Stream a child into a private file; deadline/size failure kills and joins it."""
    completed = False
    created = False
    process = None
    deadline = time.monotonic() + timeout
    try:
        with create_file(destination) as output:
            created = True
            process = subprocess.Popen(arguments, stdin=stdin or subprocess.DEVNULL,
                                       stdout=subprocess.PIPE, stderr=log)
            total = 0
            with selectors.DefaultSelector() as selector:
                selector.register(process.stdout, selectors.EVENT_READ)
                while True:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise RecoveryError("Recovery stream exceeded its deadline")
                    if not selector.select(min(remaining, 0.25)):
                        continue
                    chunk = os.read(process.stdout.fileno(), 1024 * 1024)
                    if not chunk:
                        break
                    total += len(chunk)
                    if total > limit:
                        raise RecoveryError("Recovery stream exceeded its size limit")
                    output.write(chunk)
            if process.wait(timeout=max(0.01, deadline - time.monotonic())):
                raise RecoveryError("Recovery stream failed; inspect the private log")
            output.flush()
            os.fsync(output.fileno())
        completed = True
        return total
    except (OSError, subprocess.TimeoutExpired):
        raise RecoveryError("Recovery stream could not complete; inspect the private log") from None
    finally:
        try:
            if process is not None:
                if process.poll() is None:
                    process.kill()
                process.wait(timeout=5)
                process.stdout.close()
        finally:
            if created and not completed:
                destination.unlink(missing_ok=True)


def archive_entries(archive, *, limit):
    """Allow only bounded regular files/directories, never links or special files."""
    seen = set()
    declared = 0
    for entry in archive:
        name = entry.name
        if name.startswith("./"):
            name = name[2:]
        if name in ("", ".") and entry.isdir():
            name = "."
        path = PurePosixPath(name)
        if (not name or len(name.encode()) > 1024 or path.is_absolute()
                or ".." in path.parts or len(path.parts) > 32 or "\x00" in name
                or str(path) != name.rstrip("/") or str(path) in seen
                or not (entry.isdir() or entry.isreg()) or entry.sparse is not None
                or len(seen) >= MAX_ENTRIES or entry.size < 0):
            raise RecoveryError("Unsupported or unsafe recovery archive entry")
        seen.add(str(path))
        declared += entry.size
        if declared > limit or (entry.isdir() and entry.size != 0):
            raise RecoveryError("Recovery archive contents exceed their limit")
        yield entry, path


def inspect_archive(path, *, limit):
    try:
        # Tar's PAX/GNU extension parser materializes extension records. Check raw
        # headers first so an oversized extension cannot allocate before our
        # normal member limits run. Docker emits plain (uncompressed) tar here.
        length = path.stat().st_size
        headers = 0
        with path.open("rb") as source:
            while True:
                block = source.read(512)
                if block == b"\0" * 512:
                    break
                if len(block) != 512 or headers >= MAX_ENTRIES * 3:
                    raise RecoveryError("Truncated or excessive recovery archive headers")
                entry = tarfile.TarInfo.frombuf(block, "utf-8", "strict")
                allowed = (tarfile.REGTYPE, tarfile.AREGTYPE, tarfile.DIRTYPE,
                           tarfile.XHDTYPE, tarfile.XGLTYPE, tarfile.GNUTYPE_LONGNAME)
                if (entry.type not in allowed or entry.size < 0 or entry.size > limit
                        or (entry.type in allowed[3:] and entry.size > 16 * 1024)):
                    raise RecoveryError("Unsupported or oversized recovery archive header")
                following = source.tell() + ((entry.size + 511) // 512) * 512
                if following > length:
                    raise RecoveryError("Truncated recovery archive contents")
                source.seek(following)
                headers += 1
        with tarfile.open(path, mode="r|") as archive:
            count = sum(1 for _ in archive_entries(archive, limit=limit))
        if not count:
            raise RecoveryError("Recovery archive is empty")
        return count
    except (tarfile.TarError, UnicodeError, ValueError):
        raise RecoveryError("Invalid recovery archive") from None


def restore_archive(path, destination, *, limit):
    # Validate the entire archive before creating the first output. Destination is
    # a newly owned directory; extraction never applies archived uid/mode values.
    inspect_archive(path, limit=limit)
    destination.mkdir(mode=0o700)
    try:
        with tarfile.open(path, mode="r|") as archive:
            for entry, relative in archive_entries(archive, limit=limit):
                target = destination / relative
                if entry.isdir():
                    target.mkdir(mode=0o700, parents=True, exist_ok=True)
                    continue
                target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
                remaining = entry.size
                with archive.extractfile(entry) as source, create_file(target) as output:
                    while remaining:
                        chunk = source.read(min(1024 * 1024, remaining))
                        if not chunk:
                            raise RecoveryError("Recovery archive ended before its declared size")
                        output.write(chunk)
                        remaining -= len(chunk)
    except (tarfile.TarError, UnicodeError, ValueError, OSError):
        raise RecoveryError("Recovery archive could not be restored") from None
