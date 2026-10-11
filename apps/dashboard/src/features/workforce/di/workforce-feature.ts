import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpShiftDataSource } from "../data/datasources/http-shift-data-source";
import { RemoteShiftRepository } from "../data/repositories/remote-shift-repository";
import { LoadShift } from "../domain/usecases/load-shift";
import { LoadShifts } from "../domain/usecases/load-shifts";
import { SaveShift } from "../domain/usecases/save-shift";

export function createWorkforceFeature(http: HttpClient) {
  const shifts = new RemoteShiftRepository(new HttpShiftDataSource(http));
  return {
    loadShifts: new LoadShifts(shifts),
    loadShift: new LoadShift(shifts),
    saveShift: new SaveShift(shifts),
  };
}
