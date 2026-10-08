# Outbound email

`core/mail` provides the domain `MailRepository` port and a Jakarta Mail adapter. This boundary is ready for identity/communications workers; invitation, recovery, queue, and notification workflows are tracked separately. API and worker applications do not yet import this configuration.

Import `MailConfiguration` in the application composition when enabling a delivery consumer. Set deployment secrets:

| Variable | Meaning |
| --- | --- |
| `HRIS_MAIL_ENABLED` | Opt in to SMTP; defaults to `false`. |
| `HRIS_MAIL_HOST`, `HRIS_MAIL_PORT` | Trusted server and port; default port is `587`. |
| `HRIS_MAIL_FROM` | Single sender address permitted by the provider. |
| `HRIS_MAIL_USERNAME`, `HRIS_MAIL_PASSWORD` | Provider credentials; empty values support an explicitly configured relay. |
| `HRIS_MAIL_TRANSPORT` | `STARTTLS` by default, or `TLS` for implicit TLS. |
| `HRIS_MAIL_ALLOW_LOOPBACK_PLAINTEXT` | Only for an explicitly chosen `PLAINTEXT` connection to localhost in development. |

Production transport requires TLS and certificate hostname verification. Debug protocol logging is disabled. Connections have five-second connect, read, and write timeouts. These are individual transport timeouts, not a guarantee that the whole SMTP exchange finishes in five seconds. The consuming worker must own its concurrency, deadline, retry, and shutdown policy.

Messages contain one recipient, a bounded subject, and at most 64 KiB of UTF-8 plain text. Header injection, address groups, and oversized envelopes are rejected at the SDK boundary. Each operation supplies a stable message UUID, preserved as `Message-ID`. SMTP acceptance followed by a lost response can still produce a duplicate; recipients of authentication links must enforce single-use tokens independently.

The datasource preserves SDK failures. The repository maps them once to domain failures, distinguishes recipient rejection, permanent message rejection, authentication/configuration errors, and transient transport failures, and propagates cancellation/interruption. Diagnostic output contains only categories, SMTP status codes, and application call frames. It excludes recipients, credentials, body text, and provider error messages. The boundary does not retry or dispatch messages in a background thread.

When disabled, the repository returns `mail_not_configured`. No production email provider has been configured or contacted by the integration tests; tests own a loopback SMTP fixture and release its sockets and executor.
