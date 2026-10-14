import { Navigate, Route, Routes } from "react-router-dom";
import { canReadPayrollPeriods } from "../../domain/policies/payroll-period-policy";
import { CompensationEditorScreen } from "../bindings/compensation-editor-screen";
import { CompensationEmployeesScreen } from "../bindings/compensation-employees-screen";
import { CompensationScreen } from "../bindings/compensation-screen";
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
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";

export function PayrollRoutes(props: PayrollScreenProps) {
  return (
    <Routes>
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
