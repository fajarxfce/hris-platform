# Current PRD review

Reviewed 29 October 2026. The overall PRD is **not declared complete**: actual
provider/device/deployment acceptance still needs external inputs. The current
automated verification and generic Starter port are accepted. This page is the compact continuation map;
[completion](completion.md) and [delivery](delivery.md) retain the detailed history.
Read [agent handoff](agent-handoff.md) for the latest active work before starting.

## Implementation and acceptance map

The eleven module screen/acceptance lists were reread against their current
contracts and owning workflows. No source TODO/NotImplemented/coming-soon stub was
found in Platform/Mobile application sources. That search is supplementary, not a
proof that all behavior is correct. The references below are existing implementation
and acceptance evidence; the current CI result must be assessed separately.

| PRD area | Implemented scope and retained evidence | Actual workflow example |
| --- | --- | --- |
| [Identity](modules/identity.md) | Accounts, roles/memberships, invitations, MFA, app sessions and explicit identity binding. Firebase email/password through HRIS was accepted; real Google token exchange remains external. | [Invitations/session replacement](../apps/dashboard/integration-worker/account-invitations.spec.ts), [bindings](../apps/dashboard/integration/oidc-bindings.spec.ts) |
| [People](modules/people.md) | Effective-dated organization/employment, import, account binding, company transfer and scheduled lifecycle changes. | [Import](../apps/dashboard/integration/employee-imports.spec.ts), [transfer](../apps/dashboard/integration/employment-transfer.spec.ts), [scheduled onboarding](onboarding-schedules.md) |
| [Workforce](modules/workforce.md) | Shifts, holidays, weekly/roster/bulk calendars, immutable attendance/review/corrections, planned/actual overtime and closing/adjustments. Physical GPS remains external. | [Attendance](../apps/dashboard/integration/attendance.spec.ts), [overtime](../apps/dashboard/integration/overtime.spec.ts), [closings](../apps/dashboard/integration-worker/work-periods.spec.ts) |
| [Leave](modules/leave.md) | Effective policies, reviewed creation, independent staged actions, balances, accrual/year close/carry/expiry and team calendar. | [Creation/replay](../apps/dashboard/integration/leave-creation.spec.ts), [expiry](../apps/dashboard/integration-worker/leave-carry-expiry.spec.ts), [calendar](../apps/dashboard/integration/leave-calendar.spec.ts) |
| [Approvals](modules/approvals.md) | Versioned templates, immutable assignee stages, delegation, independent decisions and blocked/reassigned workflows. Business effects stay in their owning modules. | [Delegation](../apps/dashboard/integration/approval-delegations.spec.ts), [leave decisions](../apps/dashboard/integration/leave-actions.spec.ts) |
| [Expenses](modules/expenses.md) | Categories, edited/versioned claims, evidence/duplicate review, correction/return, independent payment release and per-item reconciliation. External bank submission is manual in the initial scope. | [Authoring/review](../apps/dashboard/integration/expense-claims.spec.ts), [payments](../apps/dashboard/integration/expense-payments.spec.ts) |
| [Payroll](modules/payroll.md) | Decimal rules, reviewed policy/compensation/tax inputs, durable calculations, original period comparison, independent finalization, payslips, payments and referenced amendments. | [Calculations](../apps/dashboard/integration-worker/payroll-calculations.spec.ts), [comparison](../apps/dashboard/integration-worker/payroll-comparison.spec.ts), [amendments](payroll-amendments.md) |
| [Documents](modules/documents.md) | Scoped revisions, resumable transfer, bounded inspection, immutable evidence, retention/hold/retirement and private range downloads. | [Transfers](../apps/dashboard/integration/documents.spec.ts), [retention lifecycle](../apps/dashboard/integration/document-retention-lifecycle.spec.ts), [scanner workload](workload.md) |
| [Communications](modules/communications.md) | Audiences, draft/scheduled publication, archive, owned inbox/acknowledgement, device registration and administrative delivery observations. Real FCM message/hint delivery remains external. | [Publication/worker](../apps/dashboard/integration-worker/communications.spec.ts), [delivery](announcement-delivery.md), [native SDK registration](native-push-acceptance.md) |
| [Reporting](modules/reporting.md) | Scoped reports/drill-down/catalogue, owner-bound saved filters, durable multipart export and retirement. The unmodified physical one-hour export grace passed. | [Saved filters](../apps/dashboard/integration/saved-report-filters.spec.ts), [export](../apps/dashboard/integration-worker/report-exports.spec.ts), [elapsed retirement](export-retention-acceptance.md) |
| [Administration](modules/administration.md) | Effective settings/availability, integration observations, jobs, scoped/global audit and private encrypted recovery. | [Worker integration](../apps/dashboard/integration-worker/worker-integrations.spec.ts), [account audit](../apps/dashboard/integration-worker/platform-account-audit.spec.ts), [schema-101 recovery](recovery-set.md) |

