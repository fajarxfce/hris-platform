import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
export type PayrollCalculationCommandState<Review> = Readonly<{
  stage: "loading" | "reviewing" | "saving" | "saved" | "unconfirmed" | "conflict" | "unavailable";
  review: Review | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;
export function initialPayrollCalculationCommandState<
  Review,
>(): PayrollCalculationCommandState<Review> {
  return { stage: "loading", review: null, failure: null, operationId: null, receipt: null };
}
