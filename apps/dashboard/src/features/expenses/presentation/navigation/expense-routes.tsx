import { Navigate, Route, Routes } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canBrowseCompanyExpenses } from "../../domain/policies/expense-claim-read-policy";
import { canReadExpensePayments } from "../../domain/policies/expense-payment-read-policy";
import { ExpenseClaimActionScreen } from "../bindings/expense-claim-action-screen";
import { ExpenseClaimScreen } from "../bindings/expense-claim-screen";
import { ExpenseClaimsScreen } from "../bindings/expense-claims-screen";
import { ExpenseDecisionScreen } from "../bindings/expense-decision-screen";
import { ExpenseDraftEditorScreen } from "../bindings/expense-draft-editor-screen";
import { ExpensePaymentScreen } from "../bindings/expense-payment-screen";
import { ExpensePaymentTransitionScreen } from "../bindings/expense-payment-transition-screen";
import { ExpensePaymentsScreen } from "../bindings/expense-payments-screen";
import { ExpensePoliciesScreen } from "../bindings/expense-policies-screen";
import { ExpensePolicyEditorScreen } from "../bindings/expense-policy-editor-screen";
import { ExpensePolicyScreen } from "../bindings/expense-policy-screen";
import { ExpensePreparationScreen } from "../bindings/expense-preparation-screen";
import { ExpenseReconciliationScreen } from "../bindings/expense-reconciliation-screen";
import { ExpenseSubmissionScreen } from "../bindings/expense-submission-screen";
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
      <Route path="payments" element={<ExpensePaymentsScreen {...props} />} />
      <Route path="payments/new" element={<ExpensePreparationScreen {...props} />} />
      <Route path="payments/:batchId" element={<ExpensePaymentScreen {...props} />} />
      <Route
        path="payments/:batchId/reconcile"
        element={<ExpenseReconciliationScreen {...props} />}
      />
      <Route
        path="payments/:batchId/:intent"
        element={<ExpensePaymentTransitionScreen {...props} />}
      />
      <Route path="claims" element={<ExpenseClaimsScreen {...props} />} />
      <Route path="claims/new" element={<ExpenseDraftEditorScreen {...props} creating={true} />} />
      <Route
        path="claims/:claimId/edit"
        element={<ExpenseDraftEditorScreen {...props} creating={false} />}
      />
      <Route path="claims/:claimId" element={<ExpenseClaimScreen {...props} />} />
      <Route path="claims/:claimId/:intent" element={<ExpenseClaimActionScreen {...props} />} />
      <Route path="submissions/:submissionId" element={<ExpenseSubmissionScreen {...props} />} />
      <Route
        path="submissions/:submissionId/:intent"
        element={<ExpenseDecisionScreen {...props} />}
      />
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
        element={
          <Navigate
            replace
            to={`/expenses/${canBrowseCompanyExpenses(props.access.permissions) ? "claims" : canReadExpensePayments(props.access.permissions) ? "payments" : "policies"}?company=${props.access.companyId}`}
          />
        }
      />
    </Routes>
  );
}
