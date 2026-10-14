import type { Failure } from "../../../../core/domain/result";
import type { CompensationEmployees } from "../../domain/entities/compensation-review";
export type CompensationEmployeesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: CompensationEmployees | null;
  failure: Failure | null;
}>;
export const initialCompensationEmployeesState: CompensationEmployeesState = {
  stage: "loading",
  page: null,
  failure: null,
};
