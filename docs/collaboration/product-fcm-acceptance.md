# Actual Android product FCM acceptance

Status: fixture implementation in progress; no passing FCM execution yet. Live chat
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
it on the user's phone. Its data/image, SDK peer and owned processes are cleaned even
when setup fails. The original image and live HRIS/Matrix/Firebase account remain intact.

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
