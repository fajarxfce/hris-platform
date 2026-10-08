# Delivery and validation

## Status

- Repository and product/architecture specification: initialized.
- Backend foundation: implemented Gradle conventions, PostgreSQL migrations/jOOQ generation, transaction/error boundaries, company RLS, immutable audit/outbox storage, correlation IDs, CI, and local container configuration. Identity and job processing are separate pending slices.
- Identity: password sign-in, persistent cookie sessions, CSRF/session rotation, bootstrap, current-company permissions, revocation checks, and credential-race handling implemented. Company member administration is implemented, including versioned grants/revocation and sensitive-grant restrictions. MFA, OIDC, native tokens, invitations, and configurable role templates remain planned.
- Organization and people: company setup, organization units, employee onboarding, scoped directories, and effective employment revisions implemented. Profile editing, lifecycle checklists, import, and cross-company transfer remain planned.
- Approvals: effective templates, immutable snapshots, assignment/delegation, inbox, optimistic decision storage, and pure transitions implemented. Feature-specific business decisions remain planned.
- Workforce and leave: planned.
- Documents and expenses: planned.
- Payroll: planned.
- Communications, reporting, and administration: planned.
- Azure-style dashboard: planned.
- Compose employee application integration: subsequent phase.

Implementation status must reflect executable behavior and observed checks, not scaffolding or empty routes.

## Sequence

1. Repository, license, specifications, architecture rules, and initial contracts.
2. Gradle conventions, Spring Boot, PostgreSQL/Flyway/jOOQ, transactions/errors, audit/outbox, tests, and local deployment.
3. Authentication, company isolation/permissions, organization, people, history/import.
4. Approval policy, roster/attendance/offline verification/overtime, and leave ledger.
5. Resumable documents, claim policy, and payment reconciliation.
6. Payroll rule packs, durable calculation, review/finalization, payslips, payment exports/adjustments.
7. Communication, reporting, flags/availability, and operational controls.
8. Dashboard shell and module workflows.
9. Compose integration against stable API contracts.

## Gates

Use domain tests, architecture checks, PostgreSQL integration tests, transport contracts, job/retry/recovery tests, and UI E2E. Reproduce races with controlled synchronization, not sleeps. Include double submission, late results, lost response, access revocation, overnight/DST calendars, and transaction rollback.

Payroll acceptance includes sourced statutory golden cases, rule-version selection, deterministic rounding, restart checkpoints, concurrent finalization, historical immutability, and no duplicate payment export. Configured rules do not establish legal validation until those fixtures are reviewed.

Performance targets on a 4-vCPU/16-GiB reference deployment: interactive API p95 below one second during the attendance scenario; a 5,000-employee payroll below ten minutes; bounded file/worker memory. Record actual workload, measurements, and limitations. These are targets, not current results.

Commits are atomic and include relevant tests/docs. No co-author trailers. Push increments; releases and production deployment require explicit instructions. Documentation and demo data must not contain secrets or real employee data.

## Observed validation

Backend foundation: Gradle check and bootJar passed; six PostgreSQL integration tests passed, covering pooled company isolation, denied writes, typed/raw SQL failure mapping, failed-result rollback, cancellation rollback, and immutable audit records. Compose configuration and actionlint passed. No business module, deployment, or performance benchmark is claimed by these checks.

Identity password/session slice: full Gradle check and bootJar passed. Four domain tests and four HTTP/PostgreSQL tests passed alongside the six database tests (14 total). HTTP tests verify persisted sessions, session/CSRF rotation, logout, company isolation, and membership revocation. No MFA, OIDC, or native-token validation is claimed yet.

Company setup slice: full Gradle check and bootJar passed (17 tests total). Three additional HTTP/PostgreSQL tests verify concurrent create/replay, payload mismatch, no automatic payroll grant, stale updates, validation, and rollback without consuming failed operation IDs. Test application contexts close after each suite.

Organization/people slice: full Gradle check and bootJar passed (25 tests total). Five additional HTTP/PostgreSQL tests cover history immutability/as-of reads, stale edits, replay after reference archival, cross-company reference rejection, concurrent employee numbers without orphan people, organization cycles, and team/self resource scope. Three pure policy tests cover reporting cycles across future effective boundaries. Shared HTTP fixtures own bounded clients and close application contexts between database lifetimes.

Approval/access slice: full Gradle check and bootJar passed (36 tests total). Six policy tests cover blocked stages, author/beneficiary exclusion, delegation expiry and source-access revocation, ambiguous policy selection, and bounded monetary input. Five HTTP/PostgreSQL tests cover submitted snapshots, cross-company reads, competing decisions with one audit, idempotent reassignment, delegation ownership/revocation, and denied sensitive self grants. The inbox uses typed jOOQ timestamp parameters. Business ledger/payroll consequences are not claimed by these tests.
