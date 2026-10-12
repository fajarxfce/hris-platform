import { Navigate, Route, Routes } from "react-router-dom";
import { AttendanceCorrectionHistoryScreen } from "../bindings/attendance-correction-history-screen";
import { AttendanceDayScreen } from "../bindings/attendance-day-screen";
import { AttendanceDirectoryScreen } from "../bindings/attendance-directory-screen";
import { AttendanceEditorScreen } from "../bindings/attendance-editor-screen";
import { AttendanceExceptionsScreen } from "../bindings/attendance-exceptions-screen";
import { EmployeeAttendanceScreen } from "../bindings/employee-attendance-screen";
import { EmployeeCalendarScreen } from "../bindings/employee-calendar-screen";
import { HolidayEditorScreen } from "../bindings/holiday-editor-screen";
import { HolidayScreen } from "../bindings/holiday-screen";
import { HolidaysScreen } from "../bindings/holidays-screen";
import { OvertimeActionScreen } from "../bindings/overtime-action-screen";
import { OvertimeRequestScreen } from "../bindings/overtime-request-screen";
import { OvertimeRequestsScreen } from "../bindings/overtime-requests-screen";
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
      <Route path="overtime" element={<OvertimeRequestsScreen {...props} />} />
      <Route path="overtime/:overtimeId" element={<OvertimeRequestScreen {...props} />} />
      <Route path="overtime/:overtimeId/:intent" element={<OvertimeActionScreen {...props} />} />
      <Route path="attendance" element={<AttendanceDirectoryScreen {...props} />} />
      <Route path="attendance/exceptions" element={<AttendanceExceptionsScreen {...props} />} />
      <Route path="attendance/:employeeId" element={<EmployeeAttendanceScreen {...props} />} />
      <Route
        path="attendance/:employeeId/days/:workDate"
        element={<AttendanceDayScreen {...props} />}
      />
      <Route
        path="attendance/:employeeId/days/:workDate/history"
        element={<AttendanceCorrectionHistoryScreen {...props} />}
      />
      <Route
        path="attendance/:employeeId/days/:workDate/review/:entryId"
        element={<AttendanceEditorScreen {...props} kind="review" />}
      />
      <Route
        path="attendance/:employeeId/days/:workDate/correction"
        element={<AttendanceEditorScreen {...props} kind="correction" />}
      />
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
