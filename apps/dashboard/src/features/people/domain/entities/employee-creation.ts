import type { OrganizationUnitId } from "../../../organization/domain/entities/organization-unit";
import type { EmployeeId } from "./employee";
import type { EmploymentTerms } from "./employment-terms";
import type { PersonId, PersonProfileFields } from "./person-profile";

export type EmployeeCreation = PersonProfileFields &
  Readonly<{
    employeeId: EmployeeId;
    personId: PersonId;
    employeeNumber: string;
    startDate: string;
    endDate: string | null;
    contract: EmploymentTerms["contract"];
    status: EmploymentTerms["status"];
    branchId: OrganizationUnitId | null;
    departmentId: OrganizationUnitId | null;
    positionId: OrganizationUnitId | null;
    costCenterId: OrganizationUnitId | null;
    managerId: EmployeeId | null;
    reason: string;
  }>;
