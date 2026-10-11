import type { Failure } from "../../../../core/domain/result";
import type { ShiftPage } from "../../domain/entities/shift-page";

export type ShiftDirectoryState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  page: ShiftPage | null;
  failure: Failure | null;
}>;
export const initialShiftDirectoryState: ShiftDirectoryState = Object.freeze({
  stage: "idle",
  page: null,
  failure: null,
});
