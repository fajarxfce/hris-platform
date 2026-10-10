import type { OrganizationUnitId } from "../../../organization/domain/entities/organization-unit";
import type { Employee, EmployeeId } from "./employee";
import type { AssignedEmploymentTerms } from "./employment-assignments";

export type EmploymentUnitReference = Readonly<{
  id: OrganizationUnitId;
  code: string;
  name: string;
  active: boolean;
}>;
export type EmploymentDetails = Readonly<{
  asOf: string;
  employee: Employee & Readonly<{ terms: AssignedEmploymentTerms }>;
  branch: EmploymentUnitReference | null;
  department: EmploymentUnitReference | null;
  position: EmploymentUnitReference | null;
  costCenter: EmploymentUnitReference | null;
  manager: Readonly<{
    id: EmployeeId;
    employeeNumber: string;
    legalName: string;
    working: boolean;
  }> | null;
}>;
