# Original callback-loss recovery on Android

Latest extension: [mixed Company/Space native acceptance](mixed-space-native.md) retains
the original-only and group-worker controls and adds one independently owned Space original.

Mobile [76a143b6](https://github.com/fajarxfce/hris-mobile/blob/76a143b6c8fc81c6920658797f127dcf4a92e7b5/docs/collaboration-original-lost-callback.md)
repairs a disappeared original from exact local history or one original SDK window.
The preserved earlier failure had two app commands, one receipt and zero SDK originals;
SDK removal precedes the asynchronous app journal callback. Recovery retains physical
SDK identity, keys and transactions and does not enqueue another message.

This backend commit changes native test fixtures and drivers only. The original test
holds the second encrypted send until the first app receipt is durable. It then waits
for both exact SDK rows to disappear while only one app receipt exists and kills the
owned process. After recovery, both original transactions have receipts and Sent
projections; the provider retains exactly two events. A separate offline process reads
both records before transport. Recovery took544ms in this fictional fixture only.

The retained group test still requires actual control, replay dedup, a durable page
claim before delivery, original pusher retention and recovery without another control
or FCM. It produces two events,25 native/17 Matrix requests,one managed-pusher request,
no401/403/server errors and no added direct transport in a fresh idle sweep.

Source22157 passes25.556s with8,734 stable inputs;15 Python driver cases pass0.006s.
Native93669 passes208.523s,two cases,zero failures/errors/skips. Source/APK and the
immutable SDK6 library match; no compiler ran during the2560MiB/two-core emulator,
which is stopped. Mobile full validation covers6,338 cases,4,521 fresh+1,817 exact
reused,with70 focused cases. [Measurement](../measurements/collaboration-space-original-receipt-recovery-native-2026-11-10.json).

The first successor passed original recovery but the group handshake timed out:
the shared driver waited for the original-only first-ACK marker before relaying group
listening/control. Each scenario now owns its interruption checkpoint. Both strong
assertions remain; the same Mobile APK passes. Failed userdata remains preserved.
No production backend,shared DI,schema,live deployment,cloud callback or phone change.
Complete group incoming/older history,producer wakes,rich media,backup,calls and
meetings remain open; all54 PRD acceptance groups remain open.
