# Organization chat identity migration

The logical chat owner is the installation's communication space and HRIS account.
Existing Matrix users, devices, SDK stores and rooms retain their original identity.
Company IDs are never replaced with the space ID inside an existing crypto owner.
The [cross-company contract](cross-company-chat.md) remains the acceptance target.

## Implemented database boundaries

V117 provides the stable space and minimal eligible directory. V118 supplies owned
legacy provider metadata without token/credential access. Its bounded repository
preserves company/device cursor ordering and original Matrix identity fields.

V119 adds an immutable provider-user selection per space/account. A `LEGACY`
selection references the exact retained company/device/account/Matrix-user tuple.
A `SPACE` selection uses `@hrisg_{space32}_{account32}:{server}`. Both are provider
user intent only. The table starts empty: migration does not choose identities or
create provider users/devices. Other legacy identities remain intact. Current group
eligibility protects reads, and writes additionally require the account scope.
Credential retirement cannot change the selected user. No parent foreign key
allows ordinary native-session cleanup to erase the selection.

[Actual PostgreSQL evidence](../measurements/collaboration-space-identity-selection-2026-11-03.json)
includes immutable/exact references, scope rejection, two competing writers,
legacy-row preservation and archive restoration. Full39 core cases in18 suites
pass in237.316s, with12 recovery-tool cases and unchanged envelope formats.
Live remains schema116; no group migration has been deployed.

The selection-input repository and pure policy are now implemented without DI or
HTTP wiring. One query distinguishes unavailable scope from admitted absence and
returns the current selection or earliest retained identity on the configured
server. Mapping shares the validated legacy mapper; it never acquires provider
tokens. Actual PostgreSQL checks cover retirement, timestamp-before-UUID preference,
company switching, scope transitions and stable stored selection. Eleven fresh
domain/data cases pass in46.371s with3609 unchanged source hashes and quality checks.
[Evidence](../measurements/collaboration-identity-selection-input-2026-11-03.json)
records the earlier compile warning and report-freshness retry separately.

## Next native selection command

This command and the following device/runtime migration are **not implemented**.
Take the operation receipt guard, exclusive account guard, then native-parent
guard. Recheck current account, credentials, parent version/proof, MFA, group
eligibility, client policy and configured Matrix service before work and at
completion. Do not acquire company/member guards afterward.

Acquire current eligibility, any canonical selection and the preferred retained
identity in one SQL statement. Start from the eligible space, left-join the owned
selection, then a bounded lateral legacy candidate on the configured server when
no selection exists. Order that candidate by first observed identity time, then
unsigned company/device IDs. No outer row means unavailable group access; an
admitted row with no candidate means no retained identity on that server. Separate
nullable reads could mistake an eligibility off/on transition for absent history.

Return an existing selection idempotently only for the same configured provider.
If no selection exists, prefer its retained user on that server, including a user
whose earlier device credential was retired. This does not resume that credential.
Create the new space-derived user intent only when the admitted snapshot has no
retained user on that server. Keep other identities explicit; never overwrite a
selection after configuration or company changes.

Selection, operation receipt and journal must commit atomically, rolling back on
late denial, failure or cancellation. No provider call belongs in that transaction.
Test lost responses, competing devices, coherent eligibility snapshots, wrong
server/source, expiry, revocation and rollback before wiring DI/HTTP. Shared DI
changes must exercise both API and worker application contexts.

## Device and local-store continuity

The new organization owner needs an explicit persisted reference to each retained
SDK store. Reuse a store only after its Matrix user and device match the server's
admitted mapping. If a physical device's legacy user differs from the selected
canonical user, a distinct new device/store can serve the canonical user while the
old store and rooms remain available under explicit legacy mappings. Do not reset
the old store, reuse a retained device ID with fresh keys, or claim that a metadata
row proves local keys exist. Missing keys require the defined encrypted recovery
or a distinct new device identity.

Complete current parent/device admission, provisioning, room/gateway mappings and
bounded legacy runtime leases. Then migrate Mobile inbox, drafts, queues, reads,
notification deep links and background push ownership together. Historical rooms
are not silently merged or re-encrypted. Business context cards continue to enforce
their original HR company/resource permission when opened.

Accept only after two fictional employees from different companies discover,
exchange encrypted messages, receive background notification and reply, switch HR
company, reopen offline and lose access correctly on revocation. Preserve process
death, failure cleanup and existing-history evidence. Directory/selection tests
alone do not accept CHAT-01 or any call/video/meeting capability.
