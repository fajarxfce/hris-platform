import type { Failure } from "../../../../core/domain/result";
import type {
  EmployeeImportRow,
  EmployeeImportRows,
} from "../../domain/entities/employee-import-row";
export type EmployeeImportRowsState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: EmployeeImportRows | null;
  failure: Failure | null;
  selected: EmployeeImportRow | null;
}>;
export const initialEmployeeImportRowsState: EmployeeImportRowsState = Object.freeze({
  stage: "loading",
  page: null,
  failure: null,
  selected: null,
});
