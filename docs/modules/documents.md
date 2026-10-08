# Documents

Own company-scoped metadata, document revisions, upload state, validation, retention, and private content access.

Documents identify owner, category, classification, business resource, and version. Use resumable uploads; validate size/type/content before ready. Authenticate every read and support bounded Range/ETag streaming.

Garbage collection expires abandoned uploads/temporary exports without deleting retained business evidence. Technical processing failure is inspectable and never silently publishes an unvalidated document.

Screens: employee documents, upload progress, revision history, processing status.

Acceptance: offset/lost-response recovery, wrong version/range, permission revocation, cross-company IDs, size limits, interrupted streaming, and finalization replay.

## Resumable upload API

The upload/status/cancellation API is implemented. Validation, publication, and downloads are the next slice; uploading all bytes does not make a document readable.

Company routes use `/api/v1/companies/{companyId}/documents`. `POST /uploads` accepts stable document/revision UUIDs, employment ID, title, classification, expected document version, file name, media type, size, SHA-256, and reason. Use `Idempotency-Key` for every command. Metadata/history use `GET /{documentId}`, `GET /{documentId}/revisions`, and `GET /revisions/{revisionId}`. Lists require an `employmentId` and support bounded cursors.

Send `POST /revisions/{revisionId}/chunks` as `application/octet-stream` with `Upload-Offset`, `Upload-Checksum-Sha256` (lowercase hex), and a stable idempotency UUID for that chunk. Chunks are exactly 1 MiB except the final chunk. After a lost response, repeat the same chunk/key or fetch `uploadedBytes`; the original receipt remains stable even after later chunks commit. A mismatched offset or command payload returns a conflict. An active attempt returns `document_chunk_in_progress` with `fields.retryAt`. A failed attempt can be reclaimed after its 120-second lease using the same operation ID. Eight unsuccessful attempts require cancelling and starting a new revision; the server does not retry uploads silently.

File declarations are restricted to PDF, JPEG, and PNG, with a matching extension, safe file name, expected digest, and a 100 MiB maximum. An upload expires after 24 hours. Limits are ten active uploads per actor, 100 per company, 100 revisions per document, 10,000 document headers, and a 10 GiB company reservation budget. Every physical attempt counts toward the budget until cleanup acknowledges deletion. Four binary requests can run concurrently per API instance. Both declared and streamed bodies are bounded before allocation; body progress cannot extend the 30-second read budget, and socket inactivity is limited to five seconds.

`POST /revisions/{revisionId}/cancel` takes an expected version and reason. Cancellation prevents a pending writer from committing and schedules temporary objects for deletion after a five-minute grace period. Starting a replacement closes an expired upload without rewriting its metadata/history. Every write attempt has a different storage key; registration precedes external I/O, and progress, command receipts, and audit commit atomically afterwards. File I/O holds no database transaction. See [temporary-object cleanup](../storage.md#temporary-object-cleanup).

Generic reads require `documents.read` and `people.profile.read`; writes require `documents.manage` and `people.profile.manage`. Self-service uses `documents.self.read` / `documents.self.upload` for the account linked to the employment. `HR_ONLY` documents are excluded from self-service. Finance access to claim receipts belongs to the expense workflow's resource authorization. Current credentials, membership, and grants are checked again before accepting pending writes. Responses contain metadata, never physical storage keys.
