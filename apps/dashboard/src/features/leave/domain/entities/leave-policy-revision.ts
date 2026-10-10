import type { LeavePolicyTerms } from "./leave-policy-terms";

export type LeavePolicyRevision = Readonly<
  LeavePolicyTerms & {
    revision: number;
    effectiveFrom: string;
    active: boolean;
    actorId: string;
    reason: string;
    recordedAt: string;
  }
>;
