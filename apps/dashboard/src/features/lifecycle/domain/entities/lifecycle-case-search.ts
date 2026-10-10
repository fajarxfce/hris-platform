import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { LifecycleCaseId, LifecycleCaseStatus } from "./lifecycle-case";

export type LifecycleCaseSearch = Readonly<{
  status: string;
  employmentId: string | null;
  after: string | null;
}>;
export type LifecycleCaseFilter = Readonly<{
  status: LifecycleCaseStatus | null;
  employmentId: EmployeeId | null;
  after: LifecycleCaseId | null;
}>;
