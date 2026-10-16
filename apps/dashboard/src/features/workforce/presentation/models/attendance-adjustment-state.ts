import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { AttendanceAdjustmentReview } from "../../domain/entities/attendance-adjustment-review";

export type AttendanceAdjustmentState = Readonly<{
  stage: "loading" | "reviewing" | "saving" | "saved" | "unconfirmed" | "conflict" | "unavailable";
  review: AttendanceAdjustmentReview | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;
export const initialAttendanceAdjustmentState: AttendanceAdjustmentState = Object.freeze({
  stage: "loading",
  review: null,
  failure: null,
  operationId: null,
  receipt: null,
});
