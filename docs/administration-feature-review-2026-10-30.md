# Administration requirement review

All five initial requirements and six original detailed sections have matching
implementation and behavior evidence. The new [completion-assurance correction](administration-assurance-2026-10-30.md)
is locally verified; final source CI remains pending before closing the five
inventory rows. This review does not establish completion of other modules.

| Initial requirement | Implementation and verification |
| --- | --- |
| `administration.01` | Versioned policy repository/use cases expose configured/effective settings and immutable revisions. Separate public availability, global integration configuration/worker reports, company/global audit and company job use cases preserve their distinct authority. `ClientPolicyHttpTest`, settings/access/sync suites, integration/audit suites and job tests cover the actual SQL/API contracts. |
| `administration.02` | `ClientAvailabilityPolicy` evaluates build/module/maintenance admission without granting permissions. `ClientPolicySyncHttpTest` rejects disabled collection selections and retains valid cursors when restored. Public policy omits future revisions and editor reasons. Configuration acquires six API enablement flags; worker observations contain four startup flags and database timestamps, with no provider identifiers or credentials. Integration API/repository and actual browser tests verify disclosure and independent revocation. |
| `administration.03` | Journal writes are transactional and append-only; company search selects metadata without acquiring reasons/change payloads. V99/V100 retain separate global metadata/account-access projections. Job leases are fenced, bounded and worker-only, with safe failures and feature-owned explicit recovery. `JobQueueHttpTest` verifies competing leases, expiry, cancellation, attempt ceilings and rollback; real recovery evidence restores database, exact objects and old/current encryption keys. |
| `administration.04` | Routed settings review/editor, module switches, platform integrations, company/global audit explorer and job monitor are implemented. The actual policy/job browser workflow exercises scheduled versus effective values, lost-response receipt recovery, maintenance/build recovery and confirmed cancellation. The cumulative identity browser scenario exercises real company audit filters/details; the integration scenarios cover global-only configuration and advancing actual worker reports. |
| `administration.05` | Policy activation/supersession and original receipts are covered by the policy HTTP suite. Access/command regressions cover original/live grants, current enrollment, expired proof, cancellation and rollback. Repository projections enforce redaction; recovery verification uses independently restored content/keys, rejects a wrong retired key and preserves source independence. |

## Detailed contract coverage

| Original section | Evidence and scope |
| --- | --- |
| Reference workload | [Recorded measurements](workload.md) cover 5,000 arrivals/20,000 requests, 5,000 homogeneous monthly payroll results, and two 100-MiB documents with bounded resources and malicious-file rejection. The retained dates, corpus and shared-host limits remain explicit; this review makes no new performance measurement. |
| Backup and recovery | [Recovery sets](recovery-set.md) quiesce writers and independently restore the database, Garage inventory/content and separate encryption keys. Metadata source `292a30f1` passed schema-103 recovery in full CI `41043798660`; the private runtime also retains its verified pre-migration schema-101 checkpoint. Restore verification is distinct from deployment cutover/RPO/RTO. |
| Integration configuration | `GetIntegrationConfiguration` and `GetWorkerIntegrationRegistry` check original/current global authority and final MFA. The reporter owns bounded serial publication/pruning; database constraints keep startup flags immutable, five-minute retention and at most 64 identities. Actual worker/browser acceptance observes advancing timestamps and a separate fictional stale process. Flags/reports do not imply provider health or delivery. |
| Client availability and module flags | Settings use a shared snapshot guard, future activation and optimistic replacement with immutable receipt recovery. Public discovery remains usable during blocked business admission. Twelve completion cases and prior policy/access/sync tests verify final proof, current enrollment, ordinary recovery, original receipts, foreign scope and interruption. |
| Audit search | Company/global queries use bounded windows, exact filters and immutable cursor compatibility. Source projections do not acquire reason/change payloads. Current access and final MFA apply before releasing pages. Company/global/account audit tests verify row scope, redaction, stable pagination, cancellation, credential/grant changes and historical account values. |
| Durable job foundation | Read/cancellation use cases preserve own-job cleanup versus company-wide authority and final assurance. Fixed-total/upper-bound progress, attempt/concurrency limits, immutable inputs, lease tokens and database-clock expiry are enforced. The actual policy/job scenario distinguishes cancellation requested from terminal cancellation. Worker suites verify execution, cleanup and feature-owned recovery without an arbitrary-job API. |

## Evidence and remaining gate

[Correction measurements](measurements/administration-assurance-2026-10-30.json)
retain baseline classification, exact local commands, per-suite cases and package
hashes. The selected 81 API cases include all changed use cases and unchanged
integration/global-audit/job controls. Both runtime application contexts pass,
including all 104 ordinary worker cases; the separately accepted elapsed-retention
case remains an explicit skip. Dashboard source is unchanged by the correction.

The source commit must pass all ten Platform CI jobs, including full dashboard
integration and isolated recovery. Until then the initial audit rows remain
unverified for final-source acceptance. The next modules are Reporting, Payroll
and Workforce, followed by the complete Mobile/applicable Starter review.
