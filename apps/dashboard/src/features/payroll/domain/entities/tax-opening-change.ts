import type { TaxOpeningTerms } from "./tax-opening-terms";
export type TaxOpeningChange = Readonly<{
  expectedEmploymentVersion: number;
  expectedVersion: number | null;
  terms: TaxOpeningTerms;
  reason: string;
}>;
export type TaxOpeningVerification = Readonly<{ expectedVersion: number; reason: string }>;
