import type { HttpClient } from "../../../core/data/http/http-client";
import type { FileRepository } from "../../../core/domain/files/file-repository";
import { HttpCompensationDataSource } from "../data/datasources/http-compensation-data-source";
import { HttpPayrollCalculationDataSource } from "../data/datasources/http-payroll-calculation-data-source";
import { HttpPayrollFinalizationDataSource } from "../data/datasources/http-payroll-finalization-data-source";
import { HttpPayrollInputDataSource } from "../data/datasources/http-payroll-input-data-source";
import { HttpPayrollPaymentDataSource } from "../data/datasources/http-payroll-payment-data-source";
import { HttpPayrollPaymentRecoveryDataSource } from "../data/datasources/http-payroll-payment-recovery-data-source";
import { HttpPayrollPayslipDataSource } from "../data/datasources/http-payroll-payslip-data-source";
import { HttpPayrollPeriodDataSource } from "../data/datasources/http-payroll-period-data-source";
import { HttpPayrollPolicyDataSource } from "../data/datasources/http-payroll-policy-data-source";
import { HttpPayrollReviewDataSource } from "../data/datasources/http-payroll-review-data-source";
import { HttpTaxOpeningDataSource } from "../data/datasources/http-tax-opening-data-source";
import { RemoteCompensationRepository } from "../data/repositories/remote-compensation-repository";
import { RemotePayrollCalculationRepository } from "../data/repositories/remote-payroll-calculation-repository";
import { RemotePayrollFinalizationRepository } from "../data/repositories/remote-payroll-finalization-repository";
import { RemotePayrollInputRepository } from "../data/repositories/remote-payroll-input-repository";
import { RemotePayrollPaymentRecoveryRepository } from "../data/repositories/remote-payroll-payment-recovery-repository";
import { RemotePayrollPaymentRepository } from "../data/repositories/remote-payroll-payment-repository";
import { RemotePayrollPayslipRepository } from "../data/repositories/remote-payroll-payslip-repository";
import { RemotePayrollPeriodRepository } from "../data/repositories/remote-payroll-period-repository";
import { RemotePayrollPolicyRepository } from "../data/repositories/remote-payroll-policy-repository";
import { RemotePayrollReviewRepository } from "../data/repositories/remote-payroll-review-repository";
import { RemoteTaxOpeningRepository } from "../data/repositories/remote-tax-opening-repository";
import { AbandonPayrollCalculation } from "../domain/usecases/abandon-payroll-calculation";
import { CancelPayrollPeriod } from "../domain/usecases/cancel-payroll-period";
import { ConfirmPayrollPaymentRecovery } from "../domain/usecases/confirm-payroll-payment-recovery";
import { CreatePayrollPeriod } from "../domain/usecases/create-payroll-period";
import { DecidePayrollReview } from "../domain/usecases/decide-payroll-review";
import { DownloadPayrollPayment } from "../domain/usecases/download-payroll-payment";
import { DownloadPayrollPayslip } from "../domain/usecases/download-payroll-payslip";
import { ListCompensationEmployees } from "../domain/usecases/list-compensation-employees";
import { ListPayrollInputEmployees } from "../domain/usecases/list-payroll-input-employees";
import { ListPayrollPayslips } from "../domain/usecases/list-payroll-payslips";
import { ListPayrollPeriodEmployees } from "../domain/usecases/list-payroll-period-employees";
import { ListPayrollPeriods } from "../domain/usecases/list-payroll-periods";
import { ListPayrollPublicationRuns } from "../domain/usecases/list-payroll-publication-runs";
import { ListTaxOpeningEmployees } from "../domain/usecases/list-tax-opening-employees";
import { LoadCompensationEditor } from "../domain/usecases/load-compensation-editor";
import { LoadCompensationReview } from "../domain/usecases/load-compensation-review";
import { LoadPayrollCalculations } from "../domain/usecases/load-payroll-calculations";
import { LoadPayrollFinalization } from "../domain/usecases/load-payroll-finalization";
import { LoadPayrollFinalizations } from "../domain/usecases/load-payroll-finalizations";
import { LoadPayrollInputReview } from "../domain/usecases/load-payroll-input-review";
import { LoadPayrollInputRevision } from "../domain/usecases/load-payroll-input-revision";
import { LoadPayrollPayables } from "../domain/usecases/load-payroll-payables";
import { LoadPayrollPayment } from "../domain/usecases/load-payroll-payment";
import { LoadPayrollPayments } from "../domain/usecases/load-payroll-payments";
import { LoadPayrollPayslip } from "../domain/usecases/load-payroll-payslip";
import { LoadPayrollPayslipPayments } from "../domain/usecases/load-payroll-payslip-payments";
import { LoadPayrollPeriod } from "../domain/usecases/load-payroll-period";
import { LoadPayrollPolicy } from "../domain/usecases/load-payroll-policy";
import { LoadPayrollPolicyEditor } from "../domain/usecases/load-payroll-policy-editor";
import { LoadPayrollReview } from "../domain/usecases/load-payroll-review";
import { LoadPayrollReviewAction } from "../domain/usecases/load-payroll-review-action";
import { LoadPayrollReviewSubmission } from "../domain/usecases/load-payroll-review-submission";
import { LoadPayrollReviews } from "../domain/usecases/load-payroll-reviews";
import { LoadPayrollRun } from "../domain/usecases/load-payroll-run";
import { LoadPayrollRunEmployee } from "../domain/usecases/load-payroll-run-employee";
import { LoadTaxOpeningReview } from "../domain/usecases/load-tax-opening-review";
import { LoadTaxOpeningRevision } from "../domain/usecases/load-tax-opening-revision";
import { PreparePayrollPayment } from "../domain/usecases/prepare-payroll-payment";
import { ReconcilePayrollPayment } from "../domain/usecases/reconcile-payroll-payment";
import { ResumePayrollCalculation } from "../domain/usecases/resume-payroll-calculation";
import { ReviewPayrollCalculation } from "../domain/usecases/review-payroll-calculation";
import { ReviewPayrollFinalization } from "../domain/usecases/review-payroll-finalization";
import { ReviewPayrollInputAction } from "../domain/usecases/review-payroll-input-action";
import { ReviewPayrollPaymentReconciliation } from "../domain/usecases/review-payroll-payment-reconciliation";
import { ReviewPayrollPaymentRecovery } from "../domain/usecases/review-payroll-payment-recovery";
import { ReviewPayrollPaymentTransition } from "../domain/usecases/review-payroll-payment-transition";
import { ReviewPayrollPeriodCancellation } from "../domain/usecases/review-payroll-period-cancellation";
import { ReviewPayrollPeriodCreation } from "../domain/usecases/review-payroll-period-creation";
import { ReviewPayrollRunRecovery } from "../domain/usecases/review-payroll-run-recovery";
import { ReviewTaxOpeningAction } from "../domain/usecases/review-tax-opening-action";
import { SaveCompensation } from "../domain/usecases/save-compensation";
import { SavePayrollInput } from "../domain/usecases/save-payroll-input";
import { SavePayrollPolicy } from "../domain/usecases/save-payroll-policy";
import { SaveTaxOpening } from "../domain/usecases/save-tax-opening";
import { StartPayrollCalculation } from "../domain/usecases/start-payroll-calculation";
import { StartPayrollFinalization } from "../domain/usecases/start-payroll-finalization";
import { SubmitPayrollReview } from "../domain/usecases/submit-payroll-review";
import { TransitionPayrollPayment } from "../domain/usecases/transition-payroll-payment";
import { VerifyPayrollInput } from "../domain/usecases/verify-payroll-input";
import { VerifyTaxOpening } from "../domain/usecases/verify-tax-opening";
import { WithdrawPayrollReview } from "../domain/usecases/withdraw-payroll-review";
import type { PayrollUseCases } from "../presentation/contracts/payroll-use-cases";

