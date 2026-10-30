# Document command completion — 30 October 2026

At `7a1c0251`, eleven transaction variants could complete their journal after
interactive assurance expired; four original upload receipts could return after
expiry during ownership acquisition; and chunk preparation could begin storage
I/O after its reservation proof expired. Three retention commands also accepted
an admitted MFA proof when the current account no longer had an enrolled factor.
These nineteen reproductions failed. A twentieth, positive control confirms that
inventory receipt replay already required current recent proof.

Eight use cases now retain their guarded account projection and recheck session
assurance through journal/receipt completion. Inventory and retention retain their
additional recent-authentication requirement. Retention changes, policy saves and
accepted revision retirement also require current enrollment when MFA is enforced.
Upload receipt replay checks after ownership acquisition; chunk preparation checks
after reservation and cleanup registration, before leaving the transaction for I/O.
No dependency, query, schema or transport is added.

`DocumentCommandCompletionTest` uses actual PostgreSQL repositories and advances
the clock after successful acquisition or journaling. It checks unchanged revision
state, retention state/policies, metadata, jobs, receipts, audit and outbox after
failure. Expired preparation leaves no chunk or garbage registration and performs
no object write. A failure after physical upload preserves the previously committed
attempt registration; an explicit retry after its lease can succeed with the same
operation identity and a distinct bounded physical attempt. Fresh assurance returns
the original committed receipt without another journal or storage write.

The first seventeen-case baseline ran in 45 seconds (sixteen failures, one positive
control); the three enrollment reproductions ran in 40 seconds. All twenty corrected
cases passed in 50 seconds, with zero failure, error or skip. Worker execution keeps
its independent credential/lease authority; interactive proof age does not cancel
an already authorized background job.

All 243 API, 18 domain and nine data cases passed, with formatting, architecture
and API/worker packages (416 seconds). Four actual API/browser workflows passed
in 42.5 seconds including setup. Exact counts and scope are recorded in the
[measurement](measurements/document-command-assurance-2026-10-30.json). The private
development stack, accepted Google binding and installed phone remain unchanged.
