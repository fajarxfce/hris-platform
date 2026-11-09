# Organization readiness queue repository

The typed queue contract retains explicit MATRIX/PUSH channel,organization,publisher,
lease and complete publication checkpoint. It never substitutes an HR company ID.
Pure domain policy validates nonzero coordinates,PostgreSQL microsecond precision,
finite timestamps,publication expiry,attempt bounds and forward progress. The caller
owns transactions,current-target admission,deadlines and retry policy.

The datasource performs only bounded SQL and returns generated records. Every read
or mutation fences the complete captured identity,revision,timestamps,cursor,attempt
and availability fields. The repository rejects substituted,duplicate or unordered
results inside the same failure boundary as acquisition. Target pages contain at most
eight distinct positions and native parents. Neither a target coordinate nor a
readiness signal grants Matrix,content or push authority.

A successful cursor update must read back exactly the expected next checkpoint with
attempts reset to zero. A successful release must read back the unchanged publication
and cursor,cleared lease,exact availability and valid completion timestamp. Missing,
malformed,failed or interrupted readback fails the owning transaction; cancellation
and interruption propagate. Lost compare-and-set returns false for the owning use
case to handle. Invalid caller bounds/coordinates acquire no datasource.

[Audited validation](../measurements/collaboration-space-readiness-queue-2026-11-09.json)
passes four fresh tests in80.941 seconds,zero failures/errors/skips,on8,716 stable
source inputs. The real PostgreSQL tests verify independent channels,full checkpoint
fencing,exact current targets,and fourteen post-write rollback/cancellation/interruption
variants. Further malformed actual claim and target variants prove mapping isolation
and rollback of rejected claims. Subcases are not counted as additional JUnit cases.
Format and architecture pass. Two earlier smart-cast compilation failures occurred
before tests; the second followed an incomplete scripted edit and is retained.

These eight Kotlin files introduce no DI binding,worker loop,new schema,provider I/O,
Mobile change,SDK build or phone release. The existing V139 storage evidence is
separate and is not rerun or recounted. Next implement bounded current-target Matrix/
FCM fanout,then durable Mobile foreground/background consumption and product routing.
All54 PRD acceptance groups remain open;live schema116.4 and phone87 are unchanged.
