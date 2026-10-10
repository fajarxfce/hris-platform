import type { Failure } from "../../../../core/domain/result";
import type { EmployeeImportAttempts } from "../../domain/entities/employee-import-attempt";
export type EmployeeImportAttemptsState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: EmployeeImportAttempts | null;
  failure: Failure | null;
}>;
export const initialEmployeeImportAttemptsState: EmployeeImportAttemptsState = Object.freeze({
  stage: "loading",
  page: null,
  failure: null,
});
