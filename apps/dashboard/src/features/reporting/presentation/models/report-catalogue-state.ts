import type { Failure } from "../../../../core/domain/result";
import type { ReportCatalogue } from "../../domain/entities/report-catalogue";

export type ReportCatalogueState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  report: ReportCatalogue | null;
  failure: Failure | null;
}>;

export const initialReportCatalogueState: ReportCatalogueState = {
  stage: "idle",
  report: null,
  failure: null,
};
