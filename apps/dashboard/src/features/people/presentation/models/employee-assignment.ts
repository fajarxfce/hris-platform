import type { OrganizationUnitKind } from "../../../organization/domain/entities/organization-unit";

export type EmployeeAssignmentKind = OrganizationUnitKind | "MANAGER";
export type EmployeeAssignmentOption = Readonly<{
  id: string;
  label: string;
  cells: readonly string[];
  referenceStatus?: "inactive" | "notWorking" | "unavailable";
}>;
export const assignmentFieldNames = {
  BRANCH: "branch",
  DEPARTMENT: "department",
  POSITION: "position",
  COST_CENTER: "costCenter",
  MANAGER: "manager",
} as const;
