# Documents

Own company-scoped metadata, document revisions, upload state, validation, retention, and private content access.

Documents identify owner, category, classification, business resource, and version. Use resumable uploads; validate size/type/content before ready. Authenticate every read and support bounded Range/ETag streaming.

Garbage collection expires abandoned uploads/temporary exports without deleting retained business evidence. Technical processing failure is inspectable and never silently publishes an unvalidated document.

Screens: employee documents, upload progress, revision history, processing status.

Acceptance: offset/lost-response recovery, wrong version/range, permission revocation, cross-company IDs, size limits, interrupted streaming, and finalization replay.

## Resumable upload API

The upload/status/cancellation and durable validation APIs are implemented. Uploading all bytes does not publish a revision; only a completed, accepted inspection sets `READY`. Authorized downloads are available for ready revisions.

Company routes use `/api/v1/companies/{companyId}/documents`. `POST /uploads` accepts stable document/revision UUIDs, employment ID, title, classification, expected document version, file name, media type, size, SHA-256, and reason. Use `Idempotency-Key` for every command. Metadata/history use `GET /{documentId}`, `GET /{documentId}/revisions`, and `GET /revisions/{revisionId}`. Lists require an `employmentId` and support bounded cursors.

Send `POST /revisions/{revisionId}/chunks` as `application/octet-stream` with `Upload-Offset`, `Upload-Checksum-Sha256` (lowercase hex), and a stable idempotency UUID for that chunk. Chunks are exactly 1 MiB except the final chunk. After a lost response, repeat the same chunk/key or fetch `uploadedBytes`; the original receipt remains stable even after later chunks commit. A mismatched offset or command payload returns a conflict. An active attempt returns `document_chunk_in_progress` with `fields.retryAt`. A failed attempt can be reclaimed after its 120-second lease using the same operation ID. Eight unsuccessful attempts require cancelling and starting a new revision; the server does not retry uploads silently.

File declarations are restricted to PDF, JPEG, and PNG, with a matching extension, safe file name, expected digest, and a 100 MiB maximum. An upload expires after 24 hours. Limits are ten active uploads per actor, 100 per company, 100 revisions per document, 10,000 document headers, and a 10 GiB company reservation budget. Every physical attempt counts toward the budget until cleanup acknowledges deletion. Four binary requests can run concurrently per API instance. Both declared and streamed bodies are bounded before allocation; body progress cannot extend the 30-second read budget, and socket inactivity is limited to five seconds.

