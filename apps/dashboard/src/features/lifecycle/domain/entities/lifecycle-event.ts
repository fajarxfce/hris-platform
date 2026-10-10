import type { AccountId } from "../../../../core/domain/identifiers";

export type LifecycleAction =
  | "CREATED"
  | "ASSIGNED"
  | "TASK_PENDING"
  | "TASK_DONE"
  | "TASK_WAIVED"
  | "CANCELLED"
  | "COMPLETED";
export type LifecycleEvent = Readonly<{
  version: number;
  taskKey: string | null;
  action: LifecycleAction;
  assigneeId: AccountId | null;
  actorId: AccountId;
  reason: string;
  recordedAt: string;
}>;
export type LifecycleHistoryPage = Readonly<{
  items: readonly LifecycleEvent[];
  nextCursor: string | null;
}>;
