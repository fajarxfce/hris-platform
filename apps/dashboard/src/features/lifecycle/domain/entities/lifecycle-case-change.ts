export type LifecycleCaseAction = "cancel" | "completeOnboarding";
export type LifecycleCaseChange = Readonly<{
  caseId: string;
  expectedVersion: number;
  reason: string;
}>;
