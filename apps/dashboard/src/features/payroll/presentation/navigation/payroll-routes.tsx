import { Navigate, Route, Routes } from "react-router-dom";
import { CompensationEditorScreen } from "../bindings/compensation-editor-screen";
import { CompensationEmployeesScreen } from "../bindings/compensation-employees-screen";
import { CompensationScreen } from "../bindings/compensation-screen";
import { PayrollPolicyEditorScreen } from "../bindings/payroll-policy-editor-screen";
import { PayrollPolicyScreen } from "../bindings/payroll-policy-screen";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";

export function PayrollRoutes(props: PayrollScreenProps) {
  return (
    <Routes>
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
        element={<Navigate to={`/payroll/policy?company=${props.access.companyId}`} replace />}
      />
    </Routes>
  );
}
