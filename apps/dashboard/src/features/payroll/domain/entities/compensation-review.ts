import type { CompensationEmployee } from "./compensation-employee";
import type { CompensationRevision } from "./compensation-revision";
import type { EmployeeCompensation } from "./employee-compensation";
export type CompensationReview = Readonly<{
  asOf: string;
  employee: CompensationEmployee;
  current: EmployeeCompensation | null;
  effective: EmployeeCompensation | null;
  history: Readonly<{ items: readonly CompensationRevision[]; nextCursor: string | null }>;
  canChange: boolean;
}>;
export type CompensationEmployees = Readonly<{
  items: readonly CompensationEmployee[];
  nextCursor: string | null;
}>;
