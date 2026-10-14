import type { Failure } from "../../../../core/domain/result";
import type { TaxOpeningEmployees } from "../../domain/entities/tax-opening-review";
export type TaxOpeningEmployeesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: TaxOpeningEmployees | null;
  failure: Failure | null;
}>;
export const initialTaxOpeningEmployeesState: TaxOpeningEmployeesState = {
  stage: "loading",
  page: null,
  failure: null,
};
