# Organization request ownership

V127 adds operational leases for group gateway I/O and native provider renewal.
These leases have separate organization ownership. They do not reopen original
company runtime slots, transfer SDK files or grant provider/room authority. The
feature still has no product caller or DI binding at this storage increment.

## Bounds and current authority

Each exact space/account/device/original-native parent has seven ordinary gateway
slots (1–7) and one reserved provider-renewal slot (0). Gateway positions also pin
the gateway ID/version. The database permits at most60 seconds per lease; use cases
must clip that ceiling to their original admitted request deadline. A future renewal
caller must serialize candidate login through the reserved slot while ordinary
requests remain independently bounded.

The claim function serializes on the group device and recomputes time after waiting.
New claims require an active device, current bound native access/security and current
group eligibility. Gateway claims additionally require the exact live gateway head
and a deadline inside its access/hard lifetime. A busy or waited-out slot returns no
lease. No retry is hidden in the database function, source or repository.

Callers acquire account, native parent, group device, optional gateway and lease in
that order. They commit before provider I/O. They must revalidate all current
application authority and the exact original lease before and after I/O, including
failed responses and cancellation. Metadata or a successful claim is not an access
grant. The original company runtime remains DRAINING after completed handover.

## Replacement, release and cleanup

A slot can be reclaimed only after real expiry and with a different request token.
Existing scope, slot and purpose are immutable while the row exists. An exact release
compares owner/native/slot/token, purpose, gateway position and both timestamps. A late
callback cannot release a newer request in the same slot. Ordinary cleanup may delete
operational rows; it never changes device, provider, gateway or key provenance.

Owned request observations and release remain possible after eligibility loss or
native-parent purge. No native-session foreign key keeps that parent alive. Worker
RLS can read, lock and delete only expired rows; its lock policy cannot publish an
UPDATE. Cleanup uses a finite1–100-row ordered SKIP LOCKED batch with both the supplied
cutoff and database time. It takes no reverse device/native/gateway guard.

## Typed storage

Pure owner/position/kind/lease entities and the request repository carry explicit
coordinates. The raw source performs SQL and preserves technical exceptions. Mapping
and acquisition share safeDatabaseCall. The stored repository rejects malformed
scope, purpose/gateway combinations, slot bounds, nil ownership/token IDs, timestamp
precision and chronology. A claim must match its requested position/deadline and exact
post-write readback. Failed results roll back through the caller's TransactionRunner;
cancellation/interruption propagate. Release readback rejects a falsely acknowledged
old request while preserving a successor. Cleanup rejects out-of-range returned counts.

Recovery supports schema127 and rejects future128. This table has no encrypted
credential or new recovery envelope; the encrypted projection stays identical to126.
Actual validation and source hashes are recorded with the accepted storage increment.

[Storage evidence](../measurements/collaboration-space-request-storage-2026-11-04.json)
records six focused PostgreSQL cases (92.446s), then all76 database and five typed-data
cases (679.944s), with8,333 unchanged final inputs and format/architecture checks.
Recovery17 passes0.012s. The initial compile correction fixes only JDBC test map
nullability. No provider, API/worker DI or Mobile runtime acceptance is claimed here.

## Remaining integration

No current gateway exchange, native provider renewal, request maintenance scheduling
or Mobile adoption is enabled here. Native provider renewal must recover an expired
provider head before bootstrap, because current group issuance requires live metadata.
Retain original Matrix identity/creation time, perform login outside SQL and use final
admission plus generation CAS. Losing candidates expire; never logout a retained
Matrix device. Add group gateway replay cleanup as well as expired request cleanup.

Then implement room/member authority and Mobile provider-key preflight, handover and
ownership across inbox/outbox/drafts/read/history/push/deep links. Expired gateway
replay alone does not justify removing local history or changing SDK namespaces.
All54 communications acceptance groups remain open.
