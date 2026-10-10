import type { Failure } from "../../../../core/domain/result";
import type { EmployeeImportPage } from "../../domain/entities/employee-import";
export type EmployeeImportsState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: EmployeeImportPage | null;
  failure: Failure | null;
}>;
export const initialEmployeeImportsState: EmployeeImportsState = Object.freeze({
  stage: "loading",
  page: null,
  failure: null,
});
