import type { EmploymentTerms } from "./employment-terms";

export type EmploymentRevision = Readonly<{
  revision: number;
  terms: EmploymentTerms;
  reason: string;
  recordedAt: string;
  cancellation: Readonly<{ reason: string; recordedAt: string }> | null;
}>;

export type EmploymentHistoryPage = Readonly<{
  items: readonly EmploymentRevision[];
  nextCursor: string | null;
}>;
