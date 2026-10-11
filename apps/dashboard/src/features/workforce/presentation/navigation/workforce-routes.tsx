import { Navigate, Route, Routes } from "react-router-dom";
import { ShiftEditorScreen } from "../bindings/shift-editor-screen";
import { ShiftScreen } from "../bindings/shift-screen";
import { ShiftsScreen } from "../bindings/shifts-screen";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { shiftParameters, shiftsPath } from "../models/shift-route";

export function WorkforceRoutes(props: WorkforceScreenProps) {
  return (
    <Routes>
      <Route path="shifts" element={<ShiftsScreen {...props} />} />
      <Route path="shifts/new" element={<ShiftEditorScreen {...props} creating />} />
      <Route
        path="shifts/:shiftId/edit"
        element={<ShiftEditorScreen {...props} creating={false} />}
      />
      <Route path="shifts/:shiftId" element={<ShiftScreen {...props} />} />
      <Route
        path="*"
        element={
          <Navigate to={`${shiftsPath}?${shiftParameters(props.access.companyId)}`} replace />
        }
      />
    </Routes>
  );
}
