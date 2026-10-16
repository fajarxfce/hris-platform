import { Navigate, Route, Routes } from "react-router-dom";
import { CompanyAccountPickerScreen } from "../bindings/company-account-picker-screen";
import { CompanyMemberScreen } from "../bindings/company-member-screen";
import { CompanyMembersScreen } from "../bindings/company-members-screen";
import { CompanyMembershipCreationScreen } from "../bindings/company-membership-creation-screen";
import { CompanyMembershipEditorScreen } from "../bindings/company-membership-editor-screen";
import type { CompanyMembershipScreenProps } from "../contracts/company-membership-screen-props";
export function CompanyMemberRoutes(props: CompanyMembershipScreenProps) {
  return (
    <Routes>
      <Route index element={<CompanyMembersScreen {...props} />} />
      <Route path="new" element={<CompanyAccountPickerScreen {...props} />} />
      <Route path="new/:memberId" element={<CompanyMembershipCreationScreen {...props} />} />
      <Route path=":memberId/edit" element={<CompanyMembershipEditorScreen {...props} />} />
      <Route path=":memberId" element={<CompanyMemberScreen {...props} />} />
      <Route
        path="*"
        element={
          <Navigate to={`/administration/members?company=${props.access.companyId}`} replace />
        }
      />
    </Routes>
  );
}
