import type { BinaryResponseDto } from "../../../../core/data/http/binary-response-dto";
import type { ExpenseClaimPageDto, ExpenseClaimReviewDto } from "../models/expense-claim-dto";
import type { ExpenseDecisionDto, ExpenseDecisionReceiptDto } from "../models/expense-decision-dto";
import type {
  ExpenseReceiptMetadataDto,
  ExpenseSubmissionDto,
} from "../models/expense-submission-dto";
export interface ExpenseClaimDataSource {
  decide(
    company: string,
    id: string,
    operation: string,
    change: ExpenseDecisionDto,
    signal: AbortSignal,
  ): Promise<ExpenseDecisionReceiptDto>;
  list(
    company: string,
    from: string,
    until: string,
    status: string | null,
    employment: string | null,
    after: string | null,
    signal: AbortSignal,
  ): Promise<ExpenseClaimPageDto>;
  review(
    company: string,
    id: string,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<ExpenseClaimReviewDto>;
  submission(company: string, id: string, signal: AbortSignal): Promise<ExpenseSubmissionDto>;
  downloadReceipt(
    company: string,
    id: string,
    receipt: Pick<ExpenseReceiptMetadataDto, "revisionId" | "mediaType" | "size" | "sha256">,
    signal: AbortSignal,
  ): Promise<BinaryResponseDto>;
}
