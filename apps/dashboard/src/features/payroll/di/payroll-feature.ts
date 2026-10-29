import type { HttpClient } from "../../../core/data/http/http-client";
import type { FileRepository } from "../../../core/domain/files/file-repository";
import { HttpCompensationDataSource } from "../data/datasources/http-compensation-data-source";
import { HttpPayrollAmendmentDataSource } from "../data/datasources/http-payroll-amendment-data-source";
import { HttpPayrollAmendmentPublicationDataSource } from "../data/datasources/http-payroll-amendment-publication-data-source";
import { HttpPayrollAmendmentReviewDataSource } from "../data/datasources/http-payroll-amendment-review-data-source";
import { HttpPayrollAmendmentSourceDataSource } from "../data/datasources/http-payroll-amendment-source-data-source";
import { HttpPayrollCalculationDataSource } from "../data/datasources/http-payroll-calculation-data-source";
import { HttpPayrollComparisonDataSource } from "../data/datasources/http-payroll-comparison-data-source";
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
import { RemotePayrollAmendmentPublicationRepository } from "../data/repositories/remote-payroll-amendment-publication-repository";
import { RemotePayrollAmendmentRepository } from "../data/repositories/remote-payroll-amendment-repository";
import { RemotePayrollAmendmentReviewRepository } from "../data/repositories/remote-payroll-amendment-review-repository";
import { RemotePayrollAmendmentSourceRepository } from "../data/repositories/remote-payroll-amendment-source-repository";
import { RemotePayrollCalculationRepository } from "../data/repositories/remote-payroll-calculation-repository";
import { RemotePayrollComparisonRepository } from "../data/repositories/remote-payroll-comparison-repository";
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
import { CalculatePayrollAmendment } from "../domain/usecases/calculate-payroll-amendment";
import { CancelPayrollAmendment } from "../domain/usecases/cancel-payroll-amendment";
import { CancelPayrollPeriod } from "../domain/usecases/cancel-payroll-period";
import { ConfirmPayrollPaymentRecovery } from "../domain/usecases/confirm-payroll-payment-recovery";
import { CreatePayrollPeriod } from "../domain/usecases/create-payroll-period";
import { DecidePayrollAmendmentReview } from "../domain/usecases/decide-payroll-amendment-review";
import { DecidePayrollReview } from "../domain/usecases/decide-payroll-review";
import { DownloadPayrollPayment } from "../domain/usecases/download-payroll-payment";
import { DownloadPayrollPayslip } from "../domain/usecases/download-payroll-payslip";
import { ListCompensationEmployees } from "../domain/usecases/list-compensation-employees";
import { ListPayrollAmendmentSources } from "../domain/usecases/list-payroll-amendment-sources";
import { ListPayrollAmendments } from "../domain/usecases/list-payroll-amendments";
import { ListPayrollInputEmployees } from "../domain/usecases/list-payroll-input-employees";
import { ListPayrollPayslips } from "../domain/usecases/list-payroll-payslips";
import { ListPayrollPeriodEmployees } from "../domain/usecases/list-payroll-period-employees";
import { ListPayrollPeriods } from "../domain/usecases/list-payroll-periods";
import { ListPayrollPublicationRuns } from "../domain/usecases/list-payroll-publication-runs";
import { ListTaxOpeningEmployees } from "../domain/usecases/list-tax-opening-employees";
import { LoadCompensationEditor } from "../domain/usecases/load-compensation-editor";
import { LoadCompensationReview } from "../domain/usecases/load-compensation-review";
import { LoadPayrollAmendment } from "../domain/usecases/load-payroll-amendment";
import { LoadPayrollAmendmentAction } from "../domain/usecases/load-payroll-amendment-action";
import { LoadPayrollAmendmentEditor } from "../domain/usecases/load-payroll-amendment-editor";
import { LoadPayrollAmendmentPublication } from "../domain/usecases/load-payroll-amendment-publication";
import { LoadPayrollAmendmentPublications } from "../domain/usecases/load-payroll-amendment-publications";
import { LoadPayrollAmendmentReview } from "../domain/usecases/load-payroll-amendment-review";
import { LoadPayrollAmendmentReviewAction } from "../domain/usecases/load-payroll-amendment-review-action";
import { LoadPayrollAmendmentReviewSubmission } from "../domain/usecases/load-payroll-amendment-review-submission";
import { LoadPayrollAmendmentReviews } from "../domain/usecases/load-payroll-amendment-reviews";
import { LoadPayrollCalculations } from "../domain/usecases/load-payroll-calculations";
import { LoadPayrollComparisonContext } from "../domain/usecases/load-payroll-comparison-context";
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
import { LoadPayrollRunComparison } from "../domain/usecases/load-payroll-run-comparison";
import { LoadPayrollRunEmployee } from "../domain/usecases/load-payroll-run-employee";
import { LoadTaxOpeningReview } from "../domain/usecases/load-tax-opening-review";
import { LoadTaxOpeningRevision } from "../domain/usecases/load-tax-opening-revision";
import { PreparePayrollPayment } from "../domain/usecases/prepare-payroll-payment";
import { PreviewPayrollAmendment } from "../domain/usecases/preview-payroll-amendment";
import { ReconcilePayrollPayment } from "../domain/usecases/reconcile-payroll-payment";
import { RecoverPayrollAmendmentPublication } from "../domain/usecases/recover-payroll-amendment-publication";
import { ResumePayrollCalculation } from "../domain/usecases/resume-payroll-calculation";
import { ReviewPayrollAmendmentPublication } from "../domain/usecases/review-payroll-amendment-publication";
import { ReviewPayrollAmendmentPublicationRecovery } from "../domain/usecases/review-payroll-amendment-publication-recovery";
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
import { SavePayrollAmendment } from "../domain/usecases/save-payroll-amendment";
import { SavePayrollInput } from "../domain/usecases/save-payroll-input";
import { SavePayrollPolicy } from "../domain/usecases/save-payroll-policy";
import { SaveTaxOpening } from "../domain/usecases/save-tax-opening";
import { StartPayrollAmendmentPublication } from "../domain/usecases/start-payroll-amendment-publication";
import { StartPayrollCalculation } from "../domain/usecases/start-payroll-calculation";
import { StartPayrollFinalization } from "../domain/usecases/start-payroll-finalization";
import { SubmitPayrollAmendmentReview } from "../domain/usecases/submit-payroll-amendment-review";
import { SubmitPayrollReview } from "../domain/usecases/submit-payroll-review";
import { TransitionPayrollPayment } from "../domain/usecases/transition-payroll-payment";
import { VerifyPayrollInput } from "../domain/usecases/verify-payroll-input";
import { VerifyTaxOpening } from "../domain/usecases/verify-tax-opening";
import { WithdrawPayrollAmendmentReview } from "../domain/usecases/withdraw-payroll-amendment-review";
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
  const amendments = new RemotePayrollAmendmentRepository(new HttpPayrollAmendmentDataSource(http));
  const amendmentReviews = new RemotePayrollAmendmentReviewRepository(
    new HttpPayrollAmendmentReviewDataSource(http),
  );
  const amendmentPublications = new RemotePayrollAmendmentPublicationRepository(
    new HttpPayrollAmendmentPublicationDataSource(http),
  );
  const amendmentSources = new RemotePayrollAmendmentSourceRepository(
    new HttpPayrollAmendmentSourceDataSource(http),
  );
  return Object.freeze({
    loadAmendmentReviews: new LoadPayrollAmendmentReviews(amendments, amendmentReviews),
    loadAmendmentPublications: new LoadPayrollAmendmentPublications(
      amendments,
      amendmentPublications,
    ),
    loadAmendmentPublication: new LoadPayrollAmendmentPublication(amendmentPublications),
    reviewAmendmentPublication: new ReviewPayrollAmendmentPublication(
      amendments,
      amendmentReviews,
      amendmentPublications,
    ),
    reviewAmendmentPublicationRecovery: new ReviewPayrollAmendmentPublicationRecovery(
      amendmentPublications,
    ),
    startAmendmentPublication: new StartPayrollAmendmentPublication(amendmentPublications),
    recoverAmendmentPublication: new RecoverPayrollAmendmentPublication(amendmentPublications),
    loadAmendmentReview: new LoadPayrollAmendmentReview(amendmentReviews),
    loadAmendmentReviewSubmission: new LoadPayrollAmendmentReviewSubmission(amendments),
    loadAmendmentReviewAction: new LoadPayrollAmendmentReviewAction(amendmentReviews),
    submitAmendmentReview: new SubmitPayrollAmendmentReview(amendmentReviews),
    decideAmendmentReview: new DecidePayrollAmendmentReview(amendmentReviews),
    withdrawAmendmentReview: new WithdrawPayrollAmendmentReview(amendmentReviews),
    listAmendmentSources: new ListPayrollAmendmentSources(amendmentSources),
    loadAmendmentEditor: new LoadPayrollAmendmentEditor(amendments, amendmentSources),
    previewAmendment: new PreviewPayrollAmendment(amendmentSources),
    saveAmendment: new SavePayrollAmendment(amendments),
    loadAmendmentAction: new LoadPayrollAmendmentAction(amendments),
    calculateAmendment: new CalculatePayrollAmendment(amendments),
    cancelAmendment: new CancelPayrollAmendment(amendments),
    listAmendments: new ListPayrollAmendments(amendments),
    loadAmendment: new LoadPayrollAmendment(amendments),
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
    loadComparisonContext: new LoadPayrollComparisonContext(calculations, periods),
    loadComparison: new LoadPayrollRunComparison(
      new RemotePayrollComparisonRepository(new HttpPayrollComparisonDataSource(http)),
    ),
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
