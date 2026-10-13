import type { DecideExpenseSubmission } from "../../domain/usecases/decide-expense-submission";
import type { DownloadExpenseReceipt } from "../../domain/usecases/download-expense-receipt";
import type { LoadExpenseClaim } from "../../domain/usecases/load-expense-claim";
import type { LoadExpenseClaims } from "../../domain/usecases/load-expense-claims";
import type { LoadExpensePolicies } from "../../domain/usecases/load-expense-policies";
import type { LoadExpensePolicy } from "../../domain/usecases/load-expense-policy";
import type { LoadExpenseSubmission } from "../../domain/usecases/load-expense-submission";
import type { ReviewExpenseDecision } from "../../domain/usecases/review-expense-decision";
import type { SaveExpensePolicy } from "../../domain/usecases/save-expense-policy";

export type ExpenseUseCases = Readonly<{
  reviewDecision: Pick<ReviewExpenseDecision, "execute">;
  decideSubmission: Pick<DecideExpenseSubmission, "execute">;
  loadClaims: Pick<LoadExpenseClaims, "execute">;
  loadClaim: Pick<LoadExpenseClaim, "execute">;
  loadSubmission: Pick<LoadExpenseSubmission, "execute">;
  downloadReceipt: Pick<DownloadExpenseReceipt, "execute">;
  loadPolicies: Pick<LoadExpensePolicies, "execute">;
  loadPolicy: Pick<LoadExpensePolicy, "execute">;
  savePolicy: Pick<SaveExpensePolicy, "execute">;
}>;
