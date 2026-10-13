import type { BinaryFileContent } from "../../../../core/domain/files/binary-file";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { ExpenseClaimId } from "../entities/expense-claim";
import type { ExpenseClaimActionCommand } from "../entities/expense-claim-action-command";
import type { ExpenseClaimReview } from "../entities/expense-claim-review";
import type { ExpenseClaimSearch } from "../entities/expense-claim-search";
import type { ExpenseClaimPage } from "../entities/expense-claim-summary";
import type { ExpenseDecisionCommand } from "../entities/expense-decision-command";
import type { ExpenseDraftChange } from "../entities/expense-draft-change";
import type { ExpenseReceipt } from "../entities/expense-receipt";
import type { ExpenseSubmission } from "../entities/expense-submission";
import type { ExpenseSubmissionId } from "../entities/expense-submission-summary";
export interface ExpenseClaimRepository {
  transition(
    company: CompanyId,
    operation: OperationId,
    command: ExpenseClaimActionCommand,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  saveDraft(
    company: CompanyId,
    operation: OperationId,
    change: ExpenseDraftChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  decide(
    company: CompanyId,
    operation: OperationId,
    change: ExpenseDecisionCommand,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
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
