# Private chat push pilot

On 3 November 2026 the authorized WireGuard pilot switched API/worker to accepted
source `8b40bd0f`, after all fourteen jobs in Verify `41695437208` passed. All three
chat push flags are enabled; SMTP remains disabled. Live schema remains 116.
[Exact evidence](../measurements/chat-push-private-rollout-2026-11-03.json) records
images, CI JAR hashes, checkpoint directories, both attempts and verification.

The existing Matrix server name, identities, devices, encrypted events, keys and
storage are preserved. The coordinated checkpoint restored HRIS data/storage and
the nonempty Matrix database, signing key and media into isolated infrastructure.
HRIS data and keys, and Matrix data and keys, have separate protected directories.
Temporary restore containers/networks were removed. Never restore a checkpoint over
new messages simply to undo an application rollout.

The first attempt failed with an internal address collision and automatically
restored the previous images/configuration. Dynamic application address allocation
could claim the stopped Matrix address before its replacement started. The retry
pins Matrix `.3`, API `.4`, worker `.5` and gateway `.6` on `172.25.0.0/16`, starts
Matrix first, then application services, then the gateway. Its 28.528-second switch
passed health, image, configuration and ingress checks. The transient holder of the
colliding address was not captured; do not attribute it to a specific container.

The private TLS gateway publishes no host port. It accepts only the real Synapse
socket and forwards to an API callback that trusts only the gateway socket. A real
CA-verified request from Synapse reached request validation (empty input: 400).
Direct loopback/WireGuard requests, including forged forwarding headers, and a
different internal-network client were rejected (403). No push key or message was
used by these probes. The gateway uses a pinned image, UID 10001, read-only root,
dropped capabilities and bounded temporary storage.

The active overlay remains `~/.config/hris/matrix-pilot/compose.matrix.yml`; its
private compose command is in the adjacent `compose-command.json`. TLS material and
the reviewed candidate are under `~/.config/hris/chat-push-pilot`. Both trees contain
private configuration: inspect only necessary fields and never publish their full
contents. Preserve the existing Matrix server name and signing key. Keep the fixed
private address assignments together when replacing any of these services.

The local `.work/chat-push-rollout-2026-11-03` evidence includes original overlays,
old image references, `deploy2.py`, and the successful result. Deployment scripts
deliberately reject blind repetition after a recorded attempt. For a later rollout,
capture the current container IDs/configuration and a fresh coordinated recovery
checkpoint; do not reuse the obsolete pre-rollout container inventory. For an
application rollback, stop only the exact owned API/worker/provider/gateway
containers, restore the reviewed configuration/images, then check health and
current data preservation. Do not perform global Docker cleanup or restore old data.

Phone release 28 is installed and pullback-verified in Mobile's matching evidence.
This rollout did not drive the phone UI or send a real-user test message. The
client can register its push binding when the user opens Chat normally. Actual
Firebase wakeup and encrypted SystemUI reply were accepted separately using
isolated fictional accounts in the product FCM fixture. Enabling the live path
does not claim a newly observed notification on the physical phone.

Continue the communication-space migration on `feat/communication-space`; its V117
must not be mixed into this image-only rollout. Recipient acknowledgements, rich
chat, actual Apple/web provider runtime, calls/video and meetings remain open.
