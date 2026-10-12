import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpAttendanceDataSource } from "../data/datasources/http-attendance-data-source";
import { HttpCalendarDataSource } from "../data/datasources/http-calendar-data-source";
import { HttpHolidayDataSource } from "../data/datasources/http-holiday-data-source";
import { HttpOvertimeDataSource } from "../data/datasources/http-overtime-data-source";
import { HttpShiftDataSource } from "../data/datasources/http-shift-data-source";
import { RemoteAttendanceRepository } from "../data/repositories/remote-attendance-repository";
import { RemoteCalendarRepository } from "../data/repositories/remote-calendar-repository";
import { RemoteHolidayRepository } from "../data/repositories/remote-holiday-repository";
import { RemoteOvertimeRepository } from "../data/repositories/remote-overtime-repository";
import { RemoteShiftRepository } from "../data/repositories/remote-shift-repository";
import { AssignWeeklySchedule } from "../domain/usecases/assign-weekly-schedule";
import { CorrectAttendance } from "../domain/usecases/correct-attendance";
import { LoadAttendanceCorrectionHistory } from "../domain/usecases/load-attendance-correction-history";
import { LoadAttendanceExceptions } from "../domain/usecases/load-attendance-exceptions";
import { LoadCalendarReview } from "../domain/usecases/load-calendar-review";
import { LoadEmployeeAttendance } from "../domain/usecases/load-employee-attendance";
import { LoadHoliday } from "../domain/usecases/load-holiday";
import { LoadHolidays } from "../domain/usecases/load-holidays";
import { LoadOvertimeRequest } from "../domain/usecases/load-overtime-request";
import { LoadOvertimeRequests } from "../domain/usecases/load-overtime-requests";
import { LoadShift } from "../domain/usecases/load-shift";
import { LoadShifts } from "../domain/usecases/load-shifts";
import { LoadWorkforceEmployees } from "../domain/usecases/load-workforce-employees";
import { ReviewAttendance } from "../domain/usecases/review-attendance";
import { SaveHoliday } from "../domain/usecases/save-holiday";
import { SaveShift } from "../domain/usecases/save-shift";
import { SetRosterDay } from "../domain/usecases/set-roster-day";

export function createWorkforceFeature(http: HttpClient) {
  const attendance = new RemoteAttendanceRepository(new HttpAttendanceDataSource(http));
  const calendars = new RemoteCalendarRepository(new HttpCalendarDataSource(http));
  const shifts = new RemoteShiftRepository(new HttpShiftDataSource(http));
  const holidays = new RemoteHolidayRepository(new HttpHolidayDataSource(http));
  const overtime = new RemoteOvertimeRepository(new HttpOvertimeDataSource(http));
  return {
    loadOvertimeRequests: new LoadOvertimeRequests(overtime),
    loadOvertimeRequest: new LoadOvertimeRequest(overtime),
    loadAttendanceExceptions: new LoadAttendanceExceptions(attendance),
    loadAttendanceCorrectionHistory: new LoadAttendanceCorrectionHistory(attendance, calendars),
    loadEmployeeAttendance: new LoadEmployeeAttendance(attendance, calendars),
    reviewAttendance: new ReviewAttendance(attendance),
    correctAttendance: new CorrectAttendance(attendance),
    loadWorkforceEmployees: new LoadWorkforceEmployees(calendars),
    loadCalendarReview: new LoadCalendarReview(calendars),
    assignWeeklySchedule: new AssignWeeklySchedule(calendars),
    setRosterDay: new SetRosterDay(calendars),
    loadHolidays: new LoadHolidays(holidays),
    loadHoliday: new LoadHoliday(holidays),
    saveHoliday: new SaveHoliday(holidays),
    loadShifts: new LoadShifts(shifts),
    loadShift: new LoadShift(shifts),
    saveShift: new SaveShift(shifts),
  };
}
