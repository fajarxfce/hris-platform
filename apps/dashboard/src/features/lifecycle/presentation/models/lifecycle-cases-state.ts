import type { Failure } from "../../../../core/domain/result";
import type { LifecycleCasePage } from "../../domain/entities/lifecycle-case";

export type LifecycleCasesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: LifecycleCasePage | null;
  failure: Failure | null;
}>;
export const initialLifecycleCasesState: LifecycleCasesState = Object.freeze({
  stage: "loading",
  page: null,
  failure: null,
});
