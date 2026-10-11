import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { EmploymentTransferReview } from "../../domain/entities/employment-transfer-review";

export type EmploymentTransferState = Readonly<{
  stage:
    | "choosing"
    | "loading"
    | "editing"
    | "confirming"
    | "saving"
    | "unconfirmed"
    | "saved"
    | "conflict"
    | "unavailable";
  targetCompanyId: string;
  review: EmploymentTransferReview | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: OperationId | null;
}>;
export const initialEmploymentTransferState: EmploymentTransferState = Object.freeze({
  stage: "choosing",
  targetCompanyId: "",
  review: null,
  failure: null,
  receipt: null,
  operationId: null,
});
