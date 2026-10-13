import type { HttpClient } from "../../../core/data/http/http-client";
import type { FileRepository } from "../../../core/domain/files/file-repository";
import { HttpExpenseClaimDataSource } from "../data/datasources/http-expense-claim-data-source";
import { HttpExpensePaymentDataSource } from "../data/datasources/http-expense-payment-data-source";
import { HttpExpensePolicyDataSource } from "../data/datasources/http-expense-policy-data-source";
import { RemoteExpenseClaimRepository } from "../data/repositories/remote-expense-claim-repository";
import { RemoteExpensePaymentRepository } from "../data/repositories/remote-expense-payment-repository";
import { RemoteExpensePolicyRepository } from "../data/repositories/remote-expense-policy-repository";
import { DecideExpenseSubmission } from "../domain/usecases/decide-expense-submission";
import { DownloadExpensePayment } from "../domain/usecases/download-expense-payment";
import { DownloadExpenseReceipt } from "../domain/usecases/download-expense-receipt";
import { LoadExpenseClaim } from "../domain/usecases/load-expense-claim";
import { LoadExpenseClaims } from "../domain/usecases/load-expense-claims";
import { LoadExpensePayment } from "../domain/usecases/load-expense-payment";
import { LoadExpensePayments } from "../domain/usecases/load-expense-payments";
import { LoadExpensePolicies } from "../domain/usecases/load-expense-policies";
import { LoadExpensePolicy } from "../domain/usecases/load-expense-policy";
import { LoadExpenseSubmission } from "../domain/usecases/load-expense-submission";
import { ReviewExpenseDecision } from "../domain/usecases/review-expense-decision";
import { ReviewExpensePaymentTransition } from "../domain/usecases/review-expense-payment-transition";
import { SaveExpensePolicy } from "../domain/usecases/save-expense-policy";
import { TransitionExpensePayment } from "../domain/usecases/transition-expense-payment";
import type { ExpenseUseCases } from "../presentation/contracts/expense-use-cases";

export function createExpenseFeature(
  http: HttpClient,
  files: Pick<FileRepository, "downloadBinary" | "downloadText">,
): ExpenseUseCases {
  const policies = new RemoteExpensePolicyRepository(new HttpExpensePolicyDataSource(http));
  const claims = new RemoteExpenseClaimRepository(new HttpExpenseClaimDataSource(http));
  const payments = new RemoteExpensePaymentRepository(new HttpExpensePaymentDataSource(http));
  return {
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
