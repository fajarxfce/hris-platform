import type { ReportKind } from "./report-catalogue";
import type { ReportFilterParameters } from "./saved-report-filter";
export type ReportSelection = Readonly<{
  kind: ReportKind;
  definitionVersion: string;
  parameters: ReportFilterParameters;
}>;
