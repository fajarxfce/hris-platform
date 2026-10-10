import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";

export type EmployeeImportId = string & { readonly employeeImportId: unique symbol };
export const employeeImportStatuses = [
  "PREVIEWING",
  "REVIEW",
  "IMPORTING",
  "COMPLETED",
  "STOPPED",
  "CANCELLED",
] as const;
export type EmployeeImportStatus = (typeof employeeImportStatuses)[number];
export type EmployeeImport = Readonly<{
  id: EmployeeImportId;
  companyId: CompanyId;
  fileName: string;
  sourceHash: string;
  rowCount: number;
  status: EmployeeImportStatus;
  jobId: string;
  version: number;
  createdBy: AccountId;
  createdAt: string;
  reason: string;
}>;
export type EmployeeImportPage = Readonly<{
  items: readonly EmployeeImport[];
  nextCursor: string | null;
}>;
