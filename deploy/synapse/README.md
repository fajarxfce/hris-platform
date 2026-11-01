# Synapse push logging

Mount `hris_push_logging.py` and `logging.json` read-only into the Synapse process,
add their directory to `PYTHONPATH`, and set the homeserver's `log_config` to the
absolute path of `logging.json`. JSON is a valid YAML logging configuration.
The pinned provider integration tests load these exact two files.

The sole configured output handler uses final-output redaction, including format
arguments, exception chains and stack text. It masks the `hriscp1_` capability
namespace and bounds each rendered record to 16 KiB. Formatting failures emit
only a fixed diagnostic. This is not a general-purpose credential scrubber:
never log request bodies, native FCM tokens, decrypted content or bearer headers.
Keep any added output handler on the same formatter and use bounded container
log retention.

These files do not enable push delivery or create a public callback listener.
Keep managed push disabled until its restricted HTTPS callback, durable receiver
and current-authority worker are deployed. See
[the native push contract](../../docs/collaboration/native-push.md).
