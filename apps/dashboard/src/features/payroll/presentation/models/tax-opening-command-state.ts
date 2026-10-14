import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { TaxOpeningReview } from "../../domain/entities/tax-opening-review";

export type TaxOpeningCommandState = Readonly<{
  stage: "loading" | "editing" | "saving" | "conflict" | "unconfirmed" | "saved" | "unavailable";
  context: TaxOpeningReview | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;
export const initialTaxOpeningCommandState: TaxOpeningCommandState = {
  stage: "loading",
  context: null,
  failure: null,
  operationId: null,
  receipt: null,
};
