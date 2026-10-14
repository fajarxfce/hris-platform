import {
  Calendar20Regular,
  ClipboardTaskListLtr20Regular,
  MoneySettings20Regular,
  PersonMoney20Regular,
} from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadCompensation } from "../../domain/policies/compensation-access-policy";
import { canReadPayrollPolicy } from "../../domain/policies/payroll-access-policy";
import { canReadPayrollInputs } from "../../domain/policies/payroll-input-access-policy";
import { canReadPayrollPeriods } from "../../domain/policies/payroll-period-policy";
import { compensationMessages } from "../i18n/compensation-messages";
import { payrollInputMessages } from "../i18n/payroll-input-messages";
import { payrollPeriodMessages } from "../i18n/payroll-period-messages";
import { payrollPolicyMessages } from "../i18n/payroll-policy-messages";

export function payrollNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const inputs: readonly AppNavigationItem[] = canReadPayrollInputs(access?.permissions ?? [])
    ? [
        {
          to: "/payroll/inputs",
          label: payrollInputMessages(locale).title,
          icon: <ClipboardTaskListLtr20Regular />,
        },
      ]
    : [];
  const periods: readonly AppNavigationItem[] = canReadPayrollPeriods(access?.permissions ?? [])
    ? [
        {
          to: "/payroll/periods",
          label: payrollPeriodMessages(locale).title,
          icon: <Calendar20Regular />,
        },
      ]
    : [];
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
        ...periods,
        ...inputs,
        ...policies,
        {
          to: "/payroll/compensation",
          label: compensationMessages(locale).title,
          icon: <PersonMoney20Regular />,
        },
      ]
    : [...periods, ...inputs, ...policies];
}
