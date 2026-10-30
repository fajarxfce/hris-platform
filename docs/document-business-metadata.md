# Document business metadata

Status: implementation in progress. The [source audit](document-identity-metadata-gap-2026-10-30.md)
reopened `documents.02` and retained `people.03` as unverified. This working
contract is not acceptance evidence. Finish all backend/client/migration/recovery
checks before marking those requirements complete.

Documents owns an explicit category and optional document/identity number, issuing
country, issue date and business-validity date. Categories cover passport, national
identity, work/residence permit, employment contract, certificate, receipt and
other. Existing visibility/purpose classification remains independent. Nationality
belongs to People, and verified tax residency remains in Payroll. Neither follows
from an identity document or its validity date. Expired documents remain evidence;
business expiry does not remove content or change employment, access or tax status.

Store bounded metadata with a separate immutable history. Each metadata edit
requires the observed document version, an idempotency key and a reason. It must
increment the existing document version so the current `DOCUMENT_EVIDENCE` feed
publishes the updated owned facts. A sequential metadata revision (0–999) pins
at most 1,000 snapshots; history pages remain bounded to 200. Preserve existing
upload/publication/retirement transitions, archived-document rules, company RLS,
original/live scope and MFA through completion. Head and history must commit
atomically with audit, outbox and receipt. Initial migrated metadata must not be
presented as human verification.

The proposed HTTP surface adds `PUT /documents/{id}/metadata` and
`GET /documents/{id}/metadata-history` inside the existing company scope. The
write carries `expectedVersion`, `metadata` and `reason`. The value contains
`category`, `identityNumber`, `issuingCountry`, `issuedOn` and `validUntil`.
Document details expose the current value and metadata revision. Optional initial
metadata may accompany a new upload; an existing document uses the separate
versioned editor. Correcting metadata must not require re-uploading file bytes.

Legacy requests, receipts and pending uploads keep their original payload/hash
when metadata is absent. Omission must never clear existing metadata. Advertise
support through existing scoped discovery/detail responses; new clients must not
send new fields to an older unsupported endpoint. Client upgrade must acquire new
metadata even when an older client already consumed the same document version,
while retaining owned cached facts until admitted replacement. Reuse the existing
native metadata-upgrade/canonical-refresh behavior where it meets this contract.

Dashboard/native flows need concise category/identity/date fields, immutable pending
commands, explicit recovery after uncertain delivery, scoped/cancellable reads and
bounded history. Keep personal numbers out of diagnostics and unrelated public/
finance projections. This HRIS business capability does not belong in Starter.
Validate real scope, concurrency, rollback, old receipts/omissions, migration,
publication/sync, rejected uploads, archive/history, client cache upgrade and
recovery. Both API/worker contexts and affected native/Apple builds remain required;
physical iPhone testing remains waived.
