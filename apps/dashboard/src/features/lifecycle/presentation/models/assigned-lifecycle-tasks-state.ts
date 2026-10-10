import type { Failure } from "../../../../core/domain/result";
import type {
  AssignedLifecycleTaskPage,
  LifecycleTaskContext,
} from "../../domain/entities/lifecycle-task-context";

export type AssignedLifecycleTasksState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: AssignedLifecycleTaskPage | null;
  selectedTask: LifecycleTaskContext | null;
  taskPanel: "status" | "assignment";
  failure: Failure | null;
}>;
export const initialAssignedLifecycleTasksState: AssignedLifecycleTasksState = Object.freeze({
  stage: "loading",
  page: null,
  selectedTask: null,
  taskPanel: "status",
  failure: null,
});
