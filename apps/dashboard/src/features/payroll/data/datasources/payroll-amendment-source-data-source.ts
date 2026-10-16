import type {
  PayrollAmendmentPreviewDto,
  PayrollAmendmentProposalDto,
} from "../models/payroll-amendment-dto";
import type {
  PayrollAmendmentCandidatesDto,
  PayrollAmendmentPreparationDto,
  PayrollAmendmentSourceSearchDto,
} from "../models/payroll-amendment-preparation-dto";
export interface PayrollAmendmentSourceDataSource {
  list(
    company: string,
    search: PayrollAmendmentSourceSearchDto,
    signal: AbortSignal,
  ): Promise<PayrollAmendmentCandidatesDto>;
  prepare(
    company: string,
    assessment: string,
    signal: AbortSignal,
  ): Promise<PayrollAmendmentPreparationDto>;
  preview(
    company: string,
    assessment: string,
    terms: PayrollAmendmentProposalDto,
    signal: AbortSignal,
  ): Promise<PayrollAmendmentPreviewDto>;
}
