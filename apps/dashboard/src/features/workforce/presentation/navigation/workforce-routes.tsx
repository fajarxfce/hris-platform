import { Navigate, Route, Routes } from "react-router-dom";
import { EmployeeCalendarScreen } from "../bindings/employee-calendar-screen";
import { HolidayEditorScreen } from "../bindings/holiday-editor-screen";
import { HolidayScreen } from "../bindings/holiday-screen";
import { HolidaysScreen } from "../bindings/holidays-screen";
import { RosterDayScreen } from "../bindings/roster-day-screen";
import { ShiftEditorScreen } from "../bindings/shift-editor-screen";
import { ShiftScreen } from "../bindings/shift-screen";
import { ShiftsScreen } from "../bindings/shifts-screen";
import { WeeklyScheduleScreen } from "../bindings/weekly-schedule-screen";
import { WorkforceCalendarsScreen } from "../bindings/workforce-calendars-screen";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { shiftParameters, shiftsPath } from "../models/shift-route";

export function WorkforceRoutes(props: WorkforceScreenProps) {
  return (
    <Routes>
      <Route path="calendars" element={<WorkforceCalendarsScreen {...props} />} />
      <Route path="calendars/:employeeId" element={<EmployeeCalendarScreen {...props} />} />
      <Route path="calendars/:employeeId/schedule" element={<WeeklyScheduleScreen {...props} />} />
      <Route
        path="calendars/:employeeId/roster/:workDate"
        element={<RosterDayScreen {...props} />}
      />
      <Route path="holidays" element={<HolidaysScreen {...props} />} />
      <Route path="holidays/new" element={<HolidayEditorScreen {...props} creating />} />
      <Route
        path="holidays/:holidayId/edit"
        element={<HolidayEditorScreen {...props} creating={false} />}
      />
      <Route path="holidays/:holidayId" element={<HolidayScreen {...props} />} />
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
