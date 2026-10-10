import type { Failure } from "../../../../core/domain/result";
import type { LifecycleEvent, LifecycleHistoryPage } from "../../domain/entities/lifecycle-event";

export type LifecycleHistoryState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: LifecycleHistoryPage | null;
  selected: LifecycleEvent | null;
  failure: Failure | null;
}>;
export const initialLifecycleHistoryState: LifecycleHistoryState = Object.freeze({
  stage: "loading",
  page: null,
  selected: null,
  failure: null,
});
