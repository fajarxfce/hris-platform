import type { BinaryResponseDto } from "../../../../core/data/http/binary-response-dto";
import type { ExpenseClaimPageDto, ExpenseClaimReviewDto } from "../models/expense-claim-dto";
import type { ExpenseClaimReceiptDto } from "../models/expense-claim-receipt-dto";
import type { ExpenseDecisionDto } from "../models/expense-decision-dto";
import type { ExpenseDraftChangeDto } from "../models/expense-draft-change-dto";
import type {
  ExpenseReceiptMetadataDto,
  ExpenseSubmissionDto,
} from "../models/expense-submission-dto";
export interface ExpenseClaimDataSource {
  saveDraft(
    company: string,
    id: string,
    operation: string,
    change: ExpenseDraftChangeDto,
    signal: AbortSignal,
  ): Promise<ExpenseClaimReceiptDto>;
  decide(
    company: string,
    id: string,
    operation: string,
    change: ExpenseDecisionDto,
    signal: AbortSignal,
  ): Promise<ExpenseClaimReceiptDto>;
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
