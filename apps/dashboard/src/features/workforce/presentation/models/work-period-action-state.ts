import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { WorkPeriodActionContext } from "../../domain/entities/work-period-action-context";
import type { WorkPeriodOverview } from "../../domain/entities/work-period-overview";

export type WorkPeriodActionState = Readonly<{
  stage:
    | "loading"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "conflict"
    | "saved"
    | "unavailable";
  overview: WorkPeriodOverview | null;
  context: WorkPeriodActionContext | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialWorkPeriodActionState: WorkPeriodActionState = Object.freeze({
  stage: "loading",
  overview: null,
  context: null,
  failure: null,
  receipt: null,
  operationId: null,
});
