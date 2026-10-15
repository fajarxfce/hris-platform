import {
  CalendarCheckmark20Regular,
  CalendarLtr20Regular,
  CalendarSync20Regular,
  ClipboardTaskListLtr20Regular,
} from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadLeaveBatches } from "../../domain/policies/leave-batch-policy";
import { canReadLeaveCalendar } from "../../domain/policies/leave-calendar-policy";
import {
  canBrowseCompanyLeave,
  canManageLeavePolicies,
} from "../../domain/policies/leave-read-policy";
import { leaveBatchMessages } from "../i18n/leave-batch-messages";
import { leaveCalendarMessages } from "../i18n/leave-calendar-messages";
import { leaveMessages } from "../i18n/leave-messages";
import { leavePolicyMessages } from "../i18n/leave-policy-messages";

export function leaveNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const permissions = access?.permissions ?? [];
  return [
    ...(canReadLeaveCalendar(permissions, "TEAM")
      ? [
          {
            to: "/leave/calendar",
            label: leaveCalendarMessages(locale).title,
            icon: <CalendarLtr20Regular />,
          },
        ]
      : []),
    ...(canReadLeaveBatches(permissions)
      ? [
          {
            to: "/leave/batches",
            label: leaveBatchMessages(locale).title,
            icon: <CalendarSync20Regular />,
          },
        ]
      : []),
    ...(canManageLeavePolicies(permissions)
      ? [
          {
            to: "/leave/policies",
            label: leavePolicyMessages(locale).title,
            icon: <ClipboardTaskListLtr20Regular />,
          },
        ]
      : []),
    ...(canBrowseCompanyLeave(permissions)
      ? [
          {
            to: "/leave/requests",
            label: leaveMessages(locale).requests,
            icon: <CalendarCheckmark20Regular />,
          },
        ]
      : []),
  ];
}
