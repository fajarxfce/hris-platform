export type LifecycleTaskAssignmentDto = Readonly<{
  expectedVersion: number;
  assigneeId: string | null;
  reason: string;
}>;
