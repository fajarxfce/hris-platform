import { Navigate, Route, Routes } from "react-router-dom";
import { canReadPayrollPeriods } from "../../domain/policies/payroll-period-policy";
import { CompensationEditorScreen } from "../bindings/compensation-editor-screen";
import { CompensationEmployeesScreen } from "../bindings/compensation-employees-screen";
import { CompensationScreen } from "../bindings/compensation-screen";
import { PayrollCalculationStartScreen } from "../bindings/payroll-calculation-start-screen";
import { PayrollCalculationsScreen } from "../bindings/payroll-calculations-screen";
import { PayrollInputEditorScreen } from "../bindings/payroll-input-editor-screen";
import { PayrollInputEmployeesScreen } from "../bindings/payroll-input-employees-screen";
import { PayrollInputScreen } from "../bindings/payroll-input-screen";
import { PayrollInputVerificationScreen } from "../bindings/payroll-input-verification-screen";
import { PayrollPeriodCancellationScreen } from "../bindings/payroll-period-cancellation-screen";
import { PayrollPeriodCreationScreen } from "../bindings/payroll-period-creation-screen";
import { PayrollPeriodScreen } from "../bindings/payroll-period-screen";
import { PayrollPeriodsScreen } from "../bindings/payroll-periods-screen";
import { PayrollPolicyEditorScreen } from "../bindings/payroll-policy-editor-screen";
import { PayrollPolicyScreen } from "../bindings/payroll-policy-screen";
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
            to={`/payroll/${canReadPayrollPeriods(props.access.permissions) ? "periods" : "policy"}?company=${props.access.companyId}`}
            replace
          />
        }
      />
    </Routes>
  );
}
