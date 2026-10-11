import type { Failure } from "../../../../core/domain/result";
import type { ShiftDefinition } from "../../domain/entities/shift-definition";

export type ShiftState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  shift: ShiftDefinition | null;
  failure: Failure | null;
}>;
export const initialShiftState: ShiftState = Object.freeze({
  stage: "idle",
  shift: null,
  failure: null,
});
