# Actual Android product FCM acceptance

Status: fixture and Mobile1136fb9e product activation implemented; passing actual FCM
execution remains pending. Live chat
push stays disabled. This gate follows the accepted encrypted notification input,
reviewed reply and Android application host implementations in HRIS Mobile.

The opt-in `ProductChatFcmInteropTest` owns a disposable PostgreSQL database, private
Synapse, verified TLS push proxy and real worker under a restricted database role.
It creates fictional HRIS accounts and a direct conversation. The product devDebug
APK uses native password login against this isolated API, then enables notifications
and chat through its normal UI. The separate protocol APK owns the encrypted sender.
No product test endpoint or sign-in bypass is added.

The driver starts emulator-5562 from a disposable copy of an explicit source image,
checks AVD/ABI/package/debug mode and uses owned reverse mappings. It cannot select
a phone. The product fixture APK uses `http://127.0.0.1:18084/api/v1/`; never install
it on the user's phone. The driver attempts bounded cleanup of its data/image, SDK peer and owned processes
even when setup fails; acceptance requires both cleanup results to be true. The original image and live HRIS/Matrix/Firebase account remain intact.

After setup, ordinary process death must leave the product stopped. The peer sends
an encrypted original through the real HRIS gateway. Synapse must invoke the private
callback, the worker must deliver FCM, and Android's new process-start event must
identify Firebase's receiver or the app messaging service. A periodic worker restart
alone is insufficient. The actual notification must expose Reply; real SystemUI input
must return an encrypted reply with a logical ID to the peer. Capture and inspect the
notification, IME and acknowledged state before accepting. Record exact APK/source
hashes, provider results, server counts and cleanup. This is one bounded scenario,
not whole feature/capacity/Apple/web acceptance.

Explicit inputs are `HRIS_COLLAB_PRODUCT_FCM_APK`, `HRIS_COLLAB_NATIVE_APK`,
`HRIS_COLLAB_PRODUCT_WORKER_JAR`, `HRIS_COLLAB_PRODUCT_SOURCE_IMAGE`, `ANDROID_HOME`,
`HRIS_TEST_FCM_PROJECT_ID` and `HRIS_TEST_FCM_CREDENTIALS_FILE`. Credentials stay in a
private bounded file; never print their contents or upload them to GitHub. Pass the
configuration after `runuser`/`sudo`, which may filter inherited variables. Execute
`:apps:server:test --rerun --tests '*ProductChatFcmInteropTest'`: the task-specific
`--rerun` prevents reusing an earlier opt-out/skipped report. Acceptance requires one
fresh executed case, zero skips/failures/errors, the driver's complete result, and
independent artifact/visual review. Compile-only and skipped runs never pass this gate.

## Current fixture debugging evidence — 3 November 2026

No real message/FCM delivery has passed this gate yet. Run1 was skipped because
sudo filtered the opt-in; run2 reused the skipped test report. Both are rejected.
Run3 reached login but selected a Compose page title instead of the button's
clickable ancestor. Run4 passed native login and FCM registration, then incorrectly
looked for device activation on the conversation list. Both owned cleanups passed.

Run5 failed with an emulator timeout and hanging-thread diagnostics. Its old
`starting_emulator` phase also included installation, so a narrower failing phase
is not proven. The combined cleanup result was false; subsequent inspection
confirmed the owned process, disposable image and fixture credentials were gone.
The captured PNG was empty and cannot provide visual evidence.

Run6 passed login, registered one native FCM device and reached the product's
“Chat is ready” screen. It timed out during setup; no precise failing frame was
recorded. Four screenshots were inspected. Peer and emulator cleanup both passed.
The driver now resolves activation actions from one UI snapshot, checks actual
thread navigation after Open chat, and records the last action plus failure frames.
This avoids waiting for a Refresh label after asynchronous provisioning replaces it.

Peer JSON polling uses ADB `shell`, which propagates the remote cat exit status.
An exact missing-file result means pending; transport, permission, malformed and
oversized results fail. A read-only emulator observation confirmed that `exec-out
false` returned zero while `shell false` returned one. Screenshot, UI tree and peer
diagnostics now have independent capture outcomes. Twenty Python guard/cleanup/
transition/polling regressions pass; the next owned FCM attempt remains required.

Private detailed attempts are retained in `.work/product-chat-fcm-2026-11-03`.
They contain no acceptance claim and must not be uploaded wholesale. No live
configuration, employee account or phone app was changed by these tests.

Production integration gap discovered after run6: HRIS Mobile's
`ActivateCollaborationPusher` is bound and unit-tested but has no application caller.
The next product build must wire scoped background activation and wakeups after
chat setup/native push registration. The driver must continue through the normal
UI; calling this use case directly from the fixture would not prove the product.
[Committed attempt evidence](../measurements/product-chat-fcm-fixture-2026-11-03.json).

## Product integration and boot-probe correction — 3 November 2026

Mobile1136fb9e now supplies the missing application activation and normal setup/
registration wakeups. Full root/Wasm/Android/lint PASS495.015s; clean isolated APK
build5 PASS45.425s. Apple41667309251 and Native41667296924 remain pending. Run7 uses
those exact new product5/peer5 APKs, but fails before installation: the boot getprop
subprocess times out while QEMU reports a hanging main loop. Server counts0/0/0;
no product/FCM behavior has been accepted. Run150.079s/driver99.938s, both cleanups
pass; process, image/sidecars and private fixture credentials independently absent.
The inspected failure PNG is entirely black. [Evidence](../measurements/product-chat-fcm-boot-2026-11-03.json).

A single boot-probe timeout now consumes the same100-second total budget and can
be followed by another probe. It does not extend the deadline or relax real boot,
AVD and QEMU identity checks. Dead processes stop immediately. Twenty-four Python
cases pass, including four new boot recovery/deadline/identity/exit cases. Product
and provider timeouts remain failures. Run8 with the same APKs is next; no emulator
flags, live service, phone, real account or source image was changed.
