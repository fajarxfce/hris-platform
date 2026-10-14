import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { PayrollPeriodDetails } from "../../domain/entities/payroll-period-details";
export type PayrollPeriodCancellationState = Readonly<{
  stage: "loading" | "reviewing" | "saving" | "unconfirmed" | "conflict" | "unavailable" | "saved";
  review: PayrollPeriodDetails | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;
export const initialPayrollPeriodCancellationState: PayrollPeriodCancellationState = {
  stage: "loading",
  review: null,
  failure: null,
  operationId: null,
  receipt: null,
};
