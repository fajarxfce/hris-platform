import type { Failure } from "../../../../core/domain/result";
import type { EmploymentTransfer } from "../../domain/entities/employment-transfer";

export type EmploymentTransfersState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  items: readonly EmploymentTransfer[] | null;
  failure: Failure | null;
}>;
export const initialEmploymentTransfersState: EmploymentTransfersState = Object.freeze({
  stage: "loading",
  items: null,
  failure: null,
});
