import { Navigate, Route, Routes } from "react-router-dom";
import { canReadPayrollAmendments } from "../../domain/policies/payroll-amendment-access-policy";
import { canReadPayrollFinalizations } from "../../domain/policies/payroll-finalization-policy";
import { canReadPayrollPayments } from "../../domain/policies/payroll-payment-read-policy";
import { canReadPayrollPayslips } from "../../domain/policies/payroll-payslip-policy";
import { canReadPayrollPeriods } from "../../domain/policies/payroll-period-policy";
import { CompensationEditorScreen } from "../bindings/compensation-editor-screen";
import { CompensationEmployeesScreen } from "../bindings/compensation-employees-screen";
import { CompensationScreen } from "../bindings/compensation-screen";
import { PayrollAmendmentActionScreen } from "../bindings/payroll-amendment-action-screen";
import { PayrollAmendmentEditorScreen } from "../bindings/payroll-amendment-editor-screen";
import { PayrollAmendmentPublicationRecoveryScreen } from "../bindings/payroll-amendment-publication-recovery-screen";
import { PayrollAmendmentPublicationScreen } from "../bindings/payroll-amendment-publication-screen";
import { PayrollAmendmentPublicationStartScreen } from "../bindings/payroll-amendment-publication-start-screen";
import { PayrollAmendmentPublicationsScreen } from "../bindings/payroll-amendment-publications-screen";
import { PayrollAmendmentReviewActionScreen } from "../bindings/payroll-amendment-review-action-screen";
import { PayrollAmendmentReviewScreen } from "../bindings/payroll-amendment-review-screen";
import { PayrollAmendmentReviewSubmissionScreen } from "../bindings/payroll-amendment-review-submission-screen";
import { PayrollAmendmentReviewsScreen } from "../bindings/payroll-amendment-reviews-screen";
import { PayrollAmendmentScreen } from "../bindings/payroll-amendment-screen";
import { PayrollAmendmentSourcesScreen } from "../bindings/payroll-amendment-sources-screen";
import { PayrollAmendmentsScreen } from "../bindings/payroll-amendments-screen";
import { PayrollCalculationStartScreen } from "../bindings/payroll-calculation-start-screen";
import { PayrollCalculationsScreen } from "../bindings/payroll-calculations-screen";
import { PayrollFinalizationScreen } from "../bindings/payroll-finalization-screen";
import { PayrollFinalizationStartScreen } from "../bindings/payroll-finalization-start-screen";
import { PayrollFinalizationsScreen } from "../bindings/payroll-finalizations-screen";
import { PayrollInputEditorScreen } from "../bindings/payroll-input-editor-screen";
import { PayrollInputEmployeesScreen } from "../bindings/payroll-input-employees-screen";
import { PayrollInputScreen } from "../bindings/payroll-input-screen";
import { PayrollInputVerificationScreen } from "../bindings/payroll-input-verification-screen";
import { PayrollPaymentPreparationScreen } from "../bindings/payroll-payment-preparation-screen";
import { PayrollPaymentReconciliationScreen } from "../bindings/payroll-payment-reconciliation-screen";
import { PayrollPaymentRecoveryScreen } from "../bindings/payroll-payment-recovery-screen";
import { PayrollPaymentScreen } from "../bindings/payroll-payment-screen";
import { PayrollPaymentTransitionScreen } from "../bindings/payroll-payment-transition-screen";
import { PayrollPaymentsScreen } from "../bindings/payroll-payments-screen";
import { PayrollPayslipScreen } from "../bindings/payroll-payslip-screen";
import { PayrollPayslipsScreen } from "../bindings/payroll-payslips-screen";
import { PayrollPeriodCancellationScreen } from "../bindings/payroll-period-cancellation-screen";
import { PayrollPeriodCreationScreen } from "../bindings/payroll-period-creation-screen";
import { PayrollPeriodScreen } from "../bindings/payroll-period-screen";
import { PayrollPeriodsScreen } from "../bindings/payroll-periods-screen";
import { PayrollPolicyEditorScreen } from "../bindings/payroll-policy-editor-screen";
import { PayrollPolicyScreen } from "../bindings/payroll-policy-screen";
import { PayrollPublicationsScreen } from "../bindings/payroll-publications-screen";
import { PayrollReviewActionScreen } from "../bindings/payroll-review-action-screen";
import { PayrollReviewScreen } from "../bindings/payroll-review-screen";
import { PayrollReviewSubmissionScreen } from "../bindings/payroll-review-submission-screen";
import { PayrollReviewsScreen } from "../bindings/payroll-reviews-screen";
import { PayrollRunEmployeeScreen } from "../bindings/payroll-run-employee-screen";
import { PayrollRunRecoveryScreen } from "../bindings/payroll-run-recovery-screen";
import { PayrollRunScreen } from "../bindings/payroll-run-screen";
import { TaxOpeningEditorScreen } from "../bindings/tax-opening-editor-screen";
import { TaxOpeningEmployeesScreen } from "../bindings/tax-opening-employees-screen";
import { TaxOpeningScreen } from "../bindings/tax-opening-screen";
import { TaxOpeningVerificationScreen } from "../bindings/tax-opening-verification-screen";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";

