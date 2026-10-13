import type { HttpClient } from "../../../core/data/http/http-client";
import type { FileRepository } from "../../../core/domain/files/file-repository";
import { HttpExpenseClaimDataSource } from "../data/datasources/http-expense-claim-data-source";
import { HttpExpenseDraftingDataSource } from "../data/datasources/http-expense-drafting-data-source";
import { HttpExpensePaymentDataSource } from "../data/datasources/http-expense-payment-data-source";
import { HttpExpensePolicyDataSource } from "../data/datasources/http-expense-policy-data-source";
import { RemoteExpenseClaimRepository } from "../data/repositories/remote-expense-claim-repository";
import { RemoteExpenseDraftingRepository } from "../data/repositories/remote-expense-drafting-repository";
import { RemoteExpensePaymentRepository } from "../data/repositories/remote-expense-payment-repository";
import { RemoteExpensePolicyRepository } from "../data/repositories/remote-expense-policy-repository";
import { DecideExpenseSubmission } from "../domain/usecases/decide-expense-submission";
import { DownloadExpensePayment } from "../domain/usecases/download-expense-payment";
import { DownloadExpenseReceipt } from "../domain/usecases/download-expense-receipt";
import { LoadExpenseCategories } from "../domain/usecases/load-expense-categories";
import { LoadExpenseClaim } from "../domain/usecases/load-expense-claim";
import { LoadExpenseClaims } from "../domain/usecases/load-expense-claims";
import { LoadExpenseCostCenters } from "../domain/usecases/load-expense-cost-centers";
import { LoadExpenseDraftEmployees } from "../domain/usecases/load-expense-draft-employees";
import { LoadExpenseDraftReceipts } from "../domain/usecases/load-expense-draft-receipts";
import { LoadExpensePayables } from "../domain/usecases/load-expense-payables";
import { LoadExpensePayment } from "../domain/usecases/load-expense-payment";
import { LoadExpensePayments } from "../domain/usecases/load-expense-payments";
import { LoadExpensePolicies } from "../domain/usecases/load-expense-policies";
import { LoadExpensePolicy } from "../domain/usecases/load-expense-policy";
import { LoadExpenseSubmission } from "../domain/usecases/load-expense-submission";
import { PrepareExpensePayment } from "../domain/usecases/prepare-expense-payment";
import { ReconcileExpensePayment } from "../domain/usecases/reconcile-expense-payment";
import { ReviewExpenseClaimAction } from "../domain/usecases/review-expense-claim-action";
import { ReviewExpenseDecision } from "../domain/usecases/review-expense-decision";
import { ReviewExpenseDraftEdit } from "../domain/usecases/review-expense-draft-edit";
import { ReviewExpensePaymentTransition } from "../domain/usecases/review-expense-payment-transition";
import { ReviewExpenseReconciliation } from "../domain/usecases/review-expense-reconciliation";
import { SaveExpenseDraft } from "../domain/usecases/save-expense-draft";
import { SaveExpensePolicy } from "../domain/usecases/save-expense-policy";
import { TransitionExpenseClaim } from "../domain/usecases/transition-expense-claim";
import { TransitionExpensePayment } from "../domain/usecases/transition-expense-payment";
import type { ExpenseUseCases } from "../presentation/contracts/expense-use-cases";

export function createExpenseFeature(
  http: HttpClient,
  files: Pick<FileRepository, "downloadBinary" | "downloadText">,
): ExpenseUseCases {
  const policies = new RemoteExpensePolicyRepository(new HttpExpensePolicyDataSource(http));
  const claims = new RemoteExpenseClaimRepository(new HttpExpenseClaimDataSource(http));
  const drafting = new RemoteExpenseDraftingRepository(new HttpExpenseDraftingDataSource(http));
  const payments = new RemoteExpensePaymentRepository(new HttpExpensePaymentDataSource(http));
  return {
    reviewClaimAction: new ReviewExpenseClaimAction(claims, drafting),
    transitionClaim: new TransitionExpenseClaim(claims),
    loadDraftEmployees: new LoadExpenseDraftEmployees(drafting),
    loadDraftReceipts: new LoadExpenseDraftReceipts(drafting),
    loadCostCenters: new LoadExpenseCostCenters(drafting),
    loadCategories: new LoadExpenseCategories(policies),
    reviewDraftEdit: new ReviewExpenseDraftEdit(claims, drafting),
    saveDraft: new SaveExpenseDraft(claims),
    loadPayables: new LoadExpensePayables(payments),
    preparePayment: new PrepareExpensePayment(payments),
    reviewReconciliation: new ReviewExpenseReconciliation(payments),
    reconcilePayment: new ReconcileExpensePayment(payments),
    reviewPaymentTransition: new ReviewExpensePaymentTransition(payments),
    transitionPayment: new TransitionExpensePayment(payments),
    loadPayments: new LoadExpensePayments(payments),
    loadPayment: new LoadExpensePayment(payments),
    downloadPayment: new DownloadExpensePayment(payments, files),
    reviewDecision: new ReviewExpenseDecision(claims),
    decideSubmission: new DecideExpenseSubmission(claims),
    loadClaims: new LoadExpenseClaims(claims),
    loadClaim: new LoadExpenseClaim(claims),
    loadSubmission: new LoadExpenseSubmission(claims),
    downloadReceipt: new DownloadExpenseReceipt(claims, files),
    loadPolicies: new LoadExpensePolicies(policies),
    loadPolicy: new LoadExpensePolicy(policies),
    savePolicy: new SaveExpensePolicy(policies),
  };
}
