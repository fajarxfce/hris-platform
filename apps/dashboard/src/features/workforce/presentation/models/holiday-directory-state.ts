import type { Failure } from "../../../../core/domain/result";
import type { WorkHoliday } from "../../domain/entities/work-holiday";

export type HolidayDirectoryState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  holidays: readonly WorkHoliday[] | null;
  failure: Failure | null;
}>;
export const initialHolidayDirectoryState: HolidayDirectoryState = Object.freeze({
  stage: "idle",
  holidays: null,
  failure: null,
});
