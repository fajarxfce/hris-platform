import type { AssignedEmploymentTerms } from "./employment-assignments";

export const employeeImportRowStatuses = [
  "PENDING",
  "READY",
  "INVALID",
  "APPLIED",
  "REJECTED",
] as const;
export type EmployeeImportRowStatus = (typeof employeeImportRowStatuses)[number];
/** A proposal may violate business rules; its field issues remain part of the review. */
export type EmployeeImportProposal = Readonly<{
  employeeId: string;
  employeeNumber: string;
  legalName: string;
  birthDate: string | null;
  nationality: string;
  email: string | null;
  terms: AssignedEmploymentTerms;
}>;
export type EmployeeImportRow = Readonly<{
  number: number;
  employeeNumber: string;
  legalName: string;
  status: EmployeeImportRowStatus;
  issues: Readonly<Record<string, string>>;
  proposed: EmployeeImportProposal | null;
  createdEmploymentId: string | null;
}>;
export type EmployeeImportRows = Readonly<{
  items: readonly EmployeeImportRow[];
  nextCursor: string | null;
}>;
