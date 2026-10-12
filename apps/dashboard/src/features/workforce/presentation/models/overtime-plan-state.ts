import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { OvertimePlanningReview } from "../../domain/entities/overtime-planning-review";

export type OvertimePlanState = Readonly<{
  stage:
    | "choosing"
    | "loading"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "conflict"
    | "unavailable"
    | "saved";
  workDate: string;
  employeeId: string | null;
  review: OvertimePlanningReview | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;

export const initialOvertimePlanState = (workDate: string): OvertimePlanState => ({
  stage: "choosing",
  workDate,
  employeeId: null,
  review: null,
  failure: null,
  operationId: null,
  receipt: null,
});
