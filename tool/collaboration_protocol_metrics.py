"""Bounded, identifier-free request counters from the owned Synapse fixture."""
from decimal import Decimal, InvalidOperation
import json
import re
import urllib.error
import urllib.request

MAX_METRICS_BYTES = 2 * 1024 * 1024
MAX_COUNTERS = 256
METRIC = "synapse_http_server_requests_received"
LABEL = re.compile(r'([a-z_]+)=("(?:[^"\\]|\\["\\n])*")')
SERVLET = re.compile(r"[A-Za-z_][A-Za-z0-9_]{0,95}")
METHODS = {"GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS"}


def protocol_counts(payload: bytes):
    if len(payload) > MAX_METRICS_BYTES:
        raise ValueError("Protocol metrics exceeded the fixture bound")
    text = payload.decode("utf-8", errors="strict")
    counts = {}
    for line in text.splitlines():
        if not (line.startswith(METRIC + "{") or line.startswith(METRIC + "_total{")):
            continue
        match = re.fullmatch(METRIC + r'(?:_total)?\{([^}]*)\} ([0-9.eE+-]+)', line)
        if not match:
            raise ValueError("Invalid protocol counter structure")
        raw_labels, raw_count = match.groups()
        pairs = LABEL.findall(raw_labels)
        if ",".join(key + "=" + value for key, value in pairs) != raw_labels:
            raise ValueError("Invalid protocol counter labels")
        labels = {key: json.loads(value) for key, value in pairs}
        if len(labels) != len(pairs) or set(labels) != {"method", "servlet", "server_name"}:
            raise ValueError("Unexpected protocol counter labels")
        if (labels["method"] not in METHODS or not SERVLET.fullmatch(labels["servlet"])
                or labels["server_name"] != "proof.invalid"):
            raise ValueError("Unsafe protocol counter labels")
        try:
            count = Decimal(raw_count)
        except InvalidOperation:
            raise ValueError("Invalid protocol counter value") from None
        if not count.is_finite() or count < 0 or count > 1_000_000 or count != int(count):
            raise ValueError("Invalid protocol counter value")
        key = (labels["method"], labels["servlet"])
        if key in counts or len(counts) >= MAX_COUNTERS:
            raise ValueError("Duplicate or excessive protocol counters")
        counts[key] = int(count)
    if not counts:
        raise ValueError("No protocol request counters found")
    return [{"method": method, "servlet": servlet, "count": count}
            for (method, servlet), count in sorted(counts.items())]


def collect_protocol_counts(origin: str):
    # The caller supplies its owned loopback provider origin, never a user URL.
    try:
        response = urllib.request.urlopen(origin + "/_synapse/metrics", timeout=8)
    except urllib.error.HTTPError as error:
        error.close()
        raise RuntimeError("Protocol metrics endpoint failed") from None
    with response:
        if response.status != 200:
            raise RuntimeError("Protocol metrics endpoint failed")
        return protocol_counts(response.read(MAX_METRICS_BYTES + 1))
