# Document business metadata

Status: backend and dashboard implemented; native upgrade and full acceptance remain in progress. The [source audit](document-identity-metadata-gap-2026-10-30.md)
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

The HTTP surface adds `PUT /documents/{id}/metadata` and
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

## Backend and dashboard validation — 30 October 2026

Two real HTTP reproductions failed at `158b81ac`: initial metadata was rejected
and the correction route was absent. V103 and the implemented contracts now pass
24 focused API cases and the upgrade/RLS/history migration case. Coverage includes
original/live ownership, late MFA rollback, history immutability, archive/replay,
competing versions/operation keys, the 1,000-snapshot bound, old eleven-part upload
hashes and owned-sync updates without shared-evidence identity numbers.

Broad regression ran for 1,180 seconds: 688 API cases had three failures in old
response expectations and a deliberately pre-V68 fixture using the current upload
API. All 104 ordinary worker, 27 database, 18 Document domain and nine data cases
passed; one separately accepted manual worker test stayed skipped. After correcting
only those tests and adding one OpenAPI assertion, the final 78 API cases passed
in 84 seconds with unchanged production and package hashes. Replacing those three
suites gives 689 distinct accepted API cases. Both API and worker contexts, format,
architecture and packages passed. This is combined evidence from explicit runs,
not a claim that the initial broad invocation was green.

Dashboard architecture, TypeScript, Biome, build and all 4,054 unit cases passed
(357 files, 143.78 seconds). Twenty-five mocked Chromium scenarios passed in 46.7
seconds. The final real PostgreSQL/browser metadata workflow passed in 26.9 seconds
including setup (4.6 seconds for the scenario); all four existing document browser
workflows also passed. Initial new-fixture failures were an unregistered account
and an incorrect assumption that locale survived hard navigation. Both were fixed
in the fixture. The actual workflow verifies invalid-country rejection with no
header, historical validity, later independent edits and recovery of the identical
original request after a committed response is lost. Binary revision facts stay
unchanged. Its object store is intentionally disabled; chunk reservation fails
after metadata commit, so this browser scenario does not prove file inspection.

The rejection exercise also reproduced and fixed a dashboard state error: a first
definitive metadata rejection remained paused. It now returns to editing, while a
rejection after an uncertain delivery retains the original pending request. Final
unit and actual browser checks cover both cases. Dark/narrow editor layout was
visually inspected. Existing CSS specificity and bundle-size warnings remain.

[Measurements](measurements/document-business-metadata-2026-10-30.json) retain
reproductions, suite indexes, counts and hashes. Native category/identity fields,
versioned correction/history, unchanged-version encrypted cache upgrade, recovery
and affected Android/Apple builds remain next. Full CI/schema-103 restore is also
pending for this source. Neither requirement is accepted yet. The private schema-
101 API, Google account binding, SMTP configuration and phone remain unchanged.
