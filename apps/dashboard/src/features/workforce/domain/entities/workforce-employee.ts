import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { EmploymentTerms } from "../../../people/domain/entities/employment-terms";

export type WorkforceEmployee = Readonly<{
  id: EmployeeId;
  companyId: CompanyId;
  employeeNumber: string;
  legalName: string;
  status: EmploymentTerms["status"];
  version: number;
}>;
