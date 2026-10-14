import { MoneySettings20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadPayrollPolicy } from "../../domain/policies/payroll-access-policy";
import { payrollPolicyMessages } from "../i18n/payroll-policy-messages";

export function payrollNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  return canReadPayrollPolicy(access?.permissions ?? [])
    ? [
        {
          to: "/payroll/policy",
          label: payrollPolicyMessages(locale).title,
          icon: <MoneySettings20Regular />,
        },
      ]
    : [];
}
