import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  LeaveCarryExpiry,
  LeaveCarryExpiryId,
  LeaveCarryExpiryPage,
} from "../../domain/entities/leave-carry-expiry";
import type { LeaveCarryExpiryDetails } from "../../domain/entities/leave-carry-expiry-details";
import type {
  LeaveCarryExpiryDetailsDto,
  LeaveCarryExpiryDto,
  LeaveCarryExpiryPageDto,
} from "../models/leave-carry-expiry-dto";
import type { LeaveReceiptDto } from "../models/leave-receipt-dto";
export function toLeaveCarryExpiry(
  raw: LeaveCarryExpiryDto,
  companyId: CompanyId,
): LeaveCarryExpiry {
  if (
    raw.validThrough.slice(0, 4) !== String(raw.year) ||
    Date.parse(raw.expiresAt) <= Date.parse(raw.createdAt) ||
    raw.completedEmployees > raw.totalEmployees ||
    (raw.status === "SUCCEEDED" && raw.completedEmployees !== raw.totalEmployees) ||
    (raw.sourceKind === "BATCH"
      ? raw.employeeId !== null
      : raw.employeeId === null || raw.totalEmployees !== 1)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    companyId,
    id: raw.id.toLowerCase() as LeaveCarryExpiryId,
    sourceId: raw.sourceId.toLowerCase(),
    employeeId: raw.employeeId?.toLowerCase() ?? null,
    typeId: raw.typeId.toLowerCase(),
    actorId: raw.actorId.toLowerCase(),
    jobId: raw.jobId.toLowerCase(),
  });
}
export function toLeaveCarryExpiries(
  raw: LeaveCarryExpiryPageDto,
  company: CompanyId,
  after: string | null,
): LeaveCarryExpiryPage {
  const items = raw.items.map((item) => toLeaveCarryExpiry(item, company));
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
export function toLeaveCarryExpiryDetails(
  raw: LeaveCarryExpiryDetailsDto,
  company: CompanyId,
  id: LeaveCarryExpiryId,
  after: string | null,
): LeaveCarryExpiryDetails {
  const expiry = toLeaveCarryExpiry(raw.expiry, company);
  const attempts = raw.attempts.map((item) =>
    Object.freeze({ ...item, jobId: item.jobId.toLowerCase() }),
  );
  const current = attempts.at(-1),
    scheduledFor = raw.job.scheduledFor;
  if (
    expiry.id !== id ||
    !current ||
    attempts.length !== expiry.version + 1 ||
    current.jobId !== expiry.jobId ||
    raw.job.id.toLowerCase() !== expiry.jobId ||
    expiry.status !== raw.job.status ||
    expiry.failureCode !== raw.job.failureCode ||
    new Set(attempts.map((item) => item.jobId)).size !== attempts.length ||
    attempts.some(
      (item, index) =>
        item.number !== index + 1 ||
        item.baseCompleted > expiry.completedEmployees ||
        (index === 0
          ? item.baseCompleted !== 0
          : item.baseCompleted < (attempts[index - 1]?.baseCompleted ?? 0)),
    ) ||
    raw.job.totalItems !== expiry.totalEmployees - current.baseCompleted + 1 ||
    raw.job.completedItems !==
      expiry.completedEmployees - current.baseCompleted + (expiry.status === "SUCCEEDED" ? 1 : 0) ||
    scheduledFor === null ||
    Date.parse(scheduledFor) < Date.parse(expiry.expiresAt) ||
    (raw.availableActions.length > 0 &&
      (!["FAILED", "CANCELLED"].includes(expiry.status) || expiry.version >= 7))
  )
    throw new InvalidHttpResponseError();
  const employees = new Map(
    raw.employees.map((item) => [
      item.id.toLowerCase(),
      Object.freeze({ ...item, id: item.id.toLowerCase() }),
    ]),
  );
  const seen = new Set<string>();
  let previous = Number(after ?? 0);
  const items = raw.results.items.map((item) => {
    const employee = employees.get(item.employeeId.toLowerCase());
    const attempt = attempts.findIndex((value) => value.jobId === item.jobId.toLowerCase());
    if (
      !employee ||
      seen.has(employee.id) ||
      item.ordinal !== previous + 1 ||
      item.ordinal > expiry.completedEmployees ||
      attempt < 0 ||
      item.ordinal <= (attempts[attempt]?.baseCompleted ?? 0) ||
      item.ordinal > (attempts[attempt + 1]?.baseCompleted ?? expiry.completedEmployees) ||
      (item.status === "EXPIRED"
        ? Number(item.expiredDays) <= 0 || item.ledgerId === null
        : Number(item.expiredDays) !== 0 || item.ledgerId !== null)
    )
      throw new InvalidHttpResponseError();
    previous = item.ordinal;
    seen.add(employee.id);
    return Object.freeze({
      ordinal: item.ordinal,
      employee,
      jobId: item.jobId.toLowerCase(),
      status: item.status,
      expiredDays: item.expiredDays,
      ledgerId: item.ledgerId?.toLowerCase() ?? null,
      recordedAt: item.recordedAt,
    });
  });
  const cursor = raw.results.nextCursor;
  if (
    employees.size !== raw.employees.length ||
    employees.size !== items.length ||
    items.length !== Math.min(20, Math.max(0, expiry.completedEmployees - Number(after ?? 0))) ||
    cursor !== (previous < expiry.completedEmployees ? String(previous) : null)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    expiry,
    attempts: Object.freeze(attempts),
    job: Object.freeze({ ...raw.job, id: raw.job.id.toLowerCase(), scheduledFor }),
    results: Object.freeze({ items: Object.freeze(items), nextCursor: cursor }),
    actions: Object.freeze([...raw.availableActions]),
  });
}
export function toLeaveCarryExpiryReceipt(
  raw: LeaveReceiptDto,
  id: LeaveCarryExpiryId,
  version: number,
) {
  if (raw.id.toLowerCase() !== id || raw.version !== version) throw new InvalidHttpResponseError();
  return Object.freeze({ id, version });
}
