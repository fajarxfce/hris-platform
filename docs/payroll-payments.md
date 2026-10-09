# Payroll payments

Finance prepares payment batches from immutable finalized payroll assessments. The backend copies each employee's retained labels, tax month, planned payment date, and positive take-home amount; a request cannot replace those amounts. A zero take-home assessment has no payable transfer.

All finance endpoints require `payroll.pay` and current recent authentication/MFA. They revalidate original/live permissions and credentials under payment, people, company, membership, and account guards. Payroll administration, calculation, or finalization does not imply payment access. A preparer may be a payroll beneficiary, because salary inputs and finalization are independently reviewed; a different account must release the batch.

## Commands

Use `/api/v1/companies/{companyId}/payroll/payments` and a UUID `Idempotency-Key` for every command. Keep the same resource/item IDs, key, and payload when a response is lost. Changed intent gets a new key. Release, cancellation, and reconciliation also require `expectedVersion` and `reason`.

| Method and path | Behavior |
| --- | --- |
| `PUT /{batchId}` | Prepare 1–100 distinct assessment instructions with `title`, `items`, and `reason`. Each item has a client-generated `id`, `assessmentId`, and `destination` containing `bankCode`, `accountNumber`, and `accountName`. |
| `POST /{batchId}/release` | Independently approve immutable instructions; the batch becomes `RELEASED` and its items `PENDING`. |
| `POST /{batchId}/cancel` | Cancel a `PREPARED` batch before release. Instructions and history remain retained. |
| `POST /{batchId}/reconcile` | Record 1–100 distinct pending item outcomes; unresolved items remain pending. The batch closes once all items have a confirmed outcome. |

Each reconciliation result contains `itemId`, `status`, `occurredAt`, `reason`, optional `transactionReference`, and `confirmedNoTransfer`. `SUCCEEDED` requires a bank transaction reference and `confirmedNoTransfer: false`. `FAILED` requires a null reference and `confirmedNoTransfer: true`. Confirmation time must be between release and the current server time.

A timeout or unknown bank result stays `PENDING`; it must not become a confirmed failure. Prepared, pending, and successful items reserve their assessment. Only a cancelled instruction or a confirmed no-transfer failure permits a new batch. Existing results and destinations cannot be rewritten. At most twenty attempts are retained per assessment, one hundred batches may remain open, and a company retains at most ten thousand batches.

Confirmed transaction references share a company-scoped registry with expense reimbursements. One reference cannot settle two salaries or a salary and a reimbursement. Concurrent conflicts roll back the item, batch, evidence, progress version, audit/outbox, and operation receipt together. Migration backfills existing successful reimbursements without rewriting their history; an account unable to read all companies cannot silently perform a partial backfill.

## Reads and export

`GET /payables` lists unreserved positive assessments below the attempt limit. `GET /` lists batches, optionally filtered by status. Both accept `from`, `until`, `after`, and `limit`. Dates describe company-local publication/creation days, with a maximum 366-day range and default/maximum pages of 50/200. Follow the returned cursor; it is not a synchronization position.

`GET /{batchId}`, `/history`, and `/results` expose authorized finance detail and immutable evidence. `GET /{batchId}/export` returns a deterministic CSV worksheet only while the batch is released and every item is pending. It retains stable item references, escapes spreadsheet formulas and CSV content, and preserves leading account-number zeros. Repeated downloads do not create a payment or settlement record. Bank-specific formats and direct bank submission are separate integrations.

## Employee progress and synchronization

`GET /api/v1/companies/{companyId}/payroll/payslips/{assessmentId}/payments` exposes a separate progress object with `assessmentId`, `version`, and up to twenty attempts. An attempt contains batch/item IDs, status, creation/release/resolution timestamps. It omits bank destinations, transaction references, reasons, and other employees. Access follows `payroll.self.read` ownership or explicit company-wide `payroll.read`.

Before any attempt, progress has version `0`. Preparation and every item transition advance its assessment's progress version. The immutable payslip stays at version `0`; payment status never rewrites historical payroll calculation. The `PAYROLL_PAYMENTS` sync collection carries owned assessment IDs and progress versions in the same transaction as the payment change. See [mobile synchronization](synchronization.md) and [client error/retry policy](mobile-api.md).

Stable domain failures include `payroll_assessment_not_payable`, `payroll_assessment_payment_reserved`, `payroll_payment_attempt_limit`, `payroll_payment_capacity`, `payroll_payment_not_prepared`, `payroll_payment_not_released`, `payroll_payment_item_not_pending`, `payroll_payment_instructions_unavailable`, and `stale_version`. Clients translate codes and stop or resolve conflicts explicitly; they never silently replace an observed version or repeat an unknown bank transfer.
