import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpHolidayDataSource } from "../data/datasources/http-holiday-data-source";
import { HttpShiftDataSource } from "../data/datasources/http-shift-data-source";
import { RemoteHolidayRepository } from "../data/repositories/remote-holiday-repository";
import { RemoteShiftRepository } from "../data/repositories/remote-shift-repository";
import { LoadHoliday } from "../domain/usecases/load-holiday";
import { LoadHolidays } from "../domain/usecases/load-holidays";
import { LoadShift } from "../domain/usecases/load-shift";
import { LoadShifts } from "../domain/usecases/load-shifts";
import { SaveHoliday } from "../domain/usecases/save-holiday";
import { SaveShift } from "../domain/usecases/save-shift";

export function createWorkforceFeature(http: HttpClient) {
  const shifts = new RemoteShiftRepository(new HttpShiftDataSource(http));
  const holidays = new RemoteHolidayRepository(new HttpHolidayDataSource(http));
  return {
    loadHolidays: new LoadHolidays(holidays),
    loadHoliday: new LoadHoliday(holidays),
    saveHoliday: new SaveHoliday(holidays),
    loadShifts: new LoadShifts(shifts),
    loadShift: new LoadShift(shifts),
    saveShift: new SaveShift(shifts),
  };
}
