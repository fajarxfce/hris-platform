import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { OvertimePlanningReview } from "./overtime-planning-review";

/** Keep only reviewed facts needed by a pending command, without names or shift metadata. */
export type OvertimePlanContext = Readonly<{
  companyId: CompanyId;
  employeeId: EmployeeId;
  employmentVersion: number;
  workDate: string;
  companyDate: string;
  timezone: string;
  revision: string;
  schedule: Readonly<{ kind: "OFF" } | { kind: "WORK"; startsAt: string; endsAt: string }>;
}>;

export function overtimePlanContext(review: OvertimePlanningReview): OvertimePlanContext {
  return Object.freeze({
    companyId: review.employee.companyId,
    employeeId: review.employee.id,
    employmentVersion: review.employee.version,
    workDate: review.workDate,
    companyDate: review.companyDate,
    timezone: review.timezone,
    revision: review.revision,
    schedule: Object.freeze(
      review.schedule.kind === "OFF"
        ? { kind: "OFF" as const }
        : {
            kind: "WORK" as const,
            startsAt: review.schedule.startsAt,
            endsAt: review.schedule.endsAt,
          },
    ),
  });
}
