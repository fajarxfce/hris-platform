import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { EmploymentRevisionDetails } from "../../domain/entities/employment-revision-details";

export type EmploymentCancellationState = Readonly<{
  stage:
    | "loading"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "cancelled"
    | "conflict"
    | "unavailable";
  details: EmploymentRevisionDetails | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: OperationId | null;
}>;
export const initialEmploymentCancellationState: EmploymentCancellationState = Object.freeze({
  stage: "loading",
  details: null,
  failure: null,
  receipt: null,
  operationId: null,
});
