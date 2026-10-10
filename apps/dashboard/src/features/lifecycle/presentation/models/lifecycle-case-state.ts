import type { Failure } from "../../../../core/domain/result";
import type { LifecycleCase } from "../../domain/entities/lifecycle-case";
import type { LifecycleTaskContext } from "../../domain/entities/lifecycle-task-context";

export type LifecycleCaseState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  case: LifecycleCase | null;
  selectedTask: LifecycleTaskContext | null;
  taskPanel: "status" | "assignment";
  failure: Failure | null;
}>;
export const initialLifecycleCaseState: LifecycleCaseState = Object.freeze({
  stage: "loading",
  case: null,
  selectedTask: null,
  taskPanel: "status",
  failure: null,
});
