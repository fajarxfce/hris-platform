import type { ExpensePolicyTerms } from "./expense-policy-terms";

export type ExpensePolicyChange = ExpensePolicyTerms &
  Readonly<{
    id: string;
    code: string;
    active: boolean;
    effectiveFrom: string;
    expectedVersion: number | null;
    reason: string;
  }>;
