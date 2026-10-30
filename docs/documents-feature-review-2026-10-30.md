# Documents requirement review — 30 October 2026

The five initial requirements and all ten original detailed sections have been
reviewed against source and matching behavior. The review found and corrected
[read completion](document-read-assurance-2026-10-30.md) and
[command completion](document-command-assurance-2026-10-30.md). Local acceptance passed 243 API, 18 domain and nine data cases, quality/packages
and four actual API/browser workflows (42.5s). All five rows are locally accepted;
final-source CI remains separately tracked in the linked measurements and inventory.

| Requirement | Implementation and matching behavior |
| --- | --- |
| `documents.01`: metadata, revisions, upload, validation, retention and private content | Document repository/use cases, forced company RLS, immutable revisions, guarded retention and private transports. API access, original-access, retention, retirement and current-assurance suites verify the distinct permissions and transaction boundaries. |
| `documents.02`: ownership/classification/references, resumable validation and Range/ETag | Upload preparation registers each physical attempt before object I/O. Validation checks the complete manifest, hash, size, detected type and scanner evidence before atomic publication. Upload/validation/body/download suites cover replay and transport; the full-signature workload uploads, validates and downloads two 100 MiB documents and rejects EICAR. |
| `documents.03`: abandoned-content cleanup, retained evidence and inspectable failure | Registered cleanup and inventory classify exact known physical attempts conservatively. Business references and accepted revisions prevent deletion; validation failures retain inspection evidence and never replace accepted publication. Inventory bounds/security, reference, retirement/inventory and validation worker suites exercise these paths. Temporary reporting exports retain their separate Reporting ownership and elapsed-time acceptance. |
| `documents.04`: employee documents, progress, revisions and processing | Dashboard document controllers/pages and native upload/catalog flows. Four actual API/browser workflows exercise resumable upload/cancellation, retention policy, archive/restore/hold and accepted retirement including a competing hold. Native interrupted-upload evidence covers process death, retry, rejected inspection and local cancellation. |
| `documents.05`: offsets, lost responses, versions/ranges, revocation, scope, limits and interruption | API upload/body/download/original-access and command/read assurance tests exercise these boundaries, including expiry during actual I/O and journal completion. Native interrupted upload retains operation identity; full-signature storage measurements and existing bounded transport tests cover resource limits. |

The detailed review preserves these boundaries:

- Uploads use 1 MiB parts, at most 100 MiB/100 parts, a 24-hour lifetime, finite
  account/company quotas and eight attempts. Each attempt has its own object key;
  storage I/O runs outside SQL transactions, with registered cleanup retained after
  a late failure. The same operation recovers a lost response without another commit.
- Self-service discovery exposes account-owned employments and published evidence,
  excludes `HR_ONLY`, and stays independent of general People browsing. Owned sync
  never broadens to administrative document access.
- Inspection has two sessions with no queue, a 120-second whole-operation deadline
  and bounded scanner transport. Validation rechecks lease, cancellation, authority,
  revision and ownership after I/O; only complete clean evidence publishes. Publication,
  job progress, inspection evidence, retention and journal share a transaction.
- Authenticated downloads keep bounded Range/ETag/HEAD behavior, four transfers and
  readers, 16 KiB slices, 1 MiB parts and a 60-second deadline. Preparation and each
  completed content acquisition require current authority; late bytes are withheld.
- Inventory reads at most 100 objects per page and 10,000 pages, with eight run
  attempts. Unknown or mismatched objects are observations only. Recovery requires
  an exact unusable known attempt older than the captured cutoff, permits at most
  three recoveries and queues deletion with a five-minute grace.
- Expense and Leave references are immutable transaction-owned evidence backed by
  deferred constraints. Removing an attachment from a later draft, withdrawing or
  cancelling its business request does not erase the historical reference.
- Retention requires a separate explicit privilege and recent authentication.
  Archive freezes its policy; holds survive restore; history is bounded. Retirement
  requires an accepted revision, elapsed finite policy, no hold, no business reference
  and both observed versions. Metadata remains, no prior revision is auto-published,
  and quota stays reserved until cleanup acknowledgement.
- Native interrupted-upload acceptance remains the recorded Android execution.
  The physical iPhone test is waived and no APNs/device claim is inferred from it.

Evidence: [read acceptance](measurements/document-read-assurance-2026-10-30.json),
[command acceptance](measurements/document-command-assurance-2026-10-30.json),
[full-signature workload](measurements/documents-2026-10-28.json),
[native recovery](native-upload-acceptance.md), and
[bounded storage](storage.md). The earlier scanner-limit failure remains a failed
baseline, not acceptance. No live runtime, storage object or phone was changed.
