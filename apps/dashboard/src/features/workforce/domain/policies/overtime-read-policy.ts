import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { overtimeStatuses } from "../entities/overtime-request";
import type { OvertimeSearch, OvertimeSearchInput } from "../entities/overtime-search";

export const canBrowseCompanyOvertime = (permissions: readonly string[]): boolean =>
  permissions.some((permission) =>
    ["overtime.read", "overtime.manage", "workforce.read"].includes(permission),
  );

export const canBrowseEmployeeOvertime = (permissions: readonly string[]): boolean =>
  canBrowseCompanyOvertime(permissions) ||
  permissions.some((permission) =>
    ["overtime.self.manage", "overtime.team.read", "workforce.team.read"].includes(permission),
  );

export function parseOvertimeSearch(input: OvertimeSearchInput): Result<OvertimeSearch> {
  const status =
    input.status === null ? null : overtimeStatuses.find((value) => value === input.status);
  if (
    !isCalendarDate(input.from) ||
    !isCalendarDate(input.until) ||
    input.from < "2000-01-01" ||
    input.until > "2100-12-31" ||
    input.from > input.until ||
    (Date.parse(`${input.until}T00:00:00Z`) - Date.parse(`${input.from}T00:00:00Z`)) / 86_400_000 >=
      62 ||
    status === undefined ||
    (input.employeeId !== null && !isUuid(input.employeeId)) ||
    (input.after !== null && !isUuid(input.after))
  )
    return failed("invalid_overtime_page");
  return success(
    Object.freeze({
      ...input,
      status,
      employeeId: input.employeeId?.toLowerCase() ?? null,
      after: input.after?.toLowerCase() ?? null,
    }),
  );
}

export const isOvertimeHistoryCursor = (value: string): boolean =>
  /^(0|[1-9][0-9]{0,15})$/u.test(value) && Number.isSafeInteger(Number(value));
