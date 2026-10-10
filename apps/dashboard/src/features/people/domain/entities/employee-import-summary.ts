import type { JobStatus } from "../../../jobs/domain/entities/background-job";
import type { EmployeeImport } from "./employee-import";
import type { EmployeeImportAction } from "./employee-import-change";
import type { EmployeeImportRowStatus } from "./employee-import-row";

export type EmployeeImportSummary = Readonly<{
  batch: EmployeeImport;
  counts: Readonly<Record<EmployeeImportRowStatus, number>>;
  jobStatus: JobStatus;
  cancellationRequested: boolean;
  availableActions: readonly EmployeeImportAction[];
}>;
