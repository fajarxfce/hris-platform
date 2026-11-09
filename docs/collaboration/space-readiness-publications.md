# Atomic organization readiness publication storage

V139 captures `collaboration.space_device_keys_published` in the existing journal
transaction. Verified key publication, audit, commit-ordered revision and separate
MATRIX/PUSH rows commit or roll back together. Both the original-device completion
worker and fresh-device SDK upload use that existing journal. There is no new client
request, token refresh, key generation, provider call or historical backfill.

The trigger obtains actual organization/account/device/publication coordinates from
the confirmed key row; it does not trust outbox JSON or an HR company identifier.
One immutable organization exists per installation. Repeated journal events retain
the original two rows and revision. Revision allocation holds the final shared
space-counter lock until source commit. Account/native/device/key guards already
belong to the calling transaction; post-journal validation reacquires only those
same guards. A rollback does not consume a revision.

Each channel owns its cursor, attempt count, lease and completion. Claims take at
most four rows, with at most eight active leases per channel. Leases last at most
60 seconds; publication expiry is exactly24 hours after the verified publication.
Eight attempts without progress are terminal; a strictly forward target cursor
resets that counter. Release can defer at most300 seconds. Old tokens, completed
rows, backwards cursors and another publisher's scope cannot change progress.
The worker claim transaction must end before acquiring target account guards.

The security-barrier target view requires worker capability, a current exact lease
and the publisher's organization scope. It returns public target account/device/
native-parent coordinates through the existing receiving-readiness projection.
Current confirmed keys, hard parent lifetime, membership/security and original
completion still gate eligibility; a short interactive-access expiry does not
remove receiving readiness. A direct room and receiver publication must predate the
captured time. Receiver publication revision must also be earlier: an older key
observation timestamp cannot admit a transaction committed later. Missing captured
receiver rows represent pre-V139 keys; production first publication and journal
are atomic. This view is a bounded hint inventory, not an MVCC history snapshot or
content authority. New-room/startup paths independently observe current readiness.

Capture and projection have distinct nonlogin, non-bypass roles. Neither can read
outbox payload, key fingerprint or provider credential columns. FORCE RLS excludes
ordinary API access even with broad table grants. Each target still needs fresh
native/device/provider or push admission before an owning fanout use case delivers.

Recovery accepts reviewed schema139 without changing any encrypted-envelope query.
An isolated normal-role test restores the actual PostgreSQL dump and reads back the
exact retained channel/cursor/lease data. Live recovery/deployment is not implied.

[Audited validation](../measurements/collaboration-space-readiness-publications-2026-11-09.json)
passes23 fresh JVM cases:10 actual PostgreSQL publication/queue tests,11 original/
fresh activation and rollback tests, and2 native API key-publication tests. There
are zero failures, errors or skips, plus40 Python migration/recovery cases. Format
and architecture pass on8,708 unchanged source inputs. The receiver commit-order
regression first fails on the incorrect timestamp-only projection, then passes
with the revision guard. Both prior positive gates and that expected failure are
retained separately; cases are not added twice. Shared DI is unchanged.

Typed repository readback, bounded current-target fanout, Mobile durable consumption/
background ownership and product routing remain next. No live rollout, SDK change,
new APK or Starter port. Live schema116.4 and phone87 stay unchanged; group routing
is OFF and all54 communications acceptance groups remain open.
