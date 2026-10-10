export type LifecycleTaskChangeDto = Readonly<{
  expectedVersion: number;
  status: "PENDING" | "DONE" | "WAIVED";
  reason: string;
}>;
