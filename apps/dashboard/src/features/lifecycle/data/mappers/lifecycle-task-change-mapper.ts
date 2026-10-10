import type { LifecycleTaskChange } from "../../domain/entities/lifecycle-task-change";
import type { LifecycleTaskChangeDto } from "../models/lifecycle-task-change-dto";

export function toLifecycleTaskChangeDto(change: LifecycleTaskChange): LifecycleTaskChangeDto {
  return {
    expectedVersion: change.expectedVersion,
    status: change.status,
    reason: change.reason,
  };
}
