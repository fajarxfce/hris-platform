# Recipient readiness on the active Matrix connection

The controlled native test on 8 November showed first-key publication and its FCM
job completing promptly, but Google Play Services delivering the accepted hint
about 297 seconds later. The unchanged sender then sent its original once, about
2.6 seconds after OS dispatch. The 140-second gate failed. This is evidence for
an independent foreground signal, not for restarting transport requests on a timer.

## Protocol boundary

`dev.fajar.hris.recipient_ready` is a fixed, clear **to-device control event**. It
does not create a room event, message, read receipt or encrypted key. The internal
sender derives the target's own Matrix user/device from admitted HRIS coordinates;
it uses that target's existing provider credential. No service bot, new login,
replacement device or private-key reset is needed. The public Matrix gateway
continues to accept only its existing encrypted to-device send shape.

The eight-field v1 content contains the immutable publication event, target
company/account/native session/device, publication time and its one-day expiry.
All values have canonical bounded encodings. The existing sync projection accepts
only this exact shape, from the syncing user's own identity, for its exact current
company/account/device. It discards foreign or malformed controls without losing
the rest of the encrypted sync response. Native-parent/time admission remains
required in the consumer. The hint grants no chat content or send authority.

The deterministic transaction includes the publication and target device IDs.
Synapse's transaction cache can disappear on provider restart; provider acceptance
is not an exactly-once guarantee. The client must deduplicate retained controls and
reconcile existing local originals. A missed callback must be recoverable through
startup/connectivity work over those same originals.

## Delivery ownership still required

The transport boundary alone does not emit product controls. Add a separate durable
Matrix readiness checkpoint captured in the first-publication transaction, alongside
the existing FCM checkpoint. Keeping progress independent prevents an unavailable
provider from delaying FCM, or notification permission from excluding an active
device. Select published devices of pre-existing direct peers using bounded,
stable pages; do not depend on push registrations.

The delivery use case must acquire current company/member/account/native/device,
gateway and provider-credential inputs under their ordered guards. It must check
current client policy and key publication. Provider I/O happens after that
transaction, under a finite deadline, followed by a current-owner check and exact
lease/checkpoint advancement. Retain the original event and transaction through
timeouts/lost replies. Do not create or refresh provider credentials in this worker;
the active SDK's existing credential owner handles that lifecycle. Bound retries,
page size, global leases, expiry and worker resources. Keep the new worker disabled
until isolated migration, API/worker context and actual native acceptance pass.

The native runtime must attach its SDK listener before sync starts and release it
with the exact client. Callbacks must not invoke UI, networking policy, a ViewModel,
or the network inspector. An application-owned collector invokes the domain wake
use case; that use case validates the current parent and durably records/coalesces
work before scheduling. Duplicate controls and obsolete callbacks cannot create
repeated sweeps, resend an original, or wake a replacement account. Background FCM
and interrupted-activation/startup/connectivity recovery remain necessary.

## Acceptance boundaries

Transport tests exercise exact mapping, malformed/foreign controls, actual Synapse
sync wake, unacknowledged replay, same-device bearer replacement and provider restart.
These do not establish current-identity delivery admission, durable job behavior,
SDK callback lifetime, product delivery latency, or physical phone acceptance.
Those remain separate gates. The existing device stores and all original messages
must be preserved throughout further acceptance.
