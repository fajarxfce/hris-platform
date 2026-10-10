import type { Failure } from "../../../../core/domain/result";
import type { Employee } from "../../domain/entities/employee";

export type EmployeeDetailsState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  employee: Employee | null;
  failure: Failure | null;
}>;
export const initialEmployeeDetailsState: EmployeeDetailsState = Object.freeze({
  stage: "idle",
  employee: null,
  failure: null,
});
