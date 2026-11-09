# Publish the verified original identity during transfer

When an original company device became an organization device, provisioning already
read and verified its actual signed Matrix identity, then atomically completed its
LEGACY transfer. It did not retain the organization key-publication record required
by V135 receiving readiness. The unchanged original SDK can already consider its
identity uploaded; reopening that store need not upload it again. This left a
successfully transferred recipient unavailable for encrypted delivery.

The correction belongs to `AdvanceCollaborationSpaceProvisioning`. Its existing
bounded provider read remains outside SQL locks. Current account/native/device,
queue, provider and original-runtime observations are checked on both provider
outcomes and again before publication. The exact signed fingerprint, immutable
completion and native/provider/device versions must agree.

Inside the existing completion transaction, reserve the exact original public
identity, acknowledge it, verify readback and append the key-publication audit/outbox
event. Both timestamps use the database's immutable completion time. That satisfies
the retained-identity receiving projection without trusting a later client's clock.
Credential activation, original retirement, completion, key publication and audit
roll back together. An unexpected existing publication or changed successful
readback fails; there is no overwrite, reset or additional provider write.

Fresh SPACE devices still generate and upload their own keys. Standard empty
`keys/upload` stays counts-only. The change adds no client endpoint, SDK token
refresh, additional send request, migration or relaxed receiving predicate.
Already-completed worker leases cannot publish twice. Original company key records,
crypto stores and message transactions remain intact.

The regression first failed to compile because its fixture passed a DSL context
instead of the actual key datasource. After fixing only fixture construction,
unchanged production failed on the missing group publication. Both attempts remain
retained. The corrected focused gate passes11 cases in164.148 seconds, including
36 failure/cancellation/interruption/flag variants across transaction writes,
competing workers, late admission and real Synapse signed-key continuity.

The actual Android native gate then passes in139.231 seconds. The fixture asserts
confirmed publication immediately after worker completion, before gateway bootstrap.
Its former explicit key upload has been removed. The unchanged SDK6 proof APK adopts
the original identity and delivers two original messages with the recipient offline.
After process death between durable ACK and Sent projection, the new process
recovers both originals; a later process reads both Sent records offline before
transport. The provider contains exactly two encrypted events. Ten native requests
contain two bootstrap and two direct-transport reads, with no extra key-confirmation
request. Source/APK hashes match and the isolated emulator is stopped.

The final source gate passes616 fresh cases in1410.192
seconds:complete domain,relevant SQL/provider/delivery,native API/OpenAPI and the
FULL unfiltered worker suite. There are zero failures/errors and1 existing
opt-in skips. Format and architecture checks pass. [Audited evidence](../measurements/collaboration-space-original-key-publication-2026-11-09.json)
binds archived XML,all8,698 frozen backend inputs,the unchanged Mobile proof source
and SDK6 APK to these outcomes. The focused cases are included in the final suite,
not added again to its count. The one native case is reported separately.

This accepts source and the bounded native scenario. Live e558a686/schema116.4 and
phone87 remain unchanged; organization routing is still OFF. No Mobile source or
SDK change was needed. Organization readiness signals,background ownership and
product routing remain next work; all54 PRD acceptance groups remain open.
