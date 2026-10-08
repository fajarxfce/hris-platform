# Administration

Own effective company policy configuration, module flags, client availability, integration status, audit queries, and operational job controls.

Flags affect capability availability but never grant permissions. Version gates and maintenance policy are explicit. Integration secrets live in deployment secrets, not ordinary settings or Git.

Runtime audit is append-only with actor/reason/resource/correlation. Failed jobs expose safe categories and authorized retry. Backup/restore includes database, content, and encryption-key recovery.

Screens: settings, flags, integration status, audit explorer, job monitor.

Acceptance: policy effective dates, restricted settings/audit access, safe retry, stale client policy, secret redaction, and recovery from backup.
