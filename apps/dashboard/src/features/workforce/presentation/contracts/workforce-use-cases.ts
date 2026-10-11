import type { LoadHoliday } from "../../domain/usecases/load-holiday";
import type { LoadHolidays } from "../../domain/usecases/load-holidays";
import type { LoadShift } from "../../domain/usecases/load-shift";
import type { LoadShifts } from "../../domain/usecases/load-shifts";
import type { SaveHoliday } from "../../domain/usecases/save-holiday";
import type { SaveShift } from "../../domain/usecases/save-shift";

export type WorkforceUseCases = Readonly<{
  loadHoliday: Pick<LoadHoliday, "execute">;
  loadHolidays: Pick<LoadHolidays, "execute">;
  saveHoliday: Pick<SaveHoliday, "execute">;
  loadShift: Pick<LoadShift, "execute">;
  loadShifts: Pick<LoadShifts, "execute">;
  saveShift: Pick<SaveShift, "execute">;
}>;
