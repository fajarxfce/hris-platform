import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { RosterBulkChange } from "../../domain/entities/roster-bulk-change";
import type { RosterBulkDay } from "../../domain/entities/roster-bulk-day";
import type { RosterBulkReview } from "../../domain/entities/roster-bulk-review";
import type { RosterBulkSelection } from "../../domain/entities/roster-bulk-selection";
import type { RosterBulkApplyDto, RosterBulkReviewDto } from "../models/roster-bulk-dto";
import { toScheduledDay } from "./scheduled-day-mapper";
import { toWorkforceEmployee } from "./workforce-employee-mapper";

export function toRosterBulkReview(
  dto: RosterBulkReviewDto,
  company: CompanyId,
  selection: RosterBulkSelection,
): RosterBulkReview {
  if (
    dto.mode !== selection.mode ||
    dto.shiftId?.toLowerCase() !== selection.shift?.id ||
    dto.shiftVersion !== (selection.shift?.version ?? null) ||
    JSON.stringify(dto.employeeIds.map((id) => id.toLowerCase())) !==
      JSON.stringify(selection.employeeIds) ||
    JSON.stringify(dto.dates) !== JSON.stringify(selection.dates) ||
    dto.employees.length !== selection.employeeIds.length ||
    dto.employeeIds.length * dto.dates.length > 200
  )
    throw new InvalidHttpResponseError();
  const employees = dto.employees.map((entry, index) => {
    const employee = toWorkforceEmployee(entry.employee, company);
    if (
      employee.id !== selection.employeeIds[index] ||
      entry.days.length !== selection.dates.length
    )
      throw new InvalidHttpResponseError();
    const days = entry.days.map((raw, dateIndex): RosterBulkDay => {
      const workDate = selection.dates[dateIndex];
      if (
        !workDate ||
        raw.workDate !== workDate ||
        (raw.overrideActive && raw.expectedVersion === null)
      )
        throw new InvalidHttpResponseError();
      const before = toScheduledDay(raw.before, workDate);
      const after = toScheduledDay(raw.after, workDate);
      if (
        raw.overrideActive !== (before.kind !== "UNASSIGNED" && before.origin === "ROSTER") ||
        (raw.overrideActive &&
          before.kind !== "UNASSIGNED" &&
          before.originVersion !== raw.expectedVersion) ||
        (!raw.changed && JSON.stringify(before) !== JSON.stringify(after))
      )
        throw new InvalidHttpResponseError();
      if (selection.mode === "CALENDAR") {
        if (
          raw.changed !== raw.overrideActive ||
          (after.kind !== "UNASSIGNED" && after.origin === "ROSTER")
        )
          throw new InvalidHttpResponseError();
      } else {
        const proposedVersion = raw.changed
          ? raw.expectedVersion === null
            ? 0
            : raw.expectedVersion + 1
          : raw.expectedVersion;
        if (
          !Number.isSafeInteger(proposedVersion) ||
          after.kind === "UNASSIGNED" ||
          after.origin !== "ROSTER" ||
          after.originVersion !== proposedVersion ||
          (selection.mode === "DAY_OFF" && after.kind !== "OFF") ||
          (selection.mode === "SHIFT" &&
            (after.kind !== "WORK" ||
              after.shift.id !== selection.shift?.id ||
              after.shift.revision !== selection.shift?.version))
        )
          throw new InvalidHttpResponseError();
        const sameOverride =
          raw.overrideActive &&
          (selection.mode === "DAY_OFF"
            ? before.kind === "OFF"
            : before.kind === "WORK" &&
              after.kind === "WORK" &&
              JSON.stringify(before.shift) === JSON.stringify(after.shift));
        if (raw.changed === sameOverride) throw new InvalidHttpResponseError();
      }
      return Object.freeze({
        workDate,
        expectedVersion: raw.expectedVersion,
        overrideActive: raw.overrideActive,
        before,
        after,
        changed: raw.changed,
      });
    });
    return Object.freeze({ employee, days: Object.freeze(days) });
  });
  if (
    employees.reduce(
      (count, entry) => count + entry.days.filter((day) => day.changed).length,
      0,
    ) !== dto.changeCount
  )
    throw new InvalidHttpResponseError();
  const ownedSelection = Object.freeze({
    ...selection,
    employeeIds: Object.freeze([...selection.employeeIds]),
    dates: Object.freeze([...selection.dates]),
    shift: selection.shift === null ? null : Object.freeze({ ...selection.shift }),
  });
  return Object.freeze({
    companyId: company,
    selection: ownedSelection,
    employees: Object.freeze(employees),
    revision: dto.revision,
    changeCount: dto.changeCount,
  });
}

export function toRosterBulkApplyDto(change: RosterBulkChange): RosterBulkApplyDto {
  return {
    selection: change.selection,
    reviewRevision: change.reviewRevision,
    reason: change.reason,
  };
}
