import { toRosterBulkReview } from "../data/mappers/roster-bulk-mapper";
import type { RosterBulkReviewDto } from "../data/models/roster-bulk-dto";
import type { RosterBulkChange } from "../domain/entities/roster-bulk-change";
import type { RosterBulkSelection } from "../domain/entities/roster-bulk-selection";
import {
  calendarDtoFixture,
  company,
  employeeDtoFixture,
  employeeId,
  identifier,
} from "./calendar-fixture";

export const bulkSelection = (): RosterBulkSelection => ({
  employeeIds: [employeeId, identifier(502)],
  dates: ["2026-10-12", "2026-10-13"],
  mode: "DAY_OFF",
  shift: null,
});
export const bulkChange = (): RosterBulkChange => ({
  companyId: company,
  selection: bulkSelection(),
  reviewRevision: "b".repeat(64),
  reason: "Coverage update",
});
export function bulkDtoFixture(selection = bulkSelection()): RosterBulkReviewDto {
  return {
    employeeIds: [...selection.employeeIds],
    dates: [...selection.dates],
    mode: selection.mode,
    shiftId: selection.shift?.id ?? null,
    shiftVersion: selection.shift?.version ?? null,
    revision: "b".repeat(64),
    changeCount: selection.employeeIds.length * selection.dates.length,
    employees: selection.employeeIds.map((id, index) => ({
      employee: {
        ...employeeDtoFixture(),
        id,
        legalName: `Employee ${index + 1}`,
        employeeNumber: `EMP-${index + 1}`,
      },
      days: selection.dates.map((workDate) => {
        const day = calendarDtoFixture({ from: workDate, until: workDate }).calendar.days[0];
        if (!day) throw new Error("Missing fixture day");
        const shift = day.shift;
        return {
          workDate,
          expectedVersion: selection.mode === "CALENDAR" ? 2 : null,
          overrideActive: selection.mode === "CALENDAR",
          changed: true,
          before:
            selection.mode === "CALENDAR"
              ? { ...day, origin: "ROSTER" as const, originVersion: 2 }
              : day,
          after:
            selection.mode === "CALENDAR"
              ? day
              : selection.mode === "SHIFT" && selection.shift && shift
                ? {
                    ...day,
                    shift: { ...shift, id: selection.shift.id, revision: selection.shift.version },
                    origin: "ROSTER" as const,
                    originVersion: 0,
                  }
                : {
                    workDate,
                    kind: "OFF" as const,
                    shift: null,
                    startsAt: null,
                    endsAt: null,
                    plannedMinutes: 0,
                    origin: "ROSTER" as const,
                    originVersion: 0,
                    holidayId: null,
                  },
        };
      }),
    })),
  };
}
export const bulkReviewFixture = (selection = bulkSelection()) =>
  toRosterBulkReview(bulkDtoFixture(selection), company, selection);
