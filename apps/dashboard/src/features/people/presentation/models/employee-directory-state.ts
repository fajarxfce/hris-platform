import type { Failure } from "../../../../core/domain/result";
import type { EmployeePage } from "../../domain/entities/employee-page";

export type EmployeeDirectoryState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  page: EmployeePage | null;
  failure: Failure | null;
}>;
export const initialEmployeeDirectoryState: EmployeeDirectoryState = Object.freeze({
  stage: "idle",
  page: null,
  failure: null,
});
