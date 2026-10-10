import type { LifecycleTaskAssignment } from "../../domain/entities/lifecycle-task-assignment";
import type { LifecycleTaskAssignmentDto } from "../models/lifecycle-task-assignment-dto";

export function toLifecycleTaskAssignmentDto(
  change: LifecycleTaskAssignment,
): LifecycleTaskAssignmentDto {
  return {
    expectedVersion: change.expectedVersion,
    assigneeId: change.assigneeId,
    reason: change.reason,
  };
}
