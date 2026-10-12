import { toWorkPeriodOverview } from "../data/mappers/work-period-overview-mapper";
import type { WorkPeriodDto } from "../data/models/work-period-dto";
import type { WorkPeriodOverviewDto } from "../data/models/work-period-overview-dto";
import type { WorkPeriodSnapshotDto } from "../data/models/work-period-snapshot-dto";
import type { WorkPeriodStatus } from "../domain/entities/work-period";
import type { WorkPeriodIntent } from "../domain/entities/work-period-action-context";
import { reviewWorkPeriodAction } from "../domain/policies/work-period-action-policy";
import {
  calendarDtoFixture,
  company,
  employeeDtoFixture,
  employeeId,
  identifier,
} from "./calendar-fixture";

export { company, employeeId, identifier };
export const periodMonth = "2026-09";
export const periodId = identifier(810);
export const periodJobId = identifier(811);
export const periodAccess = {
  companyId: company,
  permissions: ["workforce.read", "workforce.close"],
};

export function workPeriodDtoFixture(status: WorkPeriodStatus = "OPEN"): WorkPeriodDto {
  return {
    id: periodId,
    month: periodMonth,
    status,
    timezone: status === "OPEN" ? null : "Asia/Jakarta",
    jobId: status === "OPEN" ? null : periodJobId,
    version: status === "OPEN" ? 0 : status === "PROCESSING" ? 1 : 2,
    startedAt: status === "OPEN" ? null : "2026-10-12T01:00:00Z",
    closedAt: status === "CLOSED" ? "2026-10-12T01:00:10Z" : null,
    failureCode: status === "REVIEW_REQUIRED" ? "attendance_verification_required" : null,
  };
}
export function workPeriodOverviewDtoFixture(
  status: WorkPeriodStatus = "OPEN",
): WorkPeriodOverviewDto {
  return {
    month: periodMonth,
    companyDate: "2026-10-13",
    companyTimezone: "Asia/Jakarta",
    period: workPeriodDtoFixture(status),
    attempt:
      status === "OPEN"
        ? null
        : {
            id: periodJobId,
            status:
              status === "CLOSED"
                ? "SUCCEEDED"
                : status === "REVIEW_REQUIRED"
                  ? "FAILED"
                  : "RUNNING",
            totalEmployees: 10,
            completedEmployees: status === "CLOSED" ? 10 : 3,
            cancellationRequested: false,
            failureCode: status === "REVIEW_REQUIRED" ? "attendance_verification_required" : null,
            createdAt: "2026-10-12T01:00:00Z",
            finishedAt: status === "PROCESSING" ? null : "2026-10-12T01:00:10Z",
          },
    availableActions:
      status === "OPEN"
        ? ["close"]
        : status === "REVIEW_REQUIRED"
          ? ["close", "view_job"]
          : status === "CLOSED"
            ? ["view_job", "view_snapshots"]
            : ["view_job"],
  };
}
export const workPeriodOverviewFixture = (status: WorkPeriodStatus = "OPEN") =>
  toWorkPeriodOverview(workPeriodOverviewDtoFixture(status), company, periodMonth);

export function workPeriodContextFixture(intent: WorkPeriodIntent = "close") {
  const raw = workPeriodOverviewDtoFixture(intent === "close" ? "OPEN" : "PROCESSING");
  if (intent === "recover" && raw.attempt) {
    raw.attempt.status = "FAILED";
    raw.attempt.finishedAt = "2026-10-12T01:00:10Z";
    raw.availableActions = ["recover", "view_job"];
  }
  const result = reviewWorkPeriodAction(toWorkPeriodOverview(raw, company, periodMonth), intent);
  if (!result.ok) throw new Error("Invalid work period fixture");
  return result.value;
}

export function workPeriodSnapshotDtoFixture(): WorkPeriodSnapshotDto {
  const calendar = calendarDtoFixture({ from: "2026-09-01", until: "2026-09-30" });
  const off = {
    workDate: "2026-09-03",
    kind: "OFF" as const,
    shift: null,
    startsAt: null,
    endsAt: null,
    plannedMinutes: 0,
    origin: "HOLIDAY" as const,
    originVersion: 0,
    holidayId: identifier(812),
  };
  return {
    employeeId,
    month: periodMonth,
    employee: employeeDtoFixture(),
    period: workPeriodDtoFixture("CLOSED"),
    days: calendar.calendar.days.map((schedule, index) => ({
      workDate: schedule.workDate,
      fact:
        index === 0
          ? "WORKED"
          : index === 1
            ? "ABSENCE_RECORDED"
            : index === 2
              ? "OFF"
              : index === 3
                ? "UNASSIGNED"
                : "UNRECORDED",
      acceptedMinutes: index === 0 ? 450 : 0,
      schedule:
        index === 2
          ? off
          : index === 3
            ? {
                ...off,
                workDate: schedule.workDate,
                kind: "UNASSIGNED",
                origin: null,
                originVersion: null,
                holidayId: null,
              }
            : schedule,
      evidenceIds: [],
      correctionId: index < 2 ? identifier(820 + index) : null,
      overtime:
        index === 2
          ? [
              {
                requestId: identifier(830),
                revision: 2,
                approvedMinutes: 135,
                schedule: off,
                actual: {
                  startsAt: "2026-09-03T07:15:00Z",
                  endsAt: "2026-09-03T09:45:00Z",
                  breakMinutes: 15,
                  workedMinutes: 135,
                },
              },
            ]
          : [],
    })),
  };
}
