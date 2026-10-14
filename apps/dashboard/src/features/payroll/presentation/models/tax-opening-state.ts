import type { Failure } from "../../../../core/domain/result";
import type { TaxOpening } from "../../domain/entities/tax-opening";
import type { TaxOpeningReview } from "../../domain/entities/tax-opening-review";
export type TaxOpeningState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  review: TaxOpeningReview | null;
  selected: TaxOpening | null;
  inspecting: boolean;
  failure: Failure | null;
}>;
export const initialTaxOpeningState: TaxOpeningState = {
  stage: "loading",
  review: null,
  selected: null,
  inspecting: false,
  failure: null,
};
