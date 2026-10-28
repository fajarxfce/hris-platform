import type { Failure } from "../../../../core/domain/result";
import type { SavedReportFilter } from "../../domain/entities/saved-report-filter";
export type SavedReportFiltersState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  items: readonly SavedReportFilter[];
  failure: Failure | null;
}>;
export const initialSavedReportFiltersState: SavedReportFiltersState = {
  stage: "idle",
  items: [],
  failure: null,
};
