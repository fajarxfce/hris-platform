import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { type LeaveCalendarScope, leaveCalendarScopes } from "../entities/leave-calendar";

export const canReadLeaveCalendar = (
  permissions: readonly string[],
  scope: LeaveCalendarScope,
): boolean =>
  leaveCalendarScopes.includes(scope) &&
  (permissions.includes("leave.read") ||
    (scope === "TEAM" && permissions.includes("leave.team.read")));
export const isLeaveCalendarDate = (value: string): boolean =>
  isCalendarDate(value) && value >= "1900-01-01" && value <= "2200-12-31";
export const isLeaveCalendarMonth = (value: string): boolean => isLeaveCalendarDate(`${value}-01`);
