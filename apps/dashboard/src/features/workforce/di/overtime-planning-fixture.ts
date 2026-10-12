import { toOvertimePlanningReview } from "../data/mappers/overtime-planning-mapper";
import type { OvertimePlanningReviewDto } from "../data/models/overtime-planning-dto";
import { employeeDtoFixture } from "./calendar-fixture";
import { company, employeeId, overtimeDate, overtimeDtoFixture } from "./overtime-fixture";

export function overtimePlanningDtoFixture(): OvertimePlanningReviewDto {
  return {
    employee: employeeDtoFixture(),
    workDate: overtimeDate,
    companyDate: "2026-10-13",
    timezone: "Asia/Jakarta",
    schedule: overtimeDtoFixture("PLANNED").schedule,
    revision: "a".repeat(64),
  };
}

export const overtimePlanningFixture = () =>
  toOvertimePlanningReview(overtimePlanningDtoFixture(), company, employeeId, overtimeDate);
