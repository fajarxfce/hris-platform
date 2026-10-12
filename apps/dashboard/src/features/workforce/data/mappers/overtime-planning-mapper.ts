import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { OvertimePlan } from "../../domain/entities/overtime-plan";
import type { OvertimePlanningReview } from "../../domain/entities/overtime-planning-review";
import { isOvertimePlanningDate } from "../../domain/policies/overtime-planning-policy";
import type { OvertimePlanDto } from "../models/overtime-plan-dto";
import type { OvertimePlanningReviewDto } from "../models/overtime-planning-dto";
import type { OvertimeReceiptDto } from "../models/overtime-receipt-dto";
import { toScheduledDay } from "./scheduled-day-mapper";
import { toWorkforceEmployee } from "./workforce-employee-mapper";

export function toOvertimePlanningReview(
  dto: OvertimePlanningReviewDto,
  company: CompanyId,
  employeeId: EmployeeId,
  workDate: string,
): OvertimePlanningReview {
  const employee = toWorkforceEmployee(dto.employee, company);
  const schedule = toScheduledDay(dto.schedule, workDate);
  if (
    employee.id !== employeeId ||
    dto.workDate !== workDate ||
    !isOvertimePlanningDate(workDate, dto.companyDate) ||
    schedule.kind === "UNASSIGNED"
  )
    throw new InvalidHttpResponseError();
  try {
    new Intl.DateTimeFormat("en", { timeZone: dto.timezone });
  } catch {
    throw new InvalidHttpResponseError();
  }
  return Object.freeze({
    employee,
    workDate,
    companyDate: dto.companyDate,
    timezone: dto.timezone,
    schedule,
    revision: dto.revision,
  });
}

export function toOvertimePlanDto(plan: OvertimePlan): OvertimePlanDto {
  return {
    id: plan.id,
    employeeId: plan.employeeId,
    expectedEmploymentVersion: plan.expectedEmploymentVersion,
    workDate: plan.workDate,
    requested: { ...plan.requested },
    reason: plan.reason,
    planningRevision: plan.planningRevision,
  };
}

export function toOvertimePlanReceipt(dto: OvertimeReceiptDto, plan: OvertimePlan) {
  if (dto.id.toLowerCase() !== plan.id || dto.version !== 0) throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: 0 });
}
