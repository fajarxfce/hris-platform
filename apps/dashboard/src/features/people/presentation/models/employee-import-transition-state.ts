import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { EmployeeImportSummary } from "../../domain/entities/employee-import-summary";

export type EmployeeImportTransitionState = Readonly<{
  stage:
    | "loading"
    | "reviewing"
    | "unavailable"
    | "submitting"
    | "unconfirmed"
    | "conflict"
    | "saved";
  review: EmployeeImportSummary | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;
export const initialEmployeeImportTransitionState: EmployeeImportTransitionState = Object.freeze({
  stage: "loading",
  review: null,
  failure: null,
  operationId: null,
  receipt: null,
});
