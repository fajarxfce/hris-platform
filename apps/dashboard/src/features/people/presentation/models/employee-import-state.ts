import type { Failure } from "../../../../core/domain/result";
import type { EmployeeImportSummary } from "../../domain/entities/employee-import-summary";
export type EmployeeImportState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  summary: EmployeeImportSummary | null;
  failure: Failure | null;
}>;
export const initialEmployeeImportState: EmployeeImportState = Object.freeze({
  stage: "loading",
  summary: null,
  failure: null,
});
