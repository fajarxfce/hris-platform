import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpCompensationDataSource } from "../data/datasources/http-compensation-data-source";
import { HttpPayrollInputDataSource } from "../data/datasources/http-payroll-input-data-source";
import { HttpPayrollPeriodDataSource } from "../data/datasources/http-payroll-period-data-source";
import { HttpPayrollPolicyDataSource } from "../data/datasources/http-payroll-policy-data-source";
import { RemoteCompensationRepository } from "../data/repositories/remote-compensation-repository";
import { RemotePayrollInputRepository } from "../data/repositories/remote-payroll-input-repository";
import { RemotePayrollPeriodRepository } from "../data/repositories/remote-payroll-period-repository";
import { RemotePayrollPolicyRepository } from "../data/repositories/remote-payroll-policy-repository";
import { CancelPayrollPeriod } from "../domain/usecases/cancel-payroll-period";
import { CreatePayrollPeriod } from "../domain/usecases/create-payroll-period";
import { ListCompensationEmployees } from "../domain/usecases/list-compensation-employees";
import { ListPayrollInputEmployees } from "../domain/usecases/list-payroll-input-employees";
import { ListPayrollPeriodEmployees } from "../domain/usecases/list-payroll-period-employees";
import { ListPayrollPeriods } from "../domain/usecases/list-payroll-periods";
import { LoadCompensationEditor } from "../domain/usecases/load-compensation-editor";
import { LoadCompensationReview } from "../domain/usecases/load-compensation-review";
import { LoadPayrollInputReview } from "../domain/usecases/load-payroll-input-review";
import { LoadPayrollInputRevision } from "../domain/usecases/load-payroll-input-revision";
import { LoadPayrollPeriod } from "../domain/usecases/load-payroll-period";
import { LoadPayrollPolicy } from "../domain/usecases/load-payroll-policy";
import { LoadPayrollPolicyEditor } from "../domain/usecases/load-payroll-policy-editor";
import { ReviewPayrollInputAction } from "../domain/usecases/review-payroll-input-action";
import { ReviewPayrollPeriodCancellation } from "../domain/usecases/review-payroll-period-cancellation";
import { ReviewPayrollPeriodCreation } from "../domain/usecases/review-payroll-period-creation";
import { SaveCompensation } from "../domain/usecases/save-compensation";
import { SavePayrollInput } from "../domain/usecases/save-payroll-input";
import { SavePayrollPolicy } from "../domain/usecases/save-payroll-policy";
import { VerifyPayrollInput } from "../domain/usecases/verify-payroll-input";
import type { PayrollUseCases } from "../presentation/contracts/payroll-use-cases";

export function createPayrollFeature(http: HttpClient): PayrollUseCases {
  const policies = new RemotePayrollPolicyRepository(new HttpPayrollPolicyDataSource(http));
  const compensations = new RemoteCompensationRepository(new HttpCompensationDataSource(http));
  const periods = new RemotePayrollPeriodRepository(new HttpPayrollPeriodDataSource(http));
  const inputs = new RemotePayrollInputRepository(new HttpPayrollInputDataSource(http));
  return Object.freeze({
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
