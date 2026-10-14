import type { CompensationTerms } from "./compensation-terms";
export type CompensationChange = Readonly<{
  effectiveFrom: string;
  terms: CompensationTerms;
  expectedVersion: number | null;
  expectedEmploymentVersion: number;
  reason: string;
}>;
