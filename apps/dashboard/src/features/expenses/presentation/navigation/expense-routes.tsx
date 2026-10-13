import { Navigate, Route, Routes } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { ExpensePoliciesScreen } from "../bindings/expense-policies-screen";
import { ExpensePolicyEditorScreen } from "../bindings/expense-policy-editor-screen";
import { ExpensePolicyScreen } from "../bindings/expense-policy-screen";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";

export function ExpenseRoutes(props: {
  accountId: AccountId;
  access: CompanyAccess;
  expense: ExpenseUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
  nextIdentifier: () => string;
}) {
  return (
    <Routes>
      <Route path="policies" element={<ExpensePoliciesScreen {...props} />} />
      <Route
        path="policies/new"
        element={<ExpensePolicyEditorScreen {...props} creating={true} />}
      />
      <Route
        path="policies/:policyId/edit"
        element={<ExpensePolicyEditorScreen {...props} creating={false} />}
      />
      <Route path="policies/:policyId" element={<ExpensePolicyScreen {...props} />} />
      <Route
        path="*"
        element={<Navigate replace to={`/expenses/policies?company=${props.access.companyId}`} />}
      />
    </Routes>
  );
}
