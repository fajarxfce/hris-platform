import type { CompanyId } from "../../../../core/domain/identifiers";
import type { CompensationTerms } from "./compensation-terms";
export type EmployeeCompensation = Readonly<{
  companyId: CompanyId;
  employeeId: string;
  employeeNumber: string;
  employeeName: string;
  version: number;
  appliedRevision: number;
  effectiveFrom: string;
  terms: CompensationTerms;
}>;
