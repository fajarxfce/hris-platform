import type { LeavePolicyTerms } from "./leave-policy-terms";

export type LeavePolicyChange = LeavePolicyTerms &
  Readonly<{
    id: string;
    code: string;
    active: boolean;
    effectiveFrom: string;
    expectedVersion: number | null;
    reason: string;
  }>;
