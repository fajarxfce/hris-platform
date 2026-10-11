import type { AssignWeeklySchedule } from "../../domain/usecases/assign-weekly-schedule";
import type { LoadCalendarReview } from "../../domain/usecases/load-calendar-review";
import type { LoadHoliday } from "../../domain/usecases/load-holiday";
import type { LoadHolidays } from "../../domain/usecases/load-holidays";
import type { LoadShift } from "../../domain/usecases/load-shift";
import type { LoadShifts } from "../../domain/usecases/load-shifts";
import type { LoadWorkforceEmployees } from "../../domain/usecases/load-workforce-employees";
import type { SaveHoliday } from "../../domain/usecases/save-holiday";
import type { SaveShift } from "../../domain/usecases/save-shift";
import type { SetRosterDay } from "../../domain/usecases/set-roster-day";

export type WorkforceUseCases = Readonly<{
  loadWorkforceEmployees: Pick<LoadWorkforceEmployees, "execute">;
  loadCalendarReview: Pick<LoadCalendarReview, "execute">;
  assignWeeklySchedule: Pick<AssignWeeklySchedule, "execute">;
  setRosterDay: Pick<SetRosterDay, "execute">;
  loadHoliday: Pick<LoadHoliday, "execute">;
  loadHolidays: Pick<LoadHolidays, "execute">;
  saveHoliday: Pick<SaveHoliday, "execute">;
  loadShift: Pick<LoadShift, "execute">;
  loadShifts: Pick<LoadShifts, "execute">;
  saveShift: Pick<SaveShift, "execute">;
}>;
