import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { OvertimeRequestDetails } from "../../domain/entities/overtime-request-details";

export type OvertimeActionState = Readonly<{
  stage:
    | "loading"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "conflict"
    | "saved"
    | "unavailable";
  review: OvertimeRequestDetails | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;

export const initialOvertimeActionState: OvertimeActionState = Object.freeze({
  stage: "loading",
  review: null,
  failure: null,
  receipt: null,
  operationId: null,
});
