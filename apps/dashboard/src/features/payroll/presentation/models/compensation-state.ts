import type { Failure } from "../../../../core/domain/result";
import type { CompensationReview } from "../../domain/entities/compensation-review";
import type { CompensationRevision } from "../../domain/entities/compensation-revision";
import type { EmployeeCompensation } from "../../domain/entities/employee-compensation";
export type CompensationState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  review: CompensationReview | null;
  selected: Readonly<{
    compensation: EmployeeCompensation;
    evidence: CompensationRevision | null;
  }> | null;
  failure: Failure | null;
}>;
export const initialCompensationState: CompensationState = {
  stage: "loading",
  review: null,
  selected: null,
  failure: null,
};