export function PayrollRoutes(props: PayrollScreenProps) {
  return (
    <Routes>
      <Route path="amendments" element={<PayrollAmendmentsScreen {...props} />} />
      <Route path="amendments/new" element={<PayrollAmendmentSourcesScreen {...props} />} />
      <Route
        path="amendments/new/:assessmentId"
        element={<PayrollAmendmentEditorScreen {...props} mode="create" />}
      />
      <Route
        path="amendments/:amendmentId/edit"
        element={<PayrollAmendmentEditorScreen {...props} mode="edit" />}
      />
      <Route path="amendments/:amendmentId" element={<PayrollAmendmentScreen {...props} />} />
      <Route
        path="amendments/:amendmentId/publications"
        element={<PayrollAmendmentPublicationsScreen {...props} />}
      />
      <Route
        path="amendments/:amendmentId/publish"
        element={<PayrollAmendmentPublicationStartScreen {...props} />}
      />
      <Route
        path="amendments/:amendmentId/publications/:publicationId"
        element={<PayrollAmendmentPublicationScreen {...props} />}
      />
      <Route
        path="amendments/:amendmentId/publications/:publicationId/recover"
        element={<PayrollAmendmentPublicationRecoveryScreen {...props} />}
      />
      <Route
        path="amendments/:amendmentId/calculate"
        element={<PayrollAmendmentActionScreen {...props} action="CALCULATE" />}
      />
      <Route
        path="amendments/:amendmentId/cancel"
        element={<PayrollAmendmentActionScreen {...props} action="CANCEL" />}
      />
      <Route
        path="amendments/:amendmentId/reviews"
        element={<PayrollAmendmentReviewsScreen {...props} />}
      />
      <Route
        path="amendments/:amendmentId/reviews/new"
        element={<PayrollAmendmentReviewSubmissionScreen {...props} />}
      />
      <Route
        path="amendment-reviews/:reviewId"
        element={<PayrollAmendmentReviewScreen {...props} />}
      />
      <Route
        path="amendment-reviews/:reviewId/approve"
        element={<PayrollAmendmentReviewActionScreen {...props} action="APPROVE" />}
      />
      <Route
        path="amendment-reviews/:reviewId/reject"
        element={<PayrollAmendmentReviewActionScreen {...props} action="REJECT" />}
      />
      <Route
        path="amendment-reviews/:reviewId/withdraw"
        element={<PayrollAmendmentReviewActionScreen {...props} action="WITHDRAW" />}
      />
      <Route path="payments" element={<PayrollPaymentsScreen {...props} />} />
      <Route path="payments/new" element={<PayrollPaymentPreparationScreen {...props} />} />
      <Route path="payments/:batchId" element={<PayrollPaymentScreen {...props} />} />
      <Route
        path="payments/:batchId/reconcile"
        element={<PayrollPaymentReconciliationScreen {...props} />}
      />
      <Route
        path="payments/:batchId/:intent"
        element={<PayrollPaymentTransitionScreen {...props} />}
      />

      <Route path="payslips" element={<PayrollPayslipsScreen {...props} />} />
      <Route path="payslips/:payslipId" element={<PayrollPayslipScreen {...props} />} />
      <Route
        path="payslips/:payslipId/returns"
        element={<PayrollPaymentRecoveryScreen {...props} />}
      />
      <Route
        path="payslips/:payslipId/returns/new"
        element={<PayrollPaymentRecoveryScreen {...props} record />}
      />
      <Route path="publications" element={<PayrollPublicationsScreen {...props} />} />
      <Route path="runs/:runId/finalizations" element={<PayrollFinalizationsScreen {...props} />} />
      <Route path="runs/:runId/finalize" element={<PayrollFinalizationStartScreen {...props} />} />
      <Route
        path="finalizations/:finalizationId"
        element={<PayrollFinalizationScreen {...props} />}
      />
      <Route path="runs/:runId/reviews" element={<PayrollReviewsScreen {...props} />} />
      <Route
        path="runs/:runId/reviews/new"
        element={<PayrollReviewSubmissionScreen {...props} />}
      />
      <Route path="reviews/:reviewId" element={<PayrollReviewScreen {...props} />} />
      <Route
        path="reviews/:reviewId/approve"
        element={<PayrollReviewActionScreen {...props} action="APPROVE" />}
      />
      <Route
        path="reviews/:reviewId/reject"
        element={<PayrollReviewActionScreen {...props} action="REJECT" />}
      />
      <Route
        path="reviews/:reviewId/withdraw"
        element={<PayrollReviewActionScreen {...props} action="WITHDRAW" />}
      />
      <Route
        path="periods/:periodId/calculations"
        element={<PayrollCalculationsScreen {...props} />}
      />
      <Route
        path="periods/:periodId/calculate"
        element={<PayrollCalculationStartScreen {...props} />}
      />
      <Route path="runs/:runId" element={<PayrollRunScreen {...props} />} />
      <Route
        path="runs/:runId/employees/:employeeId"
        element={<PayrollRunEmployeeScreen {...props} />}
      />
      <Route
        path="runs/:runId/resume"
        element={<PayrollRunRecoveryScreen {...props} action="resume" />}
      />
      <Route
        path="runs/:runId/abandon"
        element={<PayrollRunRecoveryScreen {...props} action="abandon" />}
      />
      <Route path="tax-openings" element={<TaxOpeningEmployeesScreen {...props} />} />
      <Route path="tax-openings/:employeeId" element={<TaxOpeningScreen {...props} />} />
      <Route path="tax-openings/:employeeId/edit" element={<TaxOpeningEditorScreen {...props} />} />
      <Route
        path="tax-openings/:employeeId/verify"
        element={<TaxOpeningVerificationScreen {...props} />}
      />
      <Route path="inputs" element={<PayrollInputEmployeesScreen {...props} />} />
      <Route path="inputs/:employeeId" element={<PayrollInputScreen {...props} />} />
      <Route path="inputs/:employeeId/edit" element={<PayrollInputEditorScreen {...props} />} />
      <Route
        path="inputs/:employeeId/verify"
        element={<PayrollInputVerificationScreen {...props} />}
      />
      <Route path="periods" element={<PayrollPeriodsScreen {...props} />} />
      <Route path="periods/new" element={<PayrollPeriodCreationScreen {...props} />} />
      <Route path="periods/:periodId" element={<PayrollPeriodScreen {...props} />} />
      <Route
        path="periods/:periodId/cancel"
        element={<PayrollPeriodCancellationScreen {...props} />}
      />
      <Route path="policy" element={<PayrollPolicyScreen {...props} />} />
      <Route path="policy/edit" element={<PayrollPolicyEditorScreen {...props} />} />
      <Route path="compensation" element={<CompensationEmployeesScreen {...props} />} />
      <Route path="compensation/:employeeId" element={<CompensationScreen {...props} />} />
      <Route
        path="compensation/:employeeId/edit"
        element={<CompensationEditorScreen {...props} />}
      />
      <Route
        path="*"
        element={
          <Navigate
            to={`/payroll/${canReadPayrollPeriods(props.access.permissions) ? "periods" : canReadPayrollFinalizations(props.access.permissions) ? "publications" : canReadPayrollPayslips(props.access.permissions) ? "payslips" : canReadPayrollPayments(props.access.permissions) ? "payments" : canReadPayrollAmendments(props.access.permissions) ? "amendments" : "policy"}?company=${props.access.companyId}`}
            replace
          />
        }
      />
    </Routes>
  );
}