Bank-specific submission formats, further tax rule packs and other workload corpora
must be assessed against the explicit initial scope, not inferred as missing work
from historical future-work prose. Do not fabricate business rules or production
configuration to close an acceptance checkbox.

## Native pending-action coverage

Actual Android 35/API acceptance now has a recovery example for each of the six
feature observers. Each row retains its own procedure, server assertions and limits.
These results do not imply every combination was exercised on every target.

| Feature | Accepted native recovery | Stable summary reload | Final server invariant |
| --- | --- | --- | --- |
| Inbox | [Committed read-response loss](native-acceptance.md) | 30 → 30 | One read result at version 1; acknowledgement remains absent |
| Attendance | [Capture response loss/offline cold start](native-attendance-acceptance.md) | 21 → 21 | One event, original timestamp, independent review still pending, zero accepted minutes |
| Overtime | [Plan response loss/offline cold start](native-overtime-acceptance.md) | 39 → 39 | One plan/history entry, version 0, 165 minutes |
| Leave | [Submission response loss/offline cold start](native-leave-acceptance.md) | 33 → 33 after navigation settles | One request/history/approval, 0.5 reserved once, 1.5 available |
| Expenses | [Save response loss/offline cold start](native-expense-acceptance.md) | 30 → 30 before Save; 43 → 43 after loss | One draft/line/history entry, IDR 25,000.25 |
| Documents | [Interrupted upload and failed-inspection review](native-upload-acceptance.md), [partial download](native-download-acceptance.md) | 32 → 32 / 42 → 42 for upload/review | One retained revision; original chunk/manifest and explicit retry/cancellation |

These HRIS fixtures required no generic Starter code change and used Mobile
application `02743a9f`. Later Mobile `678783b3` corrects only English Expense count
copy; local root/Android checks passed. Mobile remains the proving ground for
generic fixes, including its current Apple CI correction. Starter's validated
application foundation remains `9a13791`.

## Current CI ownership

| Repository | Current verification | Source/result boundary |
| --- | --- | --- |
| Platform | Corrected full [40820912608](https://github.com/fajarxfce/hris-platform/actions/runs/40820912608), head `9345c19b`, passed all ten jobs | First run passed tests/restore but failed hidden-path report upload. Run `40820005213` passed backend/recovery and uploaded its checked summary, but failed a browser locator. The correction passed all four backend/dashboard shards, actual API/worker/export integration and schema-101 restore with a downloaded sanitized report. The owned CI ref was removed after verification. [Measurements](measurements/ci-2026-10-29.json) retain each run and failure. |
| Mobile | Full [40815794384](https://github.com/fajarxfce/hris-mobile/actions/runs/40815794384), head `b0a95391`; successful Apple follow-up [40817592306](https://github.com/fajarxfce/hris-mobile/actions/runs/40817592306), head `451a2c4d` | Linux/Android, Windows and Web passed in the full run, whose Apple deadline failure remains recorded. Corrected Apple passed 27 tests, Xcode and reports before Starter port. Hosted application sources match `02743a9f`; later copy-only `678783b3` has separate local root/Android evidence. |
| Starter | Successful Apple [40820482699](https://github.com/fajarxfce/compose-fluent-starter/actions/runs/40820482699), port `1817e45`; prior full [40648662419](https://github.com/fajarxfce/compose-fluent-starter/actions/runs/40648662419), app `9a13791` | Generic CI port followed Mobile proof and independently passed 27 tests, Xcode and artifact upload. Local root passed with 306 desktop cases. Application sources are unchanged; the Apple-only run intentionally skipped verify/Windows. |

All final jobs and downloaded reports were inspected. Prior failures remain
recorded separately from their successful corrections. No production distribution
workflow was invoked by these CI dispatches.

## External acceptance still required

| Work | Current evidence | Missing prerequisite |
| --- | --- | --- |
| Real FCM send and scoped invalidation hint | Actual Firebase SDK token, HRIS registration/response-loss recovery/retirement accepted; controlled worker/delivery observations accepted. | Server-side Firebase `cmp-fajar` credential. A path-only question is already pending; do not repeat it or put private keys in Git/chat. |
| Google authentication exchange | Email/password through Firebase and HRIS accepted; Google chooser/Back accepted. | Actual eligible Google sign-in and Android OAuth/SHA configuration for a real token exchange. A public web client ID is already known. |
| iOS push/auth and physical application links | Portable build/simulator checks and Android URL recovery are separate evidence. | iOS Firebase configuration, APNs/device setup and verified deployed association URLs. |
| Physical location and target deployment | Explicit no-location review, emulator/API and constrained 5,000-employee/scanner workload measurements accepted. Deployment/recovery guidance is implemented. | Available authorized device/environment, real provider configuration and a target-specific execution. A Git push is not live release authorization. |

Current CI and identified generic ports are accepted; no additional concrete
implementation gap was found in this review. Continue these rows when their
inputs become available, and reopen implementation for an observed failure or
documented requirement gap. Mark rows complete only after actual acceptance. The repository should never
claim that a fixture sent a real FCM message, completed Google sign-in, or verified
a physical deployment when it did not.
