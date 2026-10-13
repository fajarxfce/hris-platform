import type { CompanyId } from "../../../../core/domain/identifiers";

export type LeavePreparationEmployee = Readonly<{
  companyId: CompanyId;
  id: string;
  employeeNumber: string;
  legalName: string;
  status: "PROBATION" | "ACTIVE" | "SUSPENDED" | "ENDED";
  version: number;
}>;
export type LeavePreparationDirectory = Readonly<{
  companyDate: string;
  timezone: string;
  employees: Readonly<{ items: readonly LeavePreparationEmployee[]; nextCursor: string | null }>;
}>;
