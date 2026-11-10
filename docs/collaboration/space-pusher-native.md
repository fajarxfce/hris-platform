# Native original managed-pusher verification

Mobile's owning group activation uses the existing SDK client and latest group bearer.
The native fixture registers a fictional push token through the real encrypted native
API before original transfer. The actual Android activation must create exactly one
managed-pusher request. Its published HRIS capability hash and original Matrix identity
must match Synapse, and remain identical after process death and group recovery.

The final run passes two native cases in206.26s, with one2560MiB/two-core emulator,
no concurrent compilation, exact source/APK audits and complete shutdown. Group recovery
has25 native and17 Matrix requests, exactly one managed-pusher request, zero rejected
401/403/server errors, two original provider events and two direct metadata reads.
The explicit idle sweep adds none. The existing original baseline passes separately.
No native token/capability leaves diagnostics; only bounded hashes/counts are observed.

The first original baseline failed before exercising group registration. Its unresolved
message reason was not retained then; later strict metadata diagnostics and first-case
traffic capture are now present. No retry, timing, production or assertion change was
made. The successful successor does not explain that first failure, and the failed
proof store remains available. See [full evidence and remaining limits](../measurements/collaboration-space-pusher-native-2026-11-10.json).

This test-only backend increment does not change shared DI, migrations or live rollout.
It does not test actual FCM/APNs callbacks, simultaneous company/group pushers or visible
notification dedup. Group V140 remains undeployed and all54 PRD acceptance groups open.
