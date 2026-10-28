"""Bounded fictional PDF generation and document workload transfers."""

import base64
from concurrent.futures import FIRST_EXCEPTION, ThreadPoolExecutor, wait
import hashlib
import http.client
import json
import threading
import time
from urllib.parse import urlsplit
import uuid

import database_recovery as files
from measure_attendance import WorkloadError

MIB = 1024 ** 2


def make_pdf(path, size, seed):
    """One blank page with distinct, deterministic uncompressed PDF comments.

    Each block differs, including between files, so storage deduplication cannot
    reduce a nominal maximum-size workload to one repeated chunk.
    """
    header = b"%PDF-1.7\n"
    offsets = [0]
    for item in (b"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
                 b"2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
                 b"3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Contents 4 0 R >>\nendobj\n"):
        offsets.append(len(header))
        header += item
    offsets.append(len(header))
    prefix = header + b"4 0 obj\n<< /Length 0000000000 >>\nstream\n"
    ending = b"\nendstream\nendobj\n"
    # Fixed-width xref and startxref make the total size exact without padding
    # outside the PDF; all measured bytes belong to its content stream.
    xref = b"xref\n0 5\n0000000000 65535 f \n" + b"".join(f"{offset:010} 00000 n \n".encode() for offset in offsets[1:])
    tail = xref + b"trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n0000000000\n%%EOF\n"
    content_size = size - len(prefix) - len(ending) - len(tail)
    if not 1024 <= content_size <= 100 * MIB:
        raise ValueError("PDF fixture size is outside its bound")
    prefix = prefix.replace(b"/Length 0000000000", f"/Length {content_size:010}".encode())
    tail = tail.replace(b"startxref\n0000000000", f"startxref\n{len(prefix) + content_size + len(ending):010}".encode())
    digest, chunks = hashlib.sha256(), []
    with files.create_file(path) as output:
        output.write(prefix)
        remaining, block = content_size, 0
        while remaining:
            raw = hashlib.shake_256(seed.encode() + block.to_bytes(4, "big")).digest(48 * 1024)
            comments = b"%" + base64.encodebytes(raw).replace(b"\n", b"\n%")
            payload = comments[:remaining]
            output.write(payload)
            remaining -= len(payload)
            block += 1
        output.write(ending + tail)
    with path.open("rb") as source:
        while part := source.read(MIB):
            digest.update(part)
            chunks.append(hashlib.sha256(part).hexdigest())
    return {"size": size, "sha256": digest.hexdigest(), "chunkDigests": chunks}


def parallel_transfers(tasks, perform):
    """Only the requested 1–4 transfers exist; failure cancels and joins siblings."""
    if not 1 <= len(tasks) <= 4:
        raise ValueError("Use one to four explicit transfers")
    stop = threading.Event()
    pool = ThreadPoolExecutor(max_workers=len(tasks), thread_name_prefix="document-workload")
    pending = []
    try:
        pending = [pool.submit(perform, task, stop) for task in tasks]
        done, _ = wait(pending, timeout=300, return_when=FIRST_EXCEPTION)
        for result in done:
            result.result()
        if len(done) != len(pending):
            raise WorkloadError("Document transfer phase exceeded five minutes")
        return [result.result() for result in pending]
    finally:
        stop.set()
        for result in pending:
            result.cancel()
        pool.shutdown(wait=True, cancel_futures=True)


def transfer(endpoint, path, token, stop, *, body=None, offset=0, expected_size=None, expected_digest=None):
    """One chunk upload or streamed full download, with no retry and no body log."""
    if stop.is_set():
        raise WorkloadError("Document transfer cancelled")
    if body is not None and not 1 <= len(body) <= MIB:
        raise WorkloadError("Document upload chunk exceeds its bound")
    address = urlsplit(endpoint)
    connection = http.client.HTTPConnection(address.hostname, address.port, timeout=30)
    started, response = time.monotonic(), None
    deadline = started + (30 if body is not None else 70)
    headers = {"Authorization": "Bearer " + token}
    if body is not None:
        headers.update({"Content-Type": "application/octet-stream", "Idempotency-Key": str(uuid.uuid4()),
                        "Upload-Offset": str(offset), "Upload-Checksum-Sha256": hashlib.sha256(body).hexdigest()})
    try:
        connection.request("POST" if body is not None else "GET", "/api/v1" + path, body, headers)
        response = connection.getresponse()
        if response.status != 200:
            raise WorkloadError("Document transfer HTTP " + str(response.status))
        size, digest, content = 0, hashlib.sha256(), bytearray()
        limit = 2 * MIB if body is not None else expected_size
        if limit is None or not 0 < limit <= 100 * MIB:
            raise WorkloadError("Document response size bound is missing")
        if body is None and response.getheader("Content-Length") != str(expected_size):
            raise WorkloadError("Document download declaration did not match")
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0 or stop.is_set():
                raise WorkloadError("Document transfer deadline or cancellation")
            if connection.sock:
                connection.sock.settimeout(min(remaining, 5))
            part = response.read1(64 * 1024)
            if not part:
                break
            size += len(part)
            if size > limit:
                raise WorkloadError("Document response exceeded its bound")
            if body is not None:
                content.extend(part)
            else:
                digest.update(part)
        if body is not None:
            json.loads(content)
        elif size != expected_size or digest.hexdigest() != expected_digest:
            raise WorkloadError("Document downloaded content did not match its source")
        return {"seconds": round(time.monotonic() - started, 6), "bytes": len(body) if body is not None else size}
    finally:
        if response is not None:
            response.close()
        connection.close()
