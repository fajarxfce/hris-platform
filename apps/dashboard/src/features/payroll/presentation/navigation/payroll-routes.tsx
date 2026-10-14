import { Navigate, Route, Routes } from "react-router-dom";
import { PayrollPolicyEditorScreen } from "../bindings/payroll-policy-editor-screen";
import { PayrollPolicyScreen } from "../bindings/payroll-policy-screen";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";

export function PayrollRoutes(props: PayrollScreenProps) {
  return (
    <Routes>
      <Route path="policy" element={<PayrollPolicyScreen {...props} />} />
      <Route path="policy/edit" element={<PayrollPolicyEditorScreen {...props} />} />
      <Route
        path="*"
        element={<Navigate to={`/payroll/policy?company=${props.access.companyId}`} replace />}
      />
    </Routes>
  );
}
