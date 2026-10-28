# Private deployment record template

Copy this template outside Git. Record references and safe observations, not env
values, provider credentials, private documents, raw runtime logs or recovery keys.
Use [deployment.md](deployment.md) for the procedure and decision gates.

| Decision/input | Reviewed value |
| --- | --- |
| Target host, stable Compose project, existing volume identities | Required |
| Operator and acceptance owner | Required |
| Approved UTC maintenance window and scope | Required |
| Old/candidate full commit IDs, API/worker image IDs, dashboard bundle hashes | Required |
| Exact Compose files and private configuration/credential references | Required |
| Canonical HTTPS origin, proxy/TLS maintenance/reopen procedure | Required |
| Runtime/provider capabilities and approved canary accounts/recipients | Required |
| Recovery data/key custody and new final-checkpoint locations | Required |
| Actual target dataset size and available restore disk/RAM | Required |
| RPO/RTO targets and latest measured recovery duration | Unmeasured until recorded |
| Migration changes, prior-image compatibility and rollback/restore decision | Required |
| Target-specific live restore procedure and external-delivery reconciliation | Required |

| Gate | UTC time, evidence and outcome |
| --- | --- |
| Candidate checks/artifacts retained; minimum configuration review | Pending |
| Target-sized isolated restore and migration rehearsal | Pending |
| Edge maintenance verified; direct writers paused; normal service exits | Pending |
| Final database/content/key checkpoint and isolated verification | Pending |
| Candidate migrations and expected role/constraint/schema checks | Pending |
| Dependencies/API startup and approved frontend/account/module canaries | Pending |
| Worker startup, actual job progress and required provider/device acceptance | Pending |
| Acceptance-owner decision and ingress reopening | Pending |
| Observed outage/restore time, backup retention and post-window review | Pending |

If a gate fails, record the state of migrations, accepted writes and external
delivery before selecting rollback/repair. Keep unperformed tests marked pending.
Record any accepted exception with its owner and follow-up; do not replace a failed
observation with a passing configuration flag.
