import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmploymentTerms } from "./employment-terms";

export type EmployeeId = string & { readonly employeeId: unique symbol };

/** The directory projection deliberately excludes private profile and account data. */
export type Employee = Readonly<{
  id: EmployeeId;
  companyId: CompanyId;
  employeeNumber: string;
  legalName: string;
  email: string | null;
  terms: EmploymentTerms;
  version: number;
  appliedRevision: number;
}>;
