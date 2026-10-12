import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { ClosedWorkDay } from "../../domain/entities/closed-work-day";
import type { OvertimeRequestId } from "../../domain/entities/overtime-request";
import type { WorkPeriodSnapshot } from "../../domain/entities/work-period-snapshot";
import { workPeriodDates } from "../../domain/policies/work-period-policy";
import type { WorkPeriodSnapshotDto } from "../models/work-period-snapshot-dto";
import { toOvertimeInterval } from "./overtime-interval-mapper";
import { toScheduledDay } from "./scheduled-day-mapper";
import { toWorkPeriod } from "./work-period-mapper";
import { toWorkforceEmployee } from "./workforce-employee-mapper";

export function toWorkPeriodSnapshot(
  dto: WorkPeriodSnapshotDto,
  company: CompanyId,
  month: string,
  employeeId: EmployeeId,
): WorkPeriodSnapshot {
  const employee = toWorkforceEmployee(dto.employee, company);
  const period = toWorkPeriod(dto.period, company, month);
  const dates = workPeriodDates(month);
  if (
    employee.id !== employeeId ||
    dto.employeeId.toLowerCase() !== employeeId ||
    dto.month !== month ||
    period.status !== "CLOSED" ||
    dto.days.length !== dates.length
  )
    throw new InvalidHttpResponseError();
  const days = dto.days.map((day, index): ClosedWorkDay => {
    if (day.workDate !== dates[index]) throw new InvalidHttpResponseError();
    const schedule = toScheduledDay(day.schedule, day.workDate);
    if (
      (day.fact !== "WORKED" && day.acceptedMinutes !== 0) ||
      (day.fact === "WORKED" && day.evidenceIds.length === 0 && day.correctionId === null) ||
      (day.fact === "ABSENCE_RECORDED" && day.correctionId === null) ||
      (day.fact === "UNRECORDED" && schedule.kind !== "WORK") ||
      (day.fact === "OFF" && schedule.kind !== "OFF") ||
      (day.fact === "UNASSIGNED" && schedule.kind !== "UNASSIGNED")
    )
      throw new InvalidHttpResponseError();
    const overtime = day.overtime.map((request) => {
      const actual = toOvertimeInterval(request.actual),
        original = toScheduledDay(request.schedule, day.workDate);
      if (request.approvedMinutes !== actual.workedMinutes || original.kind === "UNASSIGNED")
        throw new InvalidHttpResponseError();
      return Object.freeze({
        ...request,
        requestId: request.requestId.toLowerCase() as OvertimeRequestId,
        actual,
        schedule: original,
      });
    });
    return Object.freeze({
      ...day,
      schedule,
      evidenceIds: Object.freeze(day.evidenceIds.map((id) => id.toLowerCase())),
      correctionId: day.correctionId?.toLowerCase() ?? null,
      overtime: Object.freeze(overtime),
    });
  });
  const evidence = days.flatMap((day) => day.evidenceIds),
    corrections = days.flatMap((day) => (day.correctionId === null ? [] : [day.correctionId])),
    overtime = days.flatMap((day) => day.overtime.map((request) => request.requestId));
  if (
    new Set(evidence).size !== evidence.length ||
    new Set(corrections).size !== corrections.length ||
    overtime.length > 128 ||
    new Set(overtime).size !== overtime.length
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ employee, period, days: Object.freeze(days) });
}
