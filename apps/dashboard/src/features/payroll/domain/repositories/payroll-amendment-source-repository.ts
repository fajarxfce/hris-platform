import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { PayrollAmendmentTerms } from "../entities/payroll-amendment";
import type { PayrollAmendmentPreview } from "../entities/payroll-amendment-calculation";
import type {
  PayrollAmendmentCandidates,
  PayrollAmendmentPreparation,
  PayrollAmendmentSourceSearch,
} from "../entities/payroll-amendment-preparation";
export interface PayrollAmendmentSourceRepository {
  list(
    company: CompanyId,
    search: PayrollAmendmentSourceSearch,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentCandidates>>;
  prepare(
    company: CompanyId,
    assessment: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentPreparation>>;
  preview(
    company: CompanyId,
    assessment: string,
    terms: PayrollAmendmentTerms,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentPreview>>;
}
