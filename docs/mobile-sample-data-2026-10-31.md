# Requested mobile sample data — 31 October 2026

The existing Google-linked development employee now has populated views for the
mobile layout review. All business facts are fictional, labelled `Contoh` or
`Demo`, and belong to the existing `HRISDEMO` company.

| Data | Added and verified |
| --- | --- |
| Coworkers | Four fictional employees without login accounts |
| Leave | Approved, pending and withdrawn/cancelled examples; ten extra fictional balance days |
| Expenses | Approved, pending and draft examples with IDR amounts |
| Overtime | Approved actual time, planned and withdrawn examples |
| Attendance | Five historical days recorded explicitly as administrative sample corrections |
| Inbox | Three sample messages targeting only the requested employee |
| Documents | A training certificate and a transport receipt, both fictitious PDFs |

The normal employee API reads verified all nine request statuses, all five
attendance days and all three Inbox items. The real scanner published both PDFs
as READY; downloads under the employee's authorization matched the generated
bytes. No email was requested or sent. No salary, payment or real GPS evidence
was fabricated. Existing user bindings, grants and phone sessions were preserved;
the temporary setup session was revoked.

[The dated setup tool](../tool/seed_mobile_demo.mjs) requires a private config and
an existing private operation manifest. It checks the fictional company code,
account/person relationship and owned employment. Business commands use normal
APIs, independent administrator decisions, observed versions and persisted
idempotency keys. It never inserts database business rows or relaxes approval
policy. The announcement audience is a group containing exactly this employment,
with one recipient confirmed before publication.

The private files for continuation are
`.work/ui-review-2026-10-31/sample-config.json` and `sample-state.json`; do not
commit either file. Config keys are `baseUrl`, `adminStorageState`, `company`,
`account`, `employment`, `person`, `email`, `password`, `leaveType`,
`expenseCategory` and the fixed `asOf` date `2026-10-31`. Run `--inspect` to
authenticate, verify existing sample reads and revoke the setup session. Only
`--apply` executes unfinished commands; always reuse the same manifest.

Two initial setup commands returned HTTP 400 before writing: the balance version
was read from the ledger wrapper, and Leave withdrawal used the wrong version
field name. Corrected commands have distinct keys; the original rejections are
retained and the successful operations were not repeated. Final verification and
safe counts are in [measurements](measurements/mobile-sample-data-2026-10-31.json).

The mobile presentation review is still in progress. This data increment does
not establish completion of every layout or a new native provider test.
