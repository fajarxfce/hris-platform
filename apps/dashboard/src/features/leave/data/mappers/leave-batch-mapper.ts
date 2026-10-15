import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveBatch, LeaveBatchId, LeaveBatchPage } from "../../domain/entities/leave-batch";
import type { LeaveBatchDetails } from "../../domain/entities/leave-batch-details";
import type {
  LeaveBatchReview,
  LeaveBatchSelection,
} from "../../domain/entities/leave-batch-review";
import type { LeavePolicyId } from "../../domain/entities/leave-policy-definition";
import { isLeaveBatchPeriod } from "../../domain/policies/leave-batch-policy";
import type {
  LeaveBatchDetailsDto,
  LeaveBatchDto,
  LeaveBatchPageDto,
  LeaveBatchReviewDto,
} from "../models/leave-batch-dto";
import type { LeaveReceiptDto } from "../models/leave-receipt-dto";
import { toLeavePolicyTerms } from "./leave-policy-definition-mapper";
export function toLeaveBatch(raw: LeaveBatchDto, companyId: CompanyId): LeaveBatch {
  if (
    !isLeaveBatchPeriod(raw.period) ||
    (raw.kind === "YEAR_CLOSE" && !raw.period.endsWith("-12")) ||
    raw.typeId.toLowerCase() !== raw.policy.typeId.toLowerCase() ||
    raw.policy.revision > raw.policyVersion
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    id: raw.id.toLowerCase() as LeaveBatchId,
    companyId,
    typeId: raw.typeId.toLowerCase(),
    actorId: raw.actorId.toLowerCase(),
    jobId: raw.jobId.toLowerCase(),
    policy: Object.freeze({
      ...toLeavePolicyTerms(raw.policy),
      typeId: raw.policy.typeId.toLowerCase(),
      code: raw.policy.code,
      revision: raw.policy.revision,
    }),
  });
}
export function toLeaveBatches(
  raw: LeaveBatchPageDto,
  company: CompanyId,
  after: string | null,
): LeaveBatchPage {
  const items = raw.items.map((row) => toLeaveBatch(row, company));
  let previous = after;
  for (const item of items) {
    if (previous !== null && item.id <= previous) throw new InvalidHttpResponseError();
    previous = item.id;
  }
  const cursor = raw.nextCursor?.toLowerCase() ?? null;
  if (cursor !== null && (items.length !== 20 || cursor !== items.at(-1)?.id))
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: cursor });
}
export function toLeaveBatchReview(
  raw: LeaveBatchReviewDto,
  companyId: CompanyId,
  selected: LeaveBatchSelection,
): LeaveBatchReview {
  const type = raw.type,
    employeeIds = raw.employeeIds.map((id) => id.toLowerCase()),
    effectiveOn = `${raw.period}-${raw.kind === "ACCRUAL" ? "01" : "31"}`;
  if (
    raw.kind !== selected.kind ||
    raw.period !== selected.period ||
    type.id.toLowerCase() !== selected.typeId ||
    !isCalendarDate(raw.companyDate) ||
    !isCalendarDate(type.effectiveFrom) ||
    type.effectiveFrom < "1900-01-01" ||
    type.effectiveFrom > effectiveOn ||
    type.appliedRevision > type.version ||
    employeeIds.length !== raw.totalEmployees ||
    new Set(employeeIds).size !== employeeIds.length ||
    (raw.kind === "YEAR_CLOSE" && raw.period.slice(0, 4) >= raw.companyDate.slice(0, 4)) ||
    (raw.kind === "ACCRUAL" &&
      (!type.active ||
        !type.accrual ||
        type.accrual.frequency === "MANUAL" ||
        raw.period > raw.companyDate.slice(0, 7) ||
        (type.accrual.frequency === "MONTHLY" && raw.period >= raw.companyDate.slice(0, 7))))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...selected,
    companyId,
    companyDate: raw.companyDate,
    timezone: raw.timezone,
    employeeIds: Object.freeze(employeeIds),
    type: Object.freeze({
      ...toLeavePolicyTerms(type),
      id: type.id.toLowerCase() as LeavePolicyId,
      companyId,
      code: type.code,
      active: type.active,
      effectiveFrom: type.effectiveFrom,
      version: type.version,
      appliedRevision: type.appliedRevision,
    }),
  });
}
export function toLeaveBatchDetails(
  raw: LeaveBatchDetailsDto,
  company: CompanyId,
  id: LeaveBatchId,
  after: string | null,
): LeaveBatchDetails {
  const batch = toLeaveBatch(raw.batch, company),
    counts = raw.counts;
  const attempts = raw.attempts.map((row) =>
    Object.freeze({ ...row, jobId: row.jobId.toLowerCase() }),
  );
  const current = attempts.at(-1);
  if (
    batch.id !== id ||
    !current ||
    raw.job.jobId.toLowerCase() !== batch.jobId ||
    current.jobId !== batch.jobId ||
    counts.applied + counts.unchanged + counts.skipped + counts.failed !== counts.completed ||
    counts.completed > batch.totalEmployees ||
    new Set(attempts.map((row) => row.jobId)).size !== attempts.length ||
    attempts.some(
      (row, index) =>
        row.number !== index + 1 ||
        row.baseCompleted > counts.completed ||
        (index === 0
          ? row.baseCompleted !== 0
          : row.baseCompleted < (attempts[index - 1]?.baseCompleted ?? 0)),
    ) ||
    raw.job.totalItems !== batch.totalEmployees - current.baseCompleted + 1 ||
    raw.job.completedItems !==
      counts.completed - current.baseCompleted + (batch.status === "COMPLETED" ? 1 : 0) ||
    (batch.status === "COMPLETED") !== (raw.job.status === "SUCCEEDED") ||
    (batch.status === "COMPLETED" && counts.completed !== batch.totalEmployees)
  )
    throw new InvalidHttpResponseError();
  const employees = new Map(
    raw.employees.map((row) => [
      row.id.toLowerCase(),
      Object.freeze({ ...row, id: row.id.toLowerCase() }),
    ]),
  );
  const seen = new Set<string>();
  let previous = after === null ? 0 : Number(after);
  const items = raw.results.items.map((row) => {
    const employee = employees.get(row.employeeId.toLowerCase()),
      successful = row.status === "APPLIED" || row.status === "UNCHANGED";
    if (
      !employee ||
      seen.has(employee.id) ||
      row.ordinal !== previous + 1 ||
      row.ordinal > counts.completed ||
      !attempts.some((attempt) => attempt.jobId === row.jobId.toLowerCase()) ||
      (successful
        ? row.resourceId === null || row.failureCode !== null
        : row.resourceId !== null || row.failureCode === null)
    )
      throw new InvalidHttpResponseError();
    seen.add(employee.id);
    previous = row.ordinal;
    return Object.freeze({
      ordinal: row.ordinal,
      employee,
      jobId: row.jobId.toLowerCase(),
      status: row.status,
      resourceId: row.resourceId?.toLowerCase() ?? null,
      failureCode: row.failureCode,
      parameters: Object.freeze({ ...row.parameters }),
      completedAt: row.completedAt,
    });
  });
  const cursor = raw.results.nextCursor;
  if (
    employees.size !== raw.employees.length ||
    employees.size !== seen.size ||
    (cursor !== null &&
      (items.length !== 20 ||
        cursor !== String(items.at(-1)?.ordinal) ||
        Number(cursor) >= counts.completed)) ||
    (cursor === null && previous < counts.completed) ||
    (raw.availableActions.length > 0 &&
      (batch.status === "COMPLETED" ||
        !["FAILED", "CANCELLED"].includes(raw.job.status) ||
        attempts.length >= 8))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    batch,
    counts: Object.freeze({ ...counts }),
    attempts: Object.freeze(attempts),
    job: Object.freeze({
      id: raw.job.jobId.toLowerCase(),
      status: raw.job.status,
      completedItems: raw.job.completedItems,
      totalItems: raw.job.totalItems,
      attempts: raw.job.attempts,
      version: raw.job.version,
      cancellationRequested: raw.job.cancellationRequested,
      failureCode: raw.job.failureCode,
    }),
    results: Object.freeze({ items: Object.freeze(items), nextCursor: cursor }),
    actions: Object.freeze([...raw.availableActions]),
  });
}
export function toLeaveBatchReceipt(raw: LeaveReceiptDto, id: LeaveBatchId, version: number) {
  if (raw.id.toLowerCase() !== id.toLowerCase() || raw.version !== version)
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: raw.id.toLowerCase(), version: raw.version });
}
