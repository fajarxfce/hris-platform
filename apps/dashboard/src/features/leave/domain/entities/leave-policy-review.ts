import type { LeavePolicyDefinition } from "./leave-policy-definition";
import type { LeavePolicyRevision } from "./leave-policy-revision";

export type LeavePolicyReview = Readonly<{
  current: LeavePolicyDefinition;
  history: Readonly<{ items: readonly LeavePolicyRevision[]; nextCursor: string | null }>;
}>;
