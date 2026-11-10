# Atomic binding producer native evidence

Mobile [d89e6b5d](https://github.com/fajarxfce/hris-mobile/commit/d89e6b5dcb0c0463916628ecd10a9bae946e5a1a) retains new
room bindings and fresh recovery work in one database transaction, then requests
bounded scheduling after exact readback. Existing local bindings create no new work.
[Owning contract](https://github.com/fajarxfce/hris-mobile/blob/d89e6b5dcb0c0463916628ecd10a9bae946e5a1a/docs/collaboration-space-binding-producer-wake.md).

Both existing native scenarios pass in 193.275 seconds using the exact new proof APK.
The fixture now checks a fresh wake and one scheduler request for a new binding, and
unchanged wake/request count for retained bindings. Original callback-loss, hard process
death, original transaction continuity, group control/claim, pusher and idle checks remain.
Original recovery has two provider events and 10 native/11 Matrix requests; group has
two events,25 native/17 Matrix requests,one pusher,no rejection/server error,and zero
additional idle direct transport requests. The receiver stays offline for delivery.

The 2,560 MiB/two-core isolated emulator is stopped. Sources/APK remained unchanged;
no compilation ran alongside it. Backend source is unchanged, so the exact accepted
receipt-recovery source-2 compilation was reused. No backend DI/schema, SDK artifact,
live flag, product release or phone change occurred. Manual fixture scheduling does
not establish cloud-push delivery or physical-phone latency.
[Evidence](../measurements/collaboration-space-binding-producer-wake-native-2026-11-10.json).

Generic Apple verification, the Starter port, new SPACE message enqueue, mixed-ledger
reconciliation and complete incoming history remain separate work. All54 communications
acceptance groups remain open.
