import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { JobId } from "../../../jobs/domain/entities/background-job";
import type { WorkPeriodAttempt } from "../../domain/entities/work-period-attempt";
import {
  type WorkPeriodAction,
  type WorkPeriodOverview,
  workPeriodActions,
} from "../../domain/entities/work-period-overview";
import { isWorkPeriodMonth } from "../../domain/policies/work-period-policy";
import type { WorkPeriodOverviewDto } from "../models/work-period-overview-dto";
import { toWorkPeriod } from "./work-period-mapper";

export function toWorkPeriodOverview(
  dto: WorkPeriodOverviewDto,
  company: CompanyId,
  month: string,
): WorkPeriodOverview {
  if (dto.month !== month || !isWorkPeriodMonth(month) || !isCalendarDate(dto.companyDate))
    throw new InvalidHttpResponseError();
  try {
    new Intl.DateTimeFormat("en", { timeZone: dto.companyTimezone });
  } catch {
    throw new InvalidHttpResponseError();
  }
  const period = dto.period === null ? null : toWorkPeriod(dto.period, company, month);
  const attempt: WorkPeriodAttempt | null =
    dto.attempt === null
      ? null
      : Object.freeze({ ...dto.attempt, id: dto.attempt.id.toLowerCase() as JobId });
  if ((period?.jobId ?? null) !== (attempt?.id ?? null)) throw new InvalidHttpResponseError();
  if (attempt !== null) {
    const created = utcInstantMicroseconds(attempt.createdAt);
    const finished =
      attempt.finishedAt === null ? null : utcInstantMicroseconds(attempt.finishedAt);
    const terminal = ["SUCCEEDED", "FAILED", "CANCELLED"].includes(attempt.status);
    if (
      created === null ||
      attempt.completedEmployees > attempt.totalEmployees ||
      terminal !== (attempt.finishedAt !== null) ||
      (attempt.finishedAt !== null && (finished === null || finished < created)) ||
      (period?.status === "CLOSED" &&
        (attempt.status !== "SUCCEEDED" || attempt.completedEmployees !== attempt.totalEmployees))
    )
      throw new InvalidHttpResponseError();
  }
  if (new Set(dto.availableActions).size !== dto.availableActions.length)
    throw new InvalidHttpResponseError();
  const availableActions = dto.availableActions.filter((action): action is WorkPeriodAction =>
    workPeriodActions.includes(action as WorkPeriodAction),
  );
  if (
    (availableActions.includes("close") &&
      (month >= dto.companyDate.slice(0, 7) ||
        (period !== null && !["OPEN", "REVIEW_REQUIRED"].includes(period.status)))) ||
    (availableActions.includes("recover") &&
      (period?.status !== "PROCESSING" ||
        attempt === null ||
        !["FAILED", "CANCELLED"].includes(attempt.status))) ||
    (availableActions.includes("view_job") && attempt === null) ||
    (availableActions.includes("view_snapshots") && period?.status !== "CLOSED")
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId: company,
    month,
    companyDate: dto.companyDate,
    companyTimezone: dto.companyTimezone,
    period,
    attempt,
    availableActions: Object.freeze(availableActions),
  });
}
