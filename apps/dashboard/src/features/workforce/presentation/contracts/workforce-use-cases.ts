import type { LoadShift } from "../../domain/usecases/load-shift";
import type { LoadShifts } from "../../domain/usecases/load-shifts";
import type { SaveShift } from "../../domain/usecases/save-shift";

export type WorkforceUseCases = Readonly<{
  loadShift: Pick<LoadShift, "execute">;
  loadShifts: Pick<LoadShifts, "execute">;
  saveShift: Pick<SaveShift, "execute">;
}>;
