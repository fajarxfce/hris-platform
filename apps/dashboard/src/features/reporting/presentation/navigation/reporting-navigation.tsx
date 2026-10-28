import { DataBarVertical20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadAttendanceReport } from "../../domain/policies/attendance-report-policy";
import { canReadExpenseReport } from "../../domain/policies/expense-report-policy";
import { canReadHeadcount } from "../../domain/policies/headcount-policy";
import { canReadLeaveReport } from "../../domain/policies/leave-report-policy";
import { canReadOvertimeReport } from "../../domain/policies/overtime-report-policy";
import { canReadPayrollCostReport } from "../../domain/policies/payroll-cost-report-policy";
import { reportingMessages } from "../i18n/reporting-messages";

export function reportingNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const permissions = access?.permissions ?? [];
  const text = messages(locale);
  return [
    ...(canReadPayrollCostReport(permissions)
      ? [
          {
            to: "/reports/payroll-costs",
            label: reportingMessages(locale).payrollCostTitle,
            icon: <DataBarVertical20Regular />,
          },
        ]
      : []),
    ...(canReadExpenseReport(permissions)
      ? [
          {
            to: "/reports/expenses",
            label: reportingMessages(locale).expenseTitle,
            icon: <DataBarVertical20Regular />,
          },
        ]
      : []),
    ...(canReadOvertimeReport(permissions)
      ? [
          {
            to: "/reports/overtime",
            label: reportingMessages(locale).overtimeTitle,
            icon: <DataBarVertical20Regular />,
          },
        ]
      : []),
    ...(canReadLeaveReport(permissions)
      ? [
          {
            to: "/reports/leave",
            label: reportingMessages(locale).leaveTitle,
            icon: <DataBarVertical20Regular />,
          },
        ]
      : []),
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
