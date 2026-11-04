# Retained mobile runtime handover

Mobile0b804cb2 implements the local staging/exclusion/drain mechanism below with
actual Android SDK/Keystore cancellation, failure and process-restart evidence.
See [its contract](https://github.com/fajarxfce/hris-mobile/blob/0b804cb2/docs/collaboration-runtime-handover.md).
There is no product handover caller, provider-publication preflight or group runtime
adoption yet. Backend completion and HTTP setup metadata do not establish those.

The original Android runtime registry retired existing reservations when asked to
close other owners and permitted later acquisition. A background notification,
history reopen or process restart could reopen the old store. The new exact-store
gate and durable record guard close these paths. Pending reservations may not yet have an attached SDK resource; an empty resource list does
not prove all acquisitions have drained.

The protected SDK record already owns the original audience/account/company/native
parent/device/store namespace, passphrase and serialized credential revision. Its
JSON decoder is strict. Session callbacks serialize writes through
`SdkSessionPersistence`; SDK shutdown waits for native initialization, child resources,
queues, sync and database pause before releasing the directory lock. Keep those
ownership rules, including bounded waiters whose unfinished cleanup stays owned.

The full transition requires provider key publication/readback before local staging,
then current admission before server freeze. Mobile staging now observes and pins
the current client, excludes every acquisition for the exact store, drains the old
SDK, acquires its exclusive directory and persists the exact protected target. No server freeze may follow an uncertain or failed
local intent write. A competing process holding the store delays this transition.
After intent is durable, every old foreground/background/restore/install path must
reject SDK construction before it can resume the old gateway. A versioned strict
record must also fail closed in an older application build.

Writing after the previous runtime has fully drained avoids a second writer
racing its credential callback. Cancellation before durable intent permits a later
fresh preflight; cancellation after an uncertain committed write must recover the
same stored target and operation identity. The owning use case still revalidates
account/native/group admission before server operations. Do not infer permission
from local intent, public fingerprints or an expired access token.

The registry checks late/pending constructors and replacement clients; the actual
Android proof checks opened-client drain, foreground/background exclusion, write
faults, cancellation and process restart. A product notification racing a complete
group transfer, uncertain message continuity and old-APK downgrade remain untested. Preserve the original encrypted namespace
and passphrase; never replace its company ID with a space ID. Preserve uncertain
message transaction IDs, drafts, receipts, history, push and deep-link ownership when
the group runtime is finally installed. Provider key publication must finish before
permanent server freeze; the current local SDK identity reader performs no upload.
