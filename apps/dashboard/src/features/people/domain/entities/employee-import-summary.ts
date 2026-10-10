import type { EmployeeImport } from "./employee-import";
import type { EmployeeImportRowStatus } from "./employee-import-row";

export type EmployeeImportSummary = Readonly<{
  batch: EmployeeImport;
  counts: Readonly<Record<EmployeeImportRowStatus, number>>;
}>;
