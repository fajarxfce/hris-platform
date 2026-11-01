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

## Private callback listener

Use [push-gateway.nginx.conf](push-gateway.nginx.conf) as the private listener's
`/etc/nginx/nginx.conf`. The tested image is Nginx1.30.6,
`nginx@sha256:0e52bd054126cdc040b1276c0af347aeb02a41b9cfe305d6094767ac30e97949`.
Keep it on the private provider network; no public published port is needed when
Synapse shares that network. Mount these operator-owned files read-only:

- `/etc/nginx/hris-push-upstream.conf`: one reviewed `server hris-api:8080;` line,
  adjusted to the actual private API destination.
- `/etc/nginx/hris-push-sources.conf`: explicit trusted Synapse egress addresses/
  networks using Nginx `allow` directives, followed by `deny all;`.
- `/run/secrets/push-tls.crt` and `/run/secrets/push-tls.key`: a valid chain/key with
  SAN matching the private hostname in `HRIS_MATRIX_PUSH_GATEWAY_URL`.

Provide writable ephemeral `/tmp` for PID/body/proxy buffers and bounded container
logs. TLS keys must be readable only by the service identity. The config uses9443,
TLS1.2/1.3, an8KiB body ceiling,2s body inactivity, fixed proxy destination and
bounded upstream timeouts. Access logs are disabled; credentials/forwarding headers
are removed. Four simultaneous requests per source and20 requests/sec with16 burst
are pilot limits; measure before changing them or claiming production capacity.

Include [push-public-deny.nginx.conf](push-public-deny.nginx.conf) in every public
API server block. Equivalent explicit prefix denial is required with other public
proxies. The private configuration's optional listener directory is empty by
default; the integration fixture adds a public listener there to exercise this
exact denial include. Public Matrix client routes must never expose the push path.

Set `HRIS_MATRIX_PUSH_CALLBACK_SOURCES` to the private proxy's actual API socket
peer addresses/CIDRs, and keep `server.forward-headers-strategy=NONE`. The API's
source check also protects direct access outside the reverse proxy. Its effective
body socket timeout must be1–5000ms. Configure Synapse's `ip_range_whitelist` for
the exact private proxy destination if its standard IP blocklist would reject it.
Trust the private certificate issuer through the normal platform trust store or
an explicit CA bundle (`SSL_CERT_FILE`); never disable verification. The fixed
callback URL must end exactly `/_matrix/push/v1/notify`, without a query/fragment.

The [actual-provider fixture](../../docs/measurements/chat-push-provider-callback-2026-11-01.json)
loads these files with an ephemeral CA and demonstrates private TLS, public denial,
body limits, real outage/retry into the API queue and removal after HRIS parent
revocation. It uses synthetic opaque ciphertext; native SDK decryption is a
separate proof. No production configuration or credential is included.

Keep callback/outgoing push flags off until the durable queue schema, current-
target FCM worker and client receive/recovery paths are ready together. This
configuration alone does not enable chat notifications or deploy Synapse.