export function createPayrollFeature(
  http: HttpClient,
  files: Pick<FileRepository, "downloadBinary" | "downloadText">,
): PayrollUseCases {
  const payments = new RemotePayrollPaymentRepository(new HttpPayrollPaymentDataSource(http));
  const recoveries = new RemotePayrollPaymentRecoveryRepository(
    new HttpPayrollPaymentRecoveryDataSource(http),
  );
  const payslips = new RemotePayrollPayslipRepository(new HttpPayrollPayslipDataSource(http));
  const policies = new RemotePayrollPolicyRepository(new HttpPayrollPolicyDataSource(http));
  const compensations = new RemoteCompensationRepository(new HttpCompensationDataSource(http));
  const periods = new RemotePayrollPeriodRepository(new HttpPayrollPeriodDataSource(http));
  const inputs = new RemotePayrollInputRepository(new HttpPayrollInputDataSource(http));
  const openings = new RemoteTaxOpeningRepository(new HttpTaxOpeningDataSource(http));
  const calculations = new RemotePayrollCalculationRepository(
    new HttpPayrollCalculationDataSource(http),
  );
  const reviews = new RemotePayrollReviewRepository(new HttpPayrollReviewDataSource(http));
  const finalizations = new RemotePayrollFinalizationRepository(
    new HttpPayrollFinalizationDataSource(http),
  );
  return Object.freeze({
    reviewPaymentRecovery: new ReviewPayrollPaymentRecovery(recoveries),
    confirmPaymentRecovery: new ConfirmPayrollPaymentRecovery(recoveries),
    loadPayments: new LoadPayrollPayments(payments),
    loadPayment: new LoadPayrollPayment(payments),
    loadPayables: new LoadPayrollPayables(payments),
    preparePayment: new PreparePayrollPayment(payments),
    downloadPayment: new DownloadPayrollPayment(payments, files),
    reviewPaymentTransition: new ReviewPayrollPaymentTransition(payments),
    transitionPayment: new TransitionPayrollPayment(payments),
    reviewReconciliation: new ReviewPayrollPaymentReconciliation(payments),
    reconcilePayment: new ReconcilePayrollPayment(payments),

    listPayslips: new ListPayrollPayslips(payslips),
    loadPayslip: new LoadPayrollPayslip(payslips),
    loadPayslipPayments: new LoadPayrollPayslipPayments(payslips),
    downloadPayslip: new DownloadPayrollPayslip(payslips, files),
    listPublicationRuns: new ListPayrollPublicationRuns(finalizations),
    loadFinalizations: new LoadPayrollFinalizations(finalizations),
    loadFinalization: new LoadPayrollFinalization(finalizations),
    reviewFinalization: new ReviewPayrollFinalization(finalizations),
    startFinalization: new StartPayrollFinalization(finalizations),

    loadReviews: new LoadPayrollReviews(reviews),
    loadReview: new LoadPayrollReview(reviews),
    loadReviewSubmission: new LoadPayrollReviewSubmission(reviews),
    loadReviewAction: new LoadPayrollReviewAction(reviews),
    submitReview: new SubmitPayrollReview(reviews),
    decideReview: new DecidePayrollReview(reviews),
    withdrawReview: new WithdrawPayrollReview(reviews),

    loadCalculations: new LoadPayrollCalculations(calculations),
    reviewCalculation: new ReviewPayrollCalculation(calculations),
    loadRun: new LoadPayrollRun(calculations),
    loadRunEmployee: new LoadPayrollRunEmployee(calculations),
    reviewRunRecovery: new ReviewPayrollRunRecovery(calculations),
    startCalculation: new StartPayrollCalculation(calculations),
    resumeCalculation: new ResumePayrollCalculation(calculations),
    abandonCalculation: new AbandonPayrollCalculation(calculations),

    listTaxOpeningEmployees: new ListTaxOpeningEmployees(openings),
    loadTaxOpeningReview: new LoadTaxOpeningReview(openings),
    loadTaxOpeningRevision: new LoadTaxOpeningRevision(openings),
    reviewTaxOpeningAction: new ReviewTaxOpeningAction(openings),
    saveTaxOpening: new SaveTaxOpening(openings),
    verifyTaxOpening: new VerifyTaxOpening(openings),

    listPayrollInputEmployees: new ListPayrollInputEmployees(inputs),
    loadInputReview: new LoadPayrollInputReview(inputs),
    loadInputRevision: new LoadPayrollInputRevision(inputs),
    reviewInputAction: new ReviewPayrollInputAction(inputs),
    saveInput: new SavePayrollInput(inputs),
    verifyInput: new VerifyPayrollInput(inputs),

    listPeriods: new ListPayrollPeriods(periods),
    loadPeriod: new LoadPayrollPeriod(periods),
    listPeriodEmployees: new ListPayrollPeriodEmployees(periods),
    reviewPeriodCreation: new ReviewPayrollPeriodCreation(),
    createPeriod: new CreatePayrollPeriod(periods),
    reviewPeriodCancellation: new ReviewPayrollPeriodCancellation(periods),
    cancelPeriod: new CancelPayrollPeriod(periods),

    loadPolicy: new LoadPayrollPolicy(policies),
    loadPolicyEditor: new LoadPayrollPolicyEditor(policies),
    savePolicy: new SavePayrollPolicy(policies),
    listCompensationEmployees: new ListCompensationEmployees(compensations),
    loadCompensationReview: new LoadCompensationReview(compensations),
    loadCompensationEditor: new LoadCompensationEditor(compensations),
    saveCompensation: new SaveCompensation(compensations),
  });
}
