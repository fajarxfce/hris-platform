import type { EmployeeId } from "../../../people/domain/entities/employee";

export type LifecycleEmployeeReference = Readonly<{
  id: EmployeeId;
  employeeNumber: string;
  name: string;
}>;
