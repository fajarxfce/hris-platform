import type { CompanyId } from "../../../../core/domain/identifiers";

export type DocumentEmployee = Readonly<{
  id: string;
  companyId: CompanyId;
  employeeNumber: string;
  name: string;
  status: "PROBATION" | "ACTIVE" | "SUSPENDED" | "ENDED";
  version: number;
}>;
export type DocumentEmployeeSearch = Readonly<{ query: string; after: string | null }>;
export type DocumentEmployeeDirectory = Readonly<{
  companyId: CompanyId;
  asOf: string;
  timezone: string;
  query: string;
  employees: Readonly<{ items: readonly DocumentEmployee[]; nextCursor: string | null }>;
}>;
