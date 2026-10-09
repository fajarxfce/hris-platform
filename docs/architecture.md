# Architecture

## Deployment and modules

One modular monolith serves a single organization group with multiple companies. API and worker roles share code and PostgreSQL. The React application consumes the same versioned API as the future Compose client.

Business modules own domain, data, and delivery projects. Domain publishes entities, repository contracts, use cases, and pure policy without framework dependencies. Data implements datasource contracts, mapping, repositories, and DI. Delivery owns HTTP and job entry points. The application composes modules.

Use cases may depend on repository contracts from other modules. Datasources never depend on datasources, repositories never invoke repositories, and use cases never invoke use cases. Shared approval transitions are pure policies, not a universal business orchestrator.

Workforce owns schedules and factual attendance; leave owns absence decisions. Reporting and payroll combine those facts. Identity uses stable company/person identifiers without depending on their implementation. These directions prevent cycles. Payroll owns scoped read projections for immutable workforce evidence. Its datasources read raw records; repositories map them into payroll domain references. The originating module retains write ownership. This permits payroll to consume factual snapshots without importing another module’s data implementation or moving permission/period policy into a datasource.

## Runtime

JDK 21, Spring Boot 4.1, Kotlin 2.3, Spring MVC, PostgreSQL 18, Flyway, and jOOQ. Gradle conventions own toolchain and quality configuration; the version catalog owns versions. jOOQ is generated from migrations against PostgreSQL. OpenAPI is reviewed and validated in CI.

## Transactions and failures

Domain Result carries typed failures with stable codes, field codes, and safe unlocalized parameters. HTTP adapters expose one RFC 9457 problem contract; client applications own translation and retry decisions. Stateless safeDatabaseCall and storage/SDK-specific boundaries map technical exceptions once, preserve interruption/cancellation, and do not retry. False/null outcomes have operation-specific semantics.

TransactionRunner is a domain port implemented using Spring transactions. A failed Result marks rollback. Request-scoped company context is installed using SET LOCAL and does not leak through pooled connections. Runtime credentials cannot bypass RLS; migration credentials are separate. Business transactions set local query (15 seconds), lock (5 seconds), and idle-transaction (30 seconds) timeouts with a 30-second transaction budget. A late interruption before commit rolls back and propagates cancellation. Mapping failures become sanitized data failures; internal diagnostics retain SQLState and application call frames, never SQL text or bound values. Framework batch-error logging that includes SQL is disabled in both application roles. HTTP diagnostics retain a bounded cause-category chain and application frames without exception messages; wrapped cancellation/interruption propagates.

An explicitly injected CrossCompanyTransactionRunner supports atomic employment transfers. It installs one secondary company for a bounded transaction, with RLS policies limited to transfer inputs, employment writes, and audit/outbox inserts. Ordinary transactions explicitly clear that secondary scope. Transfer use cases lock companies/memberships in stable order and recheck live access to both companies before mutation or replay. This is not a global RLS bypass.

When a workflow needs both organization structure and company guards, acquire structure first. Employment transfers take the ordered people guards, destination structure, then ordered company/membership guards. Organization changes use the same structure-before-company order; a transfer must not hold a company guard while waiting on a structure writer that needs that company.

Lifecycle case operations acquire people guards before case locks, followed by company, membership, and ordered account guards. Template operations acquire the template guard before access guards. Shared case reads cannot hold access guards while waiting for a writer that needs them. Multi-query aggregate reads need their resource guard; paginated case/task projections instead use one bounded SQL snapshot.

Business changes, audit, outbox, and idempotency receipts share their transaction. Receipt keys bind company or account scope, actor, operation, and payload hash. Account receipts cannot be read through a company scope or by another actor. Repeated identical commands return the committed outcome; key/payload mismatches fail. Optimistic versions reject obsolete updates. A lost response never implies that the transaction rolled back.

## Security

Account, person, employment, company membership, and role assignment are separate concepts. An account can have multiple company memberships. Initial binding to an existing person requires an independent authorized operator and an active company membership. Account row locks protecting credential/access changes use `FOR NO KEY UPDATE` where identity keys remain immutable, so unrelated foreign-key history writes can proceed without a reversed lock dependency. Multi-account commands acquire account IDs in a consistent order. Approval mutation guards follow business-resource locks and precede company, membership, and account guards. Server-side permission includes company and resource scope; permission to administer users does not automatically grant payroll access.

Web uses HttpOnly Secure session cookies and CSRF protection. Mobile uses short-lived access tokens with rotated refresh tokens. OIDC uses verified issuer/subject; provider claims are never trusted from client decoding. Privileged accounts require MFA and sensitive operations require recent authentication. Revalidating an already resolved request may remove permissions but must not add newly granted capabilities without a new request and its assurance checks. The shared company-command access policy applies this intersection; background jobs resolve execution authority separately.

## Storage and jobs

Private S3-compatible object storage holds documents; the self-hosted distribution uses Garage. Uploads are resumable and bounded, downloads support Range/ETag, and documents are not readable until validation completes. Metadata and authorization stay in PostgreSQL.

Spring Batch provides persistent checkpoints for payroll/import/export. An outbox dispatches external delivery after commit. Jobs are idempotent, bounded, interruptible, and owned by the application lifecycle. Retries are explicit and failures stay inspectable. User-facing job use cases coordinate identity and company repository contracts for live access checks. Cancellation locks the job before company/member/account guards, matching worker lock order. Global worker leasing remains a separate capability boundary.

## API and clients

/api/v1 is the public prefix; company resources use /companies/{companyId}. OpenAPI describes DTOs, error codes, pagination, optimistic versions, and idempotency. Money uses decimal strings; timestamps use UTC with explicit IANA schedule zones. Default/max list sizes are 50/200.

The mobile change feed is scoped by actor/company/permission and initially covers owned expense claims, leave requests/balances, overtime requests, and finalized payslips. Storage triggers capture raw invalidations atomically; a bounded worker publishes positions after commit. Sync use cases hold shared people/company/membership/account guards and a shared publication-head guard for one page, validate live scope, and issue encrypted finite cursors. Datasources do not resolve access policy. Commands carry stable operation IDs; offline attendance remains pending verification. Cursor invalidation and access revocation require replacing the corresponding client partition. See [synchronization](synchronization.md) for pagination, retention, key rotation, and client recovery.

## Frontend

Feature data adapters map transport DTOs. Pure use cases own application policy. Feature controllers/hooks coordinate state, events, effects, and cancellation. Pages render state. TanStack Query keys include account and company; navigation cancels obsolete work. React Hook Form owns form mechanics; validation affecting business remains in the domain/backend.

## Verification

Compile-time project boundaries and architecture checks complement behavior tests. PostgreSQL integration tests cover actual RLS, transactions, constraints, migrations, concurrent writes, and rollback. Payroll uses official golden fixtures and immutable rule versions. UI tests exercise company switching, permissions, lifecycle, keyboard focus, and screenshots. Performance is measured against the documented budget.

Business document references belong to the documents domain contract and are registered by consuming use cases in their business transaction. Consumers hold the company document guard before registering evidence; retention must hold that same guard when checking references and retiring content. The registry does not depend on consuming repositories. Mandatory consumer references also have database constraints, so forgetting registration cannot produce an unprotected committed receipt.

Document retention has its own versioned metadata aggregate. Use cases coordinate retention, document, identity, and audit repositories; no datasource or repository invokes another peer. Policy snapshots and lifecycle evidence are immutable. Upload and retention commands share the company document guard, then lock company, membership, and account access in that order. Archive requires explicit cancellation of unfinished processing rather than hiding that orchestration inside storage.
