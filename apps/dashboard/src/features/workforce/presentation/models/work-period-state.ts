import type { Failure } from "../../../../core/domain/result";
import type { WorkPeriodOverview } from "../../domain/entities/work-period-overview";

export type WorkPeriodState = Readonly<{
  stage: "loading" | "updating" | "ready" | "unavailable";
  overview: WorkPeriodOverview | null;
  failure: Failure | null;
  updatesPaused: boolean;
}>;

export const initialWorkPeriodState: WorkPeriodState = Object.freeze({
  stage: "loading",
  overview: null,
  failure: null,
  updatesPaused: false,
});
