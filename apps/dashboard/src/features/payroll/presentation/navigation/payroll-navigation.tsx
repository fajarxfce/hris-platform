import { MoneySettings20Regular, PersonMoney20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadCompensation } from "../../domain/policies/compensation-access-policy";
import { canReadPayrollPolicy } from "../../domain/policies/payroll-access-policy";
import { compensationMessages } from "../i18n/compensation-messages";
import { payrollPolicyMessages } from "../i18n/payroll-policy-messages";

export function payrollNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const policies: readonly AppNavigationItem[] = canReadPayrollPolicy(access?.permissions ?? [])
    ? [
        {
          to: "/payroll/policy",
          label: payrollPolicyMessages(locale).title,
          icon: <MoneySettings20Regular />,
        },
      ]
    : [];
  return canReadCompensation(access?.permissions ?? [])
    ? [
        ...policies,
        {
          to: "/payroll/compensation",
          label: compensationMessages(locale).title,
          icon: <PersonMoney20Regular />,
        },
      ]
    : policies;
}
