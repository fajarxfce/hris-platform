import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { AttendanceAdjustmentChange } from "../../domain/entities/attendance-adjustment-change";
import type { AttendanceAdjustmentDetails } from "../../domain/entities/attendance-adjustment-details";
import type { AttendanceAdjustmentPage } from "../../domain/entities/attendance-adjustment-page";
import type { AttendanceAdjustmentReview } from "../../domain/entities/attendance-adjustment-review";
import type { AttendanceAdjustmentSummary } from "../../domain/entities/attendance-adjustment-summary";
import {
  isAttendanceAdjustmentCursor,
  isAttendanceAdjustmentDate,
} from "../../domain/policies/attendance-adjustment-policy";
import type {
  AttendanceAdjustmentCommandDto,
  AttendanceAdjustmentDetailsDto,
  AttendanceAdjustmentPageDto,
  AttendanceAdjustmentReviewDto,
  AttendanceAdjustmentSummaryDto,
} from "../models/attendance-adjustment-dto";
import { toEmployeeAttendance } from "./attendance-mapper";
import { toWorkPeriod } from "./work-period-mapper";
import { toClosedWorkDay, toWorkPeriodSnapshot } from "./work-period-snapshot-mapper";
import { toWorkforceEmployee } from "./workforce-employee-mapper";

export function toAttendanceAdjustmentReview(
  dto: AttendanceAdjustmentReviewDto,
  company: CompanyId,
  employeeId: EmployeeId,
  date: string,
): AttendanceAdjustmentReview {
  const employee = toWorkforceEmployee(dto.employee, company);
  const period = toWorkPeriod(dto.period, company, date.slice(0, 7));
  if (
    employee.id !== employeeId ||
    period.status !== "CLOSED" ||
    dto.original.workDate !== date ||
    dto.current.workDate !== date
  )
    throw new InvalidHttpResponseError();
  const original = toClosedWorkDay(dto.original),
    current = toClosedWorkDay(dto.current);
  const attendance = toEmployeeAttendance([dto.attendance], company, employeeId, {
    from: date,
    until: date,
  }).days[0];
  if (
    !attendance ||
    current.correctionId !== (attendance.correction?.id ?? null) ||
    JSON.stringify(original.schedule) !== JSON.stringify(current.schedule) ||
    JSON.stringify(original.overtime) !== JSON.stringify(current.overtime) ||
    (dto.adjustmentRevision === 0 && JSON.stringify(original) !== JSON.stringify(current)) ||
    new Set(original.evidenceIds).size !== original.evidenceIds.length ||
    new Set(current.evidenceIds).size !== current.evidenceIds.length
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId: company,
    employee,
    period,
    original,
    current,
    attendance,
    revision: dto.revision,
    adjustmentRevision: dto.adjustmentRevision,
    canCorrect: dto.canCorrect,
    canReviewEvidence: dto.canReviewEvidence,
  });
}
export function toAttendanceAdjustmentSummary(
  dto: AttendanceAdjustmentSummaryDto,
  month: string,
): AttendanceAdjustmentSummary {
  if (
    !isAttendanceAdjustmentDate(dto.workDate) ||
    dto.workDate.slice(0, 7) !== month ||
    utcInstantMicroseconds(dto.recordedAt) === null ||
    !dto.reason.trim()
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...dto,
    id: dto.id.toLowerCase(),
    correctionId: dto.correctionId.toLowerCase(),
    actorId: dto.actorId.toLowerCase(),
  });
}
export function toAttendanceAdjustmentPage(
  dto: AttendanceAdjustmentPageDto,
  month: string,
  before: string | null,
): AttendanceAdjustmentPage {
  const items = dto.items.map((item) => toAttendanceAdjustmentSummary(item, month));
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    items.some(
      (item, index) =>
        item.revision >=
        (index === 0
          ? before === null
            ? Infinity
            : Number(before)
          : (items[index - 1]?.revision ?? 0)),
    ) ||
    (dto.nextCursor !== null &&
      (!isAttendanceAdjustmentCursor(dto.nextCursor) ||
        items.length !== 25 ||
        Number(dto.nextCursor) !== items.at(-1)?.revision))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: dto.nextCursor });
}
export function toAttendanceAdjustmentDetails(
  dto: AttendanceAdjustmentDetailsDto,
  company: CompanyId,
  employee: EmployeeId,
  month: string,
  id: string,
): AttendanceAdjustmentDetails {
  const summary = toAttendanceAdjustmentSummary(dto.summary, month);
  const snapshot = toWorkPeriodSnapshot(dto.snapshot, company, month, employee);
  if (
    summary.id !== id ||
    snapshot.days.find((day) => day.workDate === summary.workDate)?.correctionId !==
      summary.correctionId
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ summary, snapshot });
}
export function toAttendanceAdjustmentCommand(
  change: AttendanceAdjustmentChange,
): AttendanceAdjustmentCommandDto {
  return {
    workDate: change.workDate,
    reviewRevision: change.reviewRevision,
    clockIn: change.clockIn,
    clockOut: change.clockOut,
    breakMinutes: change.breakMinutes,
    reason: change.reason,
    decisions: change.decisions.map((item) => ({ ...item })),
  };
}
