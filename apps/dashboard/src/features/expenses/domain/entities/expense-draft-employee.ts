import type { CompanyId } from "../../../../core/domain/identifiers";
export type ExpenseDraftEmployee = Readonly<{
  id: string;
  companyId: CompanyId;
  employeeNumber: string;
  legalName: string;
  status: string;
  version: number;
}>;
export type ExpenseDraftDirectory = Readonly<{
  companyDate: string;
  timezone: string;
  employees: Readonly<{ items: readonly ExpenseDraftEmployee[]; nextCursor: string | null }>;
}>;
