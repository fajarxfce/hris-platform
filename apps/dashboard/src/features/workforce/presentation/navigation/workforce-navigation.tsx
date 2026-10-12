import {
  CalendarClock20Regular,
  CalendarStar20Regular,
  CalendarWorkWeek20Regular,
  Clock20Regular,
  Warning20Regular,
} from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadAttendance } from "../../domain/policies/attendance-policy";
import { canReadCalendars } from "../../domain/policies/calendar-policy";
import { canReadHolidays } from "../../domain/policies/holiday-policy";
import { canReadShifts } from "../../domain/policies/shift-policy";
import { attendanceExceptionMessages } from "../i18n/attendance-exception-messages";
import { attendanceMessages } from "../i18n/attendance-messages";
import { calendarMessages } from "../i18n/calendar-messages";
import { holidayMessages } from "../i18n/holiday-messages";
import { shiftMessages } from "../i18n/shift-messages";
import { attendanceExceptionsPath } from "../models/attendance-exception-route";

export function workforceNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const permissions = access?.permissions ?? [];
  return [
    ...(canReadAttendance(permissions)
      ? [
          {
            to: "/workforce/attendance",
            label: attendanceMessages(locale).title,
            icon: <Clock20Regular />,
          },
          {
            to: attendanceExceptionsPath,
            label: attendanceExceptionMessages(locale).title,
            icon: <Warning20Regular />,
          },
        ]
      : []),
    ...(canReadCalendars(permissions)
      ? [
          {
            to: "/workforce/calendars",
            label: calendarMessages(locale).title,
            icon: <CalendarWorkWeek20Regular />,
          },
        ]
      : []),
    ...(canReadHolidays(permissions)
      ? [
          {
            to: "/workforce/holidays",
            label: holidayMessages(locale).title,
            icon: <CalendarStar20Regular />,
          },
        ]
      : []),
    ...(canReadShifts(permissions)
      ? [
          {
            to: "/workforce/shifts",
            label: shiftMessages(locale).title,
            icon: <CalendarClock20Regular />,
          },
        ]
      : []),
  ];
}
