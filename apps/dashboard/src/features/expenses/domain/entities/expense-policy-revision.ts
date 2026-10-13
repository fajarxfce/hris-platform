import type { ExpensePolicyTerms } from "./expense-policy-terms";

export type ExpensePolicyRevision = Readonly<
  ExpensePolicyTerms & {
    revision: number;
    effectiveFrom: string;
    active: boolean;
    actorId: string;
    reason: string;
    recordedAt: string;
  }
>;
