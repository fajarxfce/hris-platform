import type { EmployeeId } from "./employee";
import type { EmploymentAssignments } from "./employment-assignments";
import type { EmploymentTerms } from "./employment-terms";
import type { PersonId, PersonProfileFields } from "./person-profile";

export type EmployeeCreation = PersonProfileFields &
  EmploymentAssignments &
  Omit<EmploymentTerms, "effectiveFrom"> &
  Readonly<{
    employeeId: EmployeeId;
    personId: PersonId;
    employeeNumber: string;
    reason: string;
  }>;
