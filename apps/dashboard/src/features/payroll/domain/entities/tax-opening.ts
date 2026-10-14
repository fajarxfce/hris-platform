import type { CompanyId } from "../../../../core/domain/identifiers";
import type { TaxOpeningTerms } from "./tax-opening-terms";
export const taxOpeningStatuses = ["DRAFT", "VERIFIED"] as const;
export type TaxOpeningStatus = (typeof taxOpeningStatuses)[number];
export type TaxOpeningSummary = Readonly<{
  id: string;
  employeeId: string;
  year: string;
  version: number;
  status: TaxOpeningStatus;
  preparedBy: string;
  verifiedBy: string | null;
  recordedAt: string;
  reason: string;
}>;
export type TaxOpening = TaxOpeningSummary &
  Readonly<{
    companyId: CompanyId;
    terms: TaxOpeningTerms;
  }>;
