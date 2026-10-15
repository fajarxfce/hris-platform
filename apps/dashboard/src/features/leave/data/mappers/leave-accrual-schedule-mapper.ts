import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  LeaveAccrualSchedule,
  LeaveAccrualScheduleId,
  LeaveAccrualSchedulePage,
} from "../../domain/entities/leave-accrual-schedule";
import type { LeaveAccrualScheduleDetails } from "../../domain/entities/leave-accrual-schedule-details";
import type {
  LeaveAccrualScheduleReview,
  LeaveAccrualScheduleSelection,
} from "../../domain/entities/leave-accrual-schedule-review";
import type { LeavePolicyId } from "../../domain/entities/leave-policy-definition";
import { leaveAccrualSchedulePeriod } from "../../domain/policies/leave-accrual-schedule-policy";
import { isLeaveBatchPeriod } from "../../domain/policies/leave-batch-policy";
import type {
  LeaveAccrualScheduleDetailsDto,
  LeaveAccrualScheduleDto,
  LeaveAccrualScheduleReviewDto,
  LeaveAccrualSchedulesDto,
} from "../models/leave-accrual-schedule-dto";
import type { LeaveReceiptDto } from "../models/leave-receipt-dto";
import { toLeaveBatch } from "./leave-batch-mapper";
import { toLeavePolicyTerms } from "./leave-policy-definition-mapper";
export function toLeaveAccrualSchedule(
  raw: LeaveAccrualScheduleDto,
  companyId: CompanyId,
): LeaveAccrualSchedule {
  if (
    !isLeaveBatchPeriod(raw.firstPeriod) ||
    !isLeaveBatchPeriod(
      leaveAccrualSchedulePeriod(raw.firstPeriod, raw.periodCount - 1, raw.frequency),
    ) ||
    !Number.isFinite(Date.parse(raw.createdAt))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    companyId,
    id: raw.id.toLowerCase() as LeaveAccrualScheduleId,
    typeId: raw.typeId.toLowerCase(),
    actorId: raw.actorId.toLowerCase(),
  });
}
export function toLeaveAccrualSchedules(
  raw: LeaveAccrualSchedulesDto,
  company: CompanyId,
  after: string | null,
): LeaveAccrualSchedulePage {
  const items = raw.items.map((item) => toLeaveAccrualSchedule(item, company));
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
export function toLeaveAccrualScheduleReview(
  raw: LeaveAccrualScheduleReviewDto,
  companyId: CompanyId,
  selection: LeaveAccrualScheduleSelection,
): LeaveAccrualScheduleReview {
  const employees = raw.employeeIds.map((id) => id.toLowerCase());
  if (
    !isCalendarDate(raw.companyDate) ||
    raw.periods.length !== selection.periods ||
    new Set(employees).size !== employees.length
  )
    throw new InvalidHttpResponseError();
  const periods = raw.periods.map((row, index) => {
    const type = row.type;
    if (
      row.period !== leaveAccrualSchedulePeriod(selection.firstPeriod, index, raw.frequency) ||
      !isLeaveBatchPeriod(row.period) ||
      !Number.isFinite(Date.parse(row.dueAt)) ||
      type.id.toLowerCase() !== selection.typeId ||
      !type.active ||
      !isCalendarDate(type.effectiveFrom) ||
      type.effectiveFrom < "1900-01-01" ||
      type.effectiveFrom > `${row.period}-01` ||
      type.version !== raw.policyVersion ||
      type.appliedRevision > type.version ||
      type.accrual?.frequency !== raw.frequency ||
      type.code !== raw.periods[0]?.type.code
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({
      period: row.period,
      dueAt: row.dueAt,
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
  });
  return Object.freeze({
    companyId,
    selection: Object.freeze({ ...selection }),
    companyDate: raw.companyDate,
    timezone: raw.timezone,
    frequency: raw.frequency,
    policyVersion: raw.policyVersion,
    periods: Object.freeze(periods),
    employeeIds: Object.freeze(employees),
  });
}
export function toLeaveAccrualScheduleDetails(
  raw: LeaveAccrualScheduleDetailsDto,
  company: CompanyId,
  id: LeaveAccrualScheduleId,
): LeaveAccrualScheduleDetails {
  const schedule = toLeaveAccrualSchedule(raw.schedule, company);
  if (schedule.id !== id || raw.periods.length !== schedule.periodCount)
    throw new InvalidHttpResponseError();
  const ids = new Set<string>(),
    jobs = new Set<string>();
  let previousDue = Date.parse(schedule.createdAt);
  const periods = raw.periods.map((row, index) => {
    const batch = toLeaveBatch(row.batch, company),
      due = Date.parse(row.scheduledFor);
    if (
      !Number.isFinite(due) ||
      due < previousDue ||
      batch.kind !== "ACCRUAL" ||
      batch.typeId !== schedule.typeId ||
      batch.period !==
        leaveAccrualSchedulePeriod(schedule.firstPeriod, index, schedule.frequency) ||
      batch.policyVersion !== schedule.policyVersion ||
      batch.policy.code !== schedule.typeCode ||
      batch.policy.accrual?.frequency !== schedule.frequency ||
      batch.timezone !== schedule.timezone ||
      batch.actorId !== schedule.actorId ||
      batch.createdAt !== schedule.createdAt ||
      batch.reason !== schedule.reason ||
      batch.totalEmployees !== schedule.totalEmployees ||
      row.completedEmployees > batch.totalEmployees ||
      (batch.status === "COMPLETED") !== (row.jobStatus === "SUCCEEDED") ||
      (batch.status === "COMPLETED" && row.completedEmployees !== batch.totalEmployees) ||
      ids.has(batch.id) ||
      jobs.has(batch.jobId)
    )
      throw new InvalidHttpResponseError();
    previousDue = due;
    ids.add(batch.id);
    jobs.add(batch.jobId);
    return Object.freeze({
      scheduledFor: row.scheduledFor,
      batch,
      jobStatus: row.jobStatus,
      completedEmployees: row.completedEmployees,
    });
  });
  return Object.freeze({ schedule, periods: Object.freeze(periods) });
}
export function toLeaveAccrualScheduleReceipt(raw: LeaveReceiptDto, id: LeaveAccrualScheduleId) {
  if (raw.id.toLowerCase() !== id || raw.version !== 0) throw new InvalidHttpResponseError();
  return Object.freeze({ id, version: raw.version });
}
