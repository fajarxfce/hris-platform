import type { AccountId } from "../../../../core/domain/identifiers";

export const lifecycleTaskStatuses = ["PENDING", "DONE", "WAIVED"] as const;
export type LifecycleTaskStatus = (typeof lifecycleTaskStatuses)[number];
export type LifecycleTask = Readonly<{
  key: string;
  title: string;
  required: boolean;
  dueDate: string;
  assigneeId: AccountId | null;
  status: LifecycleTaskStatus;
  completedBy: AccountId | null;
  completedAt: string | null;
}>;
