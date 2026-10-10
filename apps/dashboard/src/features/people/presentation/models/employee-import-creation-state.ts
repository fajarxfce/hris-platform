import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";

export type EmployeeImportCreationState = Readonly<{
  stage:
    | "editing"
    | "selecting"
    | "downloading"
    | "submitting"
    | "unconfirmed"
    | "saved"
    | "unavailable";
  file: Readonly<{ name: string; byteLength: number }> | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
  templateRequested: boolean;
}>;
export const initialEmployeeImportCreationState: EmployeeImportCreationState = Object.freeze({
  stage: "editing",
  file: null,
  failure: null,
  operationId: null,
  receipt: null,
  templateRequested: false,
});
