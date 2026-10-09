# Administration

Own effective company policy configuration, module flags, client availability, integration status, audit queries, and operational job controls.

Flags affect capability availability but never grant permissions. Version gates and maintenance policy are explicit. Integration secrets live in deployment secrets, not ordinary settings or Git.

Runtime audit is append-only with actor/reason/resource/correlation. Failed jobs expose safe categories and authorized retry. Backup/restore includes database, content, and encryption-key recovery.

Screens: settings, flags, integration status, audit explorer, job monitor.

Acceptance: policy effective dates, restricted settings/audit access, safe retry, stale client policy, secret redaction, and recovery from backup.

## Client availability and module flags

Versioned company client policy is implemented, including future activation, immutable history, paired build headers, bounded maintenance, and server admission for feature endpoints and sync selections. Settings writes retain idempotent receipts and current permission/recent-authentication checks. [Client policy](../client-policy.md) defines recovery, scheduling, and mobile behavior. These availability controls do not grant permissions or interrupt already admitted transactions/jobs.

Settings reads return the configured head and effective policy in one guarded snapshot, with original/live access and MFA checks after waiting. Named revision reads retain the immutable history contract. The dashboard review screen displays both states and supports named revision navigation with company-scoped cancellation. Configuration editing remains subsequent work.


## Audit search

Company audit metadata search is implemented with current `audit.read`, original/live access and MFA checks, bounded time windows, exact filters, and timestamp/ID pagination. Reasons and change payloads are excluded from the SQL projection. The browser explorer includes UTC filters, bounded URL pagination, and metadata details with keyboard focus restoration. [Audit search](../audit.md) defines continuation, disclosure, and live-history consistency. Detailed audit disclosure and global audit queries remain subsequent work.

## Durable job foundation

Company-scoped job queries and versioned cancellation requests are implemented. Lists expose progress and safe failure codes, with own-job visibility or explicit company-wide access. Interactive use cases recheck live access and MFA after waiting for guards. Available actions come from the original/current permission intersection inside that transaction. Job request inputs and terminal updates are immutable. A cancellation request blocks subsequent progress and successful completion; the feature worker must acknowledge cancellation and release its own reservations.

Cancellation is naturally idempotent: after current authorization, an already requested cancellation returns its current job without changing its cleanup availability. A first request requires the observed version. Clients must distinguish requesting cancellation from a terminal `CANCELLED` status; an interrupted HTTP response requires a status read or an explicit retry, not an assumed rollback.

PostgreSQL leasing is restricted to the `hris_worker_capability` role. Its broader RLS policy applies to the queue only; business tables retain their existing company scope. Application credentials cannot execute the lease function or access the separate Spring Batch metadata schema. A new lease changes the ownership token. Heartbeats, checkpoints, completion, and deferral require the current token and a lease that is still valid according to the database clock. Feature workers must update their business result and checkpoint in one transaction.

Claims allow at most two active jobs per company and four across the deployment, with bounded scans and eight attempts. The leasing use case turns exhausted leases into failed jobs with an atomic audit record; the datasource does not choose retry or cleanup policy. A failed or cancelled business workflow may need a feature-specific recovery action; the queue cannot silently release business reservations. Job inputs and checkpoint JSON are limited to 8 KiB each. The first implemented workload is monthly attendance closing. Its feature endpoint owns submission and its use cases own recovery; no generic arbitrary-job submission endpoint is exposed. Spring Batch runs on a bounded worker pool, with transaction-free tasklet orchestration so feature use cases retain transaction ownership. Non-progressing adapters, stale leases, cancellation, and time budgets stop execution. Shutdown owns and releases timers/threads; completed tasks leave no growing process-local registry.

The metadata schema is based on Spring Batch 6.0.5 and is distributed under Apache-2.0. [Spring Batch source](https://github.com/spring-projects/spring-batch/tree/v6.0.5/spring-batch-core/src/main/resources/org/springframework/batch/core).

Job responses include `progressMode`. `FIXED_TOTAL` jobs must complete every declared item; the database and worker both enforce this. `UPPER_BOUND` jobs can finish when a finite source is exhausted before its safety ceiling. Both modes reject backwards, excessive, or non-progressing intermediate steps. Company document inventory uses the latter mode; no public endpoint accepts arbitrary job definitions.

Leave entitlement batches use `FIXED_TOTAL` jobs with one transaction per employee and one final metadata step. The leave module owns accrual/year-closing submission, immutable outcomes, and explicit recovery. Company job monitoring is shared; no new timer, in-memory task registry, or arbitrary-job API is introduced.
