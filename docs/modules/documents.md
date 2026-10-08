# Documents

Own company-scoped metadata, document revisions, upload state, validation, retention, and private content access.

Documents identify owner, category, classification, business resource, and version. Use resumable uploads; validate size/type/content before ready. Authenticate every read and support bounded Range/ETag streaming.

Garbage collection expires abandoned uploads/temporary exports without deleting retained business evidence. Technical processing failure is inspectable and never silently publishes an unvalidated document.

Screens: employee documents, upload progress, revision history, processing status.

Acceptance: offset/lost-response recovery, wrong version/range, permission revocation, cross-company IDs, size limits, interrupted streaming, and finalization replay.
