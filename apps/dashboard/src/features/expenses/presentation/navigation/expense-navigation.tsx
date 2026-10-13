import { MoneySettings20Regular, Receipt20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canBrowseCompanyExpenses } from "../../domain/policies/expense-claim-read-policy";
import { canManageExpensePolicies } from "../../domain/policies/expense-read-policy";
import { expenseClaimMessages } from "../i18n/expense-claim-messages";
import { expensePolicyMessages } from "../i18n/expense-policy-messages";

export function expenseNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  return [
    ...(canBrowseCompanyExpenses(access?.permissions ?? [])
      ? [
          {
            to: "/expenses/claims",
            label: expenseClaimMessages(locale).title,
            icon: <Receipt20Regular />,
          },
        ]
      : []),
    ...(canManageExpensePolicies(access?.permissions ?? [])
      ? [
          {
            to: "/expenses/policies",
            label: expensePolicyMessages(locale).title,
            icon: <MoneySettings20Regular />,
          },
        ]
      : []),
  ];
}
