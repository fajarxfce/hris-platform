# Retained mobile runtime handover

This is the remaining client contract, not implemented handover. Backend completion
and HTTP setup metadata do not establish local SDK shutdown or private-key ownership.

The Android runtime registry currently retires existing reservations when asked to
close other owners. It explicitly permits later acquisition. A background notification,
history reopen or process restart can therefore open the old store again. Pending
reservations may not yet have an attached SDK resource; an empty resource list does
not prove all acquisitions have drained.

The protected SDK record already owns the original audience/account/company/native
parent/device/store namespace, passphrase and serialized credential revision. Its
JSON decoder is strict. Session callbacks serialize writes through
`SdkSessionPersistence`; SDK shutdown waits for native initialization, child resources,
queues, sync and database pause before releasing the directory lock. Keep those
ownership rules, including bounded waiters whose unfinished cleanup stays owned.

A possible transition is: prove the current opened SDK identity and provider key
publication; temporarily exclude every acquisition for the exact store; retire and
drain existing reservations, including unfinished constructors; acquire the exclusive
directory lease; persist an exact-target handover intent in the protected record;
then request the server freeze. No server freeze may follow an uncertain or failed
local intent write. A competing process holding the store delays this transition.
After intent is durable, every old foreground/background/restore/install path must
reject SDK construction before it can resume the old gateway. A versioned strict
record must also fail closed in an older application build.

Writing after the previous runtime has fully drained can avoid a second writer
racing its credential callback. Cancellation before durable intent permits a later
fresh preflight; cancellation after an uncertain committed write must recover the
same stored target and operation identity. The owning use case still revalidates
account/native/group admission before server operations. Do not infer permission
from local intent, public fingerprints or an expired access token.

This ordering still needs implementation and actual Android SDK evidence, including
pending construction, late credential writes, timeout, failure, process death and a
background notification racing handover. Preserve the original encrypted namespace
and passphrase; never replace its company ID with a space ID. Preserve uncertain
message transaction IDs, drafts, receipts, history, push and deep-link ownership when
the group runtime is finally installed. Provider key publication must finish before
permanent server freeze; the current local SDK identity reader performs no upload.
