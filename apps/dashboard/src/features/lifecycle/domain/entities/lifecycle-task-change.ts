import type { LifecycleTaskStatus } from "./lifecycle-task";

export type LifecycleTaskChange = Readonly<{
  caseId: string;
  taskKey: string;
  expectedVersion: number;
  status: LifecycleTaskStatus;
  reason: string;
}>;
