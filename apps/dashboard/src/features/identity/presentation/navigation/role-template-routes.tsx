import { Navigate, Route, Routes } from "react-router-dom";
import { RoleTemplateEditorScreen } from "../bindings/role-template-editor-screen";
import { RoleTemplateScreen } from "../bindings/role-template-screen";
import { RoleTemplatesScreen } from "../bindings/role-templates-screen";
import type { RoleTemplateScreenProps } from "../contracts/role-template-screen-props";
export function RoleTemplateRoutes(props: RoleTemplateScreenProps) {
  return (
    <Routes>
      <Route index element={<RoleTemplatesScreen {...props} />} />
      <Route path="new" element={<RoleTemplateEditorScreen {...props} creating />} />
      <Route
        path=":roleId/edit"
        element={<RoleTemplateEditorScreen {...props} creating={false} />}
      />
      <Route path=":roleId" element={<RoleTemplateScreen {...props} />} />
      <Route
        path="*"
        element={
          <Navigate to={`/administration/roles?company=${props.access.companyId}`} replace />
        }
      />
    </Routes>
  );
}
