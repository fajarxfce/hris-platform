"""Final-output redaction for HRIS's opaque Matrix pusher capabilities.

Mount this module read-only beside the reviewed logging configuration, and add
that directory to PYTHONPATH. It does not change provider delivery behavior.
"""
import copy
import logging
import re
import time


_PUSH_KEY = re.compile(r"hris(?:cp|gp)1_[A-Za-z0-9_-]*")
_MAXIMUM_BYTES = 16384
_TRUNCATED = b" [truncated]"


class BoundedRedactingFormatter(logging.Formatter):
    converter = time.gmtime

    def format(self, record):
        try:
            # Formatter caches exception text on the record. Keep this formatter's
            # work private so another handler cannot reuse an unredacted cache.
            rendered = super().format(copy.copy(record))
            redacted = _PUSH_KEY.sub("<hris-push-key>", rendered)
            encoded = redacted.encode("utf-8")
            if len(encoded) > _MAXIMUM_BYTES:
                encoded = encoded[:_MAXIMUM_BYTES - len(_TRUNCATED)]
                return encoded.decode("utf-8", errors="ignore") + _TRUNCATED.decode("ascii")
            return redacted
        except Exception:
            # logging.Handler.handleError would otherwise print the original
            # message and arguments when formatting fails. Never invoke it here.
            return "Log record could not be formatted."
