import { DataBarVertical20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadAttendanceReport } from "../../domain/policies/attendance-report-policy";
import { canReadHeadcount } from "../../domain/policies/headcount-policy";
import { reportingMessages } from "../i18n/reporting-messages";

export function reportingNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const permissions = access?.permissions ?? [];
  const text = messages(locale);
  return [
    ...(canReadAttendanceReport(permissions)
      ? [
          {
            to: "/reports/attendance-exceptions",
            label: reportingMessages(locale).attendanceTitle,
            icon: <DataBarVertical20Regular />,
          },
        ]
      : []),
    ...(canReadHeadcount(permissions)
      ? [{ to: "/reports/headcount", label: text.reports, icon: <DataBarVertical20Regular /> }]
      : []),
  ];
}
