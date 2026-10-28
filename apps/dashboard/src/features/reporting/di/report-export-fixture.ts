import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { ReportExportDetails, StartReportExport } from "../domain/entities/report-export";
export const exportCompany = "77ed1727-6b89-48f9-ae10-f5f3f87c93f0" as CompanyId;
export const exportForeign = "87ed1727-6b89-48f9-ae10-f5f3f87c93f1" as CompanyId;
export const exportId = "00000000-0000-4000-8000-000000000010";
export const exportOperation = "00000000-0000-4000-8000-000000000001" as OperationId;
export const exportAccess = {
  companyId: exportCompany,
  permissions: [
    "reports.read",
    "people.read",
    "workforce.read",
    "leave.read",
    "expenses.read",
    "payroll.read",
  ],
};
export const exportSelection: StartReportExport = {
  kind: "HEADCOUNT",
  definitionVersion: "headcount.v1",
  parameters: {
    asOf: "2026-10-01",
    from: null,
    until: null,
    status: null,
    companies: [exportCompany],
  },
  dataset: "SUMMARY",
};
export function exportFixture(overrides: Partial<ReportExportDetails> = {}): ReportExportDetails {
  const { dataset, ...selection } = exportSelection;
  return {
    id: exportId,
    companyId: exportCompany,
    selection,
    dataset,
    status: "QUEUED",
    version: 0,
    attempt: 1,
    createdAt: "2026-10-01T00:00:00.000001Z",
    updatedAt: "2026-10-01T00:00:00.000001Z",
    expiresAt: "2026-10-08T00:00:00.000001Z",
    expired: false,
    retiredAt: null,
    completedRows: 0,
    bytes: 0,
    jobId: "00000000-0000-4000-8000-000000000020",
    jobStatus: "QUEUED",
    cancellationRequested: false,
    failureCode: null,
    ...overrides,
  };
}
export const exportNow = () => Date.parse("2026-10-02T00:00:00Z");
