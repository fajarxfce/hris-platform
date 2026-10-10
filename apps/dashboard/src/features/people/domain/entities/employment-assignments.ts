import type { OrganizationUnitId } from "../../../organization/domain/entities/organization-unit";
import type { EmployeeId } from "./employee";
import type { EmploymentTerms } from "./employment-terms";

export type EmploymentAssignments = Readonly<{
  branchId: OrganizationUnitId | null;
  departmentId: OrganizationUnitId | null;
  positionId: OrganizationUnitId | null;
  costCenterId: OrganizationUnitId | null;
  managerId: EmployeeId | null;
}>;
export type AssignedEmploymentTerms = EmploymentTerms & EmploymentAssignments;
