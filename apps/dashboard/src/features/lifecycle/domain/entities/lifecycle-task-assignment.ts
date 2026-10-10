export type LifecycleTaskAssignment = Readonly<{
  caseId: string;
  taskKey: string;
  expectedVersion: number;
  assigneeId: string | null;
  reason: string;
}>;
