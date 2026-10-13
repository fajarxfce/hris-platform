import type { BinaryFileContent } from "../../../../core/domain/files/binary-file";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { ExpenseClaimId } from "../entities/expense-claim";
import type { ExpenseClaimReview } from "../entities/expense-claim-review";
import type { ExpenseClaimSearch } from "../entities/expense-claim-search";
import type { ExpenseClaimPage } from "../entities/expense-claim-summary";
import type { ExpenseReceipt } from "../entities/expense-receipt";
import type { ExpenseSubmission } from "../entities/expense-submission";
import type { ExpenseSubmissionId } from "../entities/expense-submission-summary";
export interface ExpenseClaimRepository {
  list(
    company: CompanyId,
    search: ExpenseClaimSearch,
    signal: AbortSignal,
  ): Promise<Result<ExpenseClaimPage>>;
  review(
    company: CompanyId,
    id: ExpenseClaimId,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<Result<ExpenseClaimReview>>;
  submission(
    company: CompanyId,
    id: ExpenseSubmissionId,
    signal: AbortSignal,
  ): Promise<Result<ExpenseSubmission>>;
  downloadReceipt(
    company: CompanyId,
    id: ExpenseSubmissionId,
    receipt: ExpenseReceipt,
    signal: AbortSignal,
  ): Promise<Result<BinaryFileContent>>;
}
