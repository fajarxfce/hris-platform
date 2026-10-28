import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ReportKind } from "./report-catalogue";

export type ReportFilterParameters = Readonly<{
  asOf: string | null;
  from: string | null;
  until: string | null;
  status: string | null;
  companies: readonly CompanyId[];
}>;
export type ReportFilterDefinition = Readonly<{
  name: string;
  kind: ReportKind;
  definitionVersion: string;
  parameters: ReportFilterParameters;
}>;
export type SavedReportFilter = ReportFilterDefinition &
  Readonly<{
    id: string;
    companyId: CompanyId;
    version: number;
    createdAt: string;
    updatedAt: string;
  }>;
export type SaveReportFilterChange = ReportFilterDefinition &
  Readonly<{
    id: string;
    expectedVersion: number | null;
  }>;
export type DeleteReportFilterChange = Readonly<{ id: string; expectedVersion: number }>;
