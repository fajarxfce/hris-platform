# Outbound email

`core/mail` provides the domain `MailRepository` port and a Jakarta Mail adapter. Identity uses this boundary for invitations and password recovery. API and worker applications import the same configuration and encryption keyring; only the worker sends queued messages. General communication campaigns remain separate.

Set these deployment secrets on the API and worker:

| Variable | Meaning |
| --- | --- |
| `HRIS_MAIL_ENABLED` | Opt in to SMTP; defaults to `false`. |
| `HRIS_MAIL_HOST`, `HRIS_MAIL_PORT` | Trusted server and port; default port is `587`. |
| `HRIS_MAIL_FROM` | Single sender address permitted by the provider. |
| `HRIS_MAIL_USERNAME`, `HRIS_MAIL_PASSWORD` | Provider credentials; empty values support an explicitly configured relay. |
| `HRIS_MAIL_TRANSPORT` | `STARTTLS` by default, or `TLS` for implicit TLS. |
| `HRIS_MAIL_ALLOW_LOOPBACK_PLAINTEXT` | Only for an explicitly chosen `PLAINTEXT` connection to localhost in development. |

Production transport requires TLS and certificate hostname verification. Debug protocol logging is disabled. Connections have five-second connect, read, and write timeouts. These are individual transport timeouts, not a guarantee that the whole SMTP exchange finishes in five seconds. The identity worker limits active deliveries to two per process, with no waiting executor queue and a thirty-second delivery deadline. Lease ownership lasts two minutes and is fenced on acknowledgement. Polling, tasks, and deadline timers stop on shutdown.

Messages contain one recipient, a bounded subject, and at most 64 KiB of UTF-8 plain text. Header injection, address groups, and oversized envelopes are rejected at the SDK boundary. Each operation supplies a stable message UUID, preserved as `Message-ID`. SMTP acceptance followed by a lost response can still produce a duplicate; recipients of authentication links must enforce single-use tokens independently.

The datasource preserves SDK failures. The repository maps them once to domain failures, distinguishes recipient rejection, permanent message rejection, authentication/configuration errors, and transient transport failures, and propagates cancellation/interruption. Diagnostic output contains only categories, SMTP status codes, and application call frames. It excludes recipients, credentials, body text, and provider error messages. The boundary does not retry or dispatch messages in a background thread.

A transient transport failure is retried at most eight attempts, with delays from five seconds to five minutes and only before the credential expires. Permanent errors stop delivery. A crash after SMTP acceptance but before acknowledgement can cause a duplicate with the same Message-ID; the link remains single-use. Failed acknowledgements never claim successful delivery. Expired challenges and their encrypted queue rows are removed in bounded batches after seven days.

When disabled, the repository returns `mail_not_configured`. No production email provider has been configured or contacted by the integration tests; tests own a loopback SMTP fixture and release its sockets and executor.

`HRIS_PUBLIC_URL` is a trusted HTTPS origin with no path, query, user information, or fragment. Both applications validate it at startup when mail is enabled; request Host headers never generate links. Explicit `HRIS_AUTH_LINKS_ALLOW_LOOPBACK_HTTP=true` permits only loopback HTTP during development. Links carry their token in the URL fragment. The frontend must move it into owned state and clear the fragment before further navigation.

Invitation/recovery tokens contain 256 random bits. The challenge table stores only hashes; the delivery queue temporarily stores the token encrypted with account/challenge-bound associated data. It is erased after delivery, terminal failure, or supersession. Credential changes revoke outstanding links and previous sessions while preserving MFA. See [identity](modules/identity.md) for the API flow.
