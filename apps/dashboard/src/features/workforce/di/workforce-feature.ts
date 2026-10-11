import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpCalendarDataSource } from "../data/datasources/http-calendar-data-source";
import { HttpHolidayDataSource } from "../data/datasources/http-holiday-data-source";
import { HttpShiftDataSource } from "../data/datasources/http-shift-data-source";
import { RemoteCalendarRepository } from "../data/repositories/remote-calendar-repository";
import { RemoteHolidayRepository } from "../data/repositories/remote-holiday-repository";
import { RemoteShiftRepository } from "../data/repositories/remote-shift-repository";
import { AssignWeeklySchedule } from "../domain/usecases/assign-weekly-schedule";
import { LoadCalendarReview } from "../domain/usecases/load-calendar-review";
import { LoadHoliday } from "../domain/usecases/load-holiday";
import { LoadHolidays } from "../domain/usecases/load-holidays";
import { LoadShift } from "../domain/usecases/load-shift";
import { LoadShifts } from "../domain/usecases/load-shifts";
import { LoadWorkforceEmployees } from "../domain/usecases/load-workforce-employees";
import { SaveHoliday } from "../domain/usecases/save-holiday";
import { SaveShift } from "../domain/usecases/save-shift";
import { SetRosterDay } from "../domain/usecases/set-roster-day";

export function createWorkforceFeature(http: HttpClient) {
  const calendars = new RemoteCalendarRepository(new HttpCalendarDataSource(http));
  const shifts = new RemoteShiftRepository(new HttpShiftDataSource(http));
  const holidays = new RemoteHolidayRepository(new HttpHolidayDataSource(http));
  return {
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
