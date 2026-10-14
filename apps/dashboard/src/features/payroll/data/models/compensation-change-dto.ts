import type { CompensationTermsDto } from "./compensation-dto";
export type CompensationChangeDto = Readonly<{
  effectiveFrom: string;
  terms: CompensationTermsDto;
  expectedVersion: number | null;
  expectedEmploymentVersion: number;
  reason: string;
}>;
