# Repository instructions

Read docs/architecture.md, docs/product.md, and docs/delivery.md before changing boundaries or implementing capabilities.

- Domain contains entities, repository contracts, use cases, and pure policies. No Spring, SQL, Jackson, SDK, or DI annotations in domain.
- Datasources implement contracts, perform raw I/O, return raw values/DTOs, and preserve technical exceptions. Never datasource-to-datasource dependencies or application policy.
- Repositories coordinate datasource contracts and map values/errors into domain Result. Never repository-to-repository dependencies or UI state.
- Use cases coordinate repositories and own policy and transaction boundaries. Never use-case-to-use-case calls. Reuse named pure domain policies where appropriate.
- HTTP controllers and worker adapters invoke use cases. DI belongs in one configuration entry point per module; use constructors. Keep feature endpoints in their module.
- Centralize repeated technical error mapping in named public safe functions. Acquisition and mapping share the boundary. Preserve cancellation/interruption and failure classification. No hidden retry, permission, session, or lifecycle policy in safe functions.
- TransactionRunner must roll back on a failed Result. Business writes, audit, outbox, and idempotency receipts commit atomically where required. External delivery happens after commit.
- Always enforce actor/company/resource scope server-side. RLS context is transaction-local. Migration credentials must not be used by application runtime.
- Preserve stable API error codes and safe unlocalized parameters. Clients translate errors. Mobile commands require idempotency and observed versions; synchronization must handle commit ordering, bounded cursors, deleted data, and revoked access without leaking another account/company partition.
- Keep money decimal and time/zone policy explicit. Final payroll and submitted policy snapshots are immutable; corrections are new records.
- Use Gradle conventions, a version catalog, and type-safe projects accessors. Generated sources stay in build directories. Do not create one module per use case.
- React pages render state and forward actions. Feature controllers own use cases, state, effects, and cancellation. SDK I/O stays in data. Scope caches by account/company and cancel obsolete requests.
- Explicitly own every job, connection, listener, stream, and timer. Use bounded queues/buffers; stop observation on disposal. Never claim performance without measurements.
- Review security, races, resource leaks, and termination for every capability. Bound request sizes, query ranges, queues, retries, recursion, and loop progress. Apply timeouts and cancellation; never retry indefinitely. Exercise competing writes, lost responses, revocation, failure cleanup, and recovery when affected. Keep credentials and personal data out of diagnostics.
- Match Azure Portal shell, command bars, resource tables, and detail panels. Reuse AppXxx components with Fluent semantic tokens. Keep product copy concise.
- Test transactions, isolation, concurrent actions, cancellation, late results, retry, lifecycle, and recovery when changed. Run the relevant checks and report only validation actually performed.
- Use atomic commits including implementation, tests, and docs. No Co-authored-by trailer. Preserve unrelated changes. Push when authorized; releases and live distribution require explicit instructions.
