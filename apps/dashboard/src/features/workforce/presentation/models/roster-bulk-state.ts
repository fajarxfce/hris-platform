import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { RosterBulkReview } from "../../domain/entities/roster-bulk-review";

export type RosterBulkState = Readonly<{
  stage:
    | "editing"
    | "preparing"
    | "reviewing"
    | "saving"
    | "unconfirmed"
    | "saved"
    | "conflict"
    | "unavailable";
  review: RosterBulkReview | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;
export const initialRosterBulkState: RosterBulkState = Object.freeze({
  stage: "editing",
  review: null,
  failure: null,
  operationId: null,
  receipt: null,
});
