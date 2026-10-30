# Approval history audit — 30 October 2026

The full feature review found a second missing capability after announcement email:
the shared approval request screen exposes current stage assignments but does not
read retained decisions or assignment history. The initial Approvals contract
requires a request timeline; its detailed Dashboard section explicitly leaves
decision history for subsequent work. Existing business-specific leave, expense,
overtime and payroll histories remain valid and are not being replaced.

Evidence at Platform `8014d733`:

- `ApprovalResponse` contains request metadata and current `stages`, with no decisions.
- `ApprovalRepository` writes decisions and assignment overrides but has no history reader.
- `ApprovalRequestDataSource` exposes only `appendDecision`, `appendAssignment` and latest assignments.
- `approval-request-page.tsx` renders the summary and current stage table only.
- V4 already persists immutable `approval_decisions` and `approval_assignment_overrides`.

Implement an authorized `GET /approvals/{id}/history` and a dashboard history view.
Reuse request read scope: current company/account/session, administrator,
author/requester or eligible direct/delegated approver, with the original/live
permission intersection and final assurance check. Acquire shared approval/access
guards so request metadata, decision rows and overrides are coherent. Do not add
a generic approval decision action or call another use case from this reader.

The response should retain the current request snapshot, at most eight actual
decisions ordered by workflow step and a bounded revision-ordered reassignment
page (default 50, maximum 200). A following page must carry the observed request
version and fail explicitly if the request changed. Stable reassignment revision
cursors must not skip or duplicate retained records. Existing submission time is
real evidence; a cancelled status alone supplies no historical cancellation
timestamp. Never invent old actions, actor names, chronology or decision reasons
from current assignments. Account IDs and persisted reasons stay inside the
same authorized request boundary; no email or credential projection is needed.

Test direct/delegated decisions, reassignment, blocked requests, immutable prior
facts, original/live revocation, foreign requests, pagination bounds, changed
request versions, lost/cancelled reads and atomic snapshot races. Shared DI changes
must pass API and worker contexts. Dashboard scope/lifecycle, English/Indonesian
and real API/browser acceptance must pass before calling this capability complete.

Continue the remaining module and Mobile review after this increment. Physical
iPhone remains waived, and the accepted private schema-101 runtime/phone stay
separate from these owned tests. Announcement email source `8014d733` is undergoing
[full CI](https://github.com/fajarxfce/hris-platform/actions/runs/41005223374).

## Implemented reader and local acceptance

[Approval history](approval-history.md) now implements this contract from existing
immutable tables, with no migration. [Measurements](measurements/approval-history-2026-10-30.json)
record baseline reproduction, corrected findings and final API/worker/domain/UI
acceptance. Continue the broader module audit, including final assurance on legacy
readers; this increment does not establish whole-PRD completion.
