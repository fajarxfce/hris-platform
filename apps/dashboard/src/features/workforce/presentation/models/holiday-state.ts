import type { Failure } from "../../../../core/domain/result";
import type { WorkHoliday } from "../../domain/entities/work-holiday";

export type HolidayState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  holiday: WorkHoliday | null;
  failure: Failure | null;
}>;
export const initialHolidayState: HolidayState = Object.freeze({
  stage: "idle",
  holiday: null,
  failure: null,
});
