# Actual original-queue recovery through HRIS authentication replacement

[Evidence](../measurements/chat-queue-recovery-2026-11-05.json) binds the actual native
APK and both repositories' inputs. [Mobile implementation and limits](https://github.com/fajarxfce/hris-mobile/blob/745e5a0256904453992e5e864bdd59e373de9513/docs/collaboration-chat-queue-recovery.md).

NativeCollaborationSdkAuthInteropTest now creates an owned encrypted peer room and
injects one M_FORBIDDEN send rejection through the test-only gateway trace. The
product outbox retains its original Staged transaction. The fixture then rotates
the same gateway; actual SDK refresh rejection is observed and the product runtime
replaces credentials after draining its original owner. Keys and queue namespace
remain unchanged.

The owned proof removes only the new queue namespace field from its own protected
record to reproduce the pre-fix shape. The product use cases must stage a new
message while the original remains pending, recover the exact old transaction and
receive both encrypted messages at the peer. Receipt bindings remain attached to
their original application records. Server assertions require exactly two accepted
sends, one injected rejection and zero premature sends. Native/device/parent/key
publication guards remain asserted. This fixture never changes the employee app's
protected records.

The driver now requires queueOperationPreserved, legacyQueueRecovered,
oldAndNewEncrypted and retainedApiFailureRetried in addition to all prior auth
flags. The paired Mobile probe accepts older fixtures but does not assert these
new flags without the new scenario. The new driver cannot silently accept that
older coverage.

Both native suites pass165.743s/two cases/zero skips, with12,281 unchanged source
inputs and unchanged APK02269c9531c519579d334e25af2ea1caa58fb2cfa81f3e6a1ac284fb3961bb6e.
The second suite retains quoted replies, original transaction recovery after process
death, receipts and cold history with the gateway unreachable. Driver31 tests pass.
Owned emulator stop and transport absence verified. No production backend change
or rollout follows from this test-only commit. Live source11a98115/schema116 and
phone33 are unchanged; release34 delivery and physical Bima acceptance remain next.