`POST /revisions/{revisionId}/cancel` takes an expected version and reason. Cancellation prevents a pending writer from committing and schedules temporary objects for deletion after a five-minute grace period. Starting a replacement closes an expired upload without rewriting its metadata/history. Every write attempt has a different storage key; registration precedes external I/O, and progress, command receipts, and audit commit atomically afterwards. File I/O holds no database transaction. See [temporary-object cleanup](../storage.md#temporary-object-cleanup).

Generic reads require `documents.read` and `people.profile.read`; writes require `documents.manage` and `people.profile.manage`. Self-service uses `documents.self.read` / `documents.self.upload` for the account linked to the employment. `HR_ONLY` documents are excluded from self-service. Finance access to claim receipts belongs to the expense workflow's resource authorization. Current credentials, membership, and grants are checked again before accepting pending writes. Responses contain metadata, never physical storage keys.

## Content inspection adapter

The content inspection adapter combines private storage chunks, Apache Tika 4.1 type detection, and ClamAV's INSTREAM protocol. It reports observed size, whole-file SHA-256, media type, scanner version, and verdict. The domain workflow decides whether that evidence permits publication. A clean scanner response alone does not establish a matching document type or digest.

Set `HRIS_DOCUMENT_SCANNER_ENABLED=true`, `HRIS_DOCUMENT_SCANNER_HOST`, and `HRIS_DOCUMENT_SCANNER_PORT` (default 3310) for a managed private ClamAV endpoint. The protocol is unencrypted and unauthenticated; keep that endpoint on the private service network. It must not be exposed as a public upload API. Disabled/unavailable configuration cannot return a successful inspection.

The adapter permits two sessions per process without a waiting queue, checks at most 100 one-MiB parts, and sends them incrementally. Detection uses at most 64 KiB. Connections allow three seconds to connect, ten seconds per write, seventy seconds for a final scanner response, and 120 seconds for the complete inspection. Response frames are limited to four KiB. Nonblocking socket/selector loops check deadlines and interruption; every session, permit, stream, and selector has an owner. Existing storage failures retain their classification even if scanner cleanup also fails.

Configure the daemon for at least the document limit (`StreamMaxLength 105M`, `MaxFileSize 105M`), finite scan/archive limits, and `AlertExceedsMax yes`. Enable encrypted-document alerts if those files cannot be inspected under the organization's policy. The optional [private document services](../storage.md#private-document-services) supply this configuration and a separate signature updater. Keep signature databases current and persistent. Integration tests use ClamAV 1.5.4 with a deliberately minimal, owned EICAR signature database; they do not establish production signature coverage or update operations. The worker runs this adapter through the validation workflow described below.

References: [ClamAV releases](https://github.com/Cisco-Talos/clamav/releases), [ClamAV Docker configuration](https://github.com/Cisco-Talos/clamav-docker/blob/main/clamav/README-alpine.md), [clamd protocol](https://docs.clamav.net/manual/Usage/Scanning.html#clamd), [Apache Tika](https://tika.apache.org/).

## Validation and publication

After every chunk is committed, call `POST /revisions/{revisionId}/validate` with `expectedVersion`, a reason, and an idempotency key. The response is the revision mutation receipt; its status response exposes `validationJobId`. Observe that job through the existing company job API. `GET /revisions/{revisionId}/validation-attempts` returns the bounded, immutable attempt history to authorized document readers.

The worker checks current credentials, company membership, document permission, upload expiry, and its lease before and after storage/scanner I/O. It performs no external I/O inside a database transaction. A complete manifest, matching whole-file size/SHA-256/type, and a clean verdict are required. An accepted revision becomes `READY` and advances the document's `currentRevisionId`. A rejected revision becomes `REJECTED` with a stable failure code; the previous ready revision remains current. The job succeeds when inspection reaches either verdict. Technical processing failures use `VALIDATION_FAILED` and remain unavailable for content access.

Accepted object retention, inspection evidence, publication, job checkpoint/completion, and audit commit together. Losing any accepted cleanup registration fails the entire publication. Ready revisions remain immutable and count against company storage quota even after their temporary cleanup rows are removed. Cancelled, expired, and rejected revisions cannot become ready.

Transient infrastructure failures use the existing job retry policy with at most eight lease attempts. An operator can explicitly call `/validate` again after the prior job failed or was cancelled; a revision allows at most eight validation jobs. Crashed/exhausted jobs are projected as failed processing without rewriting their history. An expired upload requires a new revision. Cancelling the upload also requests cancellation of its active validation job. Late results and stale leases cannot publish; a worker can finalize cleanup even after the initiating account loses access.

## Authorized downloads

`GET /revisions/{revisionId}/content` streams a ready revision as an attachment. `HEAD` returns its headers without reading storage. Both requests verify current document scope; knowing a revision UUID or ETag does not grant access. Responses use a strong revision/content ETag, `private, no-store`, `nosniff`, and `Accept-Ranges: bytes`.

One `Range: bytes=start-end`, open-ended range, or suffix is supported. An invalid, unsatisfiable, or multipart range returns an empty 416 with `Content-Range: bytes */size`. `If-Range` uses an exact strong ETag; a different validator returns the full representation. `If-None-Match` can return 304 after authorization. HEAD ignores Range and does not acquire a download slot. Resume a partial download using the same revision and its ETag.

Four concurrent transfers share four owned readers and a bounded queue per API instance. Each response reads one part of at most 1 MiB at a time, verifies its stored digest, and writes in nonblocking 16-KiB slices. Socket backpressure stops additional part reads. No database transaction is held during storage or client I/O, and credentials/company/document scope are checked again after every storage read. A revoked permission prevents that pending part from being emitted. Previously authorized bytes already sent to a client cannot be withdrawn.

Transfers have a 60-second deadline. Saturation returns 429 with `Retry-After: 1`; shutdown returns 503. Errors after metadata authorization may have an empty body. If failure occurs after output starts, the incomplete Content-Length tells the client that the transfer was interrupted; retry only the missing range with a matching validator. Disconnect, timeout, and application shutdown cancel owned reads, release buffers/slots, and discard late results. Reader shutdown waits at most 25 seconds; the API container allows 90 seconds for servlet and resource shutdown. These are configured bounds, not throughput or heap measurements.

## Storage inventory reconciliation

`POST /inventory` starts a company scan with `runId`, `reason`, and `Idempotency-Key`. It requires `documents.inventory`, `jobs.retry`, and recent authentication/MFA. Existing memberships need an explicit grant; new company administrators receive the inventory capability. Inventory access does not grant document content access. `GET /inventory`, `GET /inventory/{runId}`, `/attempts`, and `/pages` expose bounded summaries and evidence without storage keys. Lists default to 50 and allow at most 100 records. Page history uses an integer `after`; run lists use a UUID cursor.

Each worker step lists at most 100 objects in the selected company's prefix, then rereads its lease, credentials, membership, permissions, and inventory version before committing. Storage I/O holds no SQL transaction. The cursor uses UTF-8 byte order and is private; unusual unknown keys remain representable without passing arbitrary keys into document-reference queries. This is a traversal of a changing bucket, not a snapshot. A later scan can find objects inserted behind the cursor.

Only an exact, immutable upload attempt with a matching byte count can be recovered. Its object must have been modified at least 24 hours before the scan started, and the attempt must be superseded, cancelled, rejected, or expired. Accepted `READY` content remains retained, including historical revisions. Active, recent, unknown, and inconsistent objects are preserved and counted separately. An existing cleanup registration, including a failed one, is never reset. Recovery queues deletion after another five-minute grace period; inventory itself performs no physical deletion. Cleanup registration, observed object evidence, page counts, job progress, and audit commit together.

A company can run one inventory job at a time, with a maximum of 10,000 retained runs. A scan stops at exhaustion or 10,000 pages (at most one million observations); `LIMIT_REACHED` explicitly means the traversal is incomplete. The generic job reports `progressMode: UPPER_BOUND`, `completedItems` as pages processed by that attempt, and `totalItems` as its remaining page ceiling. Do not display the ceiling as a known object total. Use the inventory summary for object counts.

Cancellation uses the existing company job endpoint. `POST /inventory/{runId}/resume` takes an `expectedVersion`, reason, and idempotency key after the previous job failed or was cancelled. Resume retains the original cursor/cutoff and creates a new immutable job attempt; a run permits eight such attempts. Transient failures use the existing bounded job lease retry policy. Exhausted/crashed jobs remain visible through their terminal job status. A stale worker cannot accept a late listing. Previously committed cleanup registrations remain valid after cancellation or revocation.

The same physical attempt can be recovered at most three times across scans. Further reappearances are counted as `recoveryExhausted` for operator investigation; they do not trigger an infinite cleanup loop. Unknown/anomalous objects also require storage-operator investigation. Retention of accepted business documents remains a separate lifecycle policy.
