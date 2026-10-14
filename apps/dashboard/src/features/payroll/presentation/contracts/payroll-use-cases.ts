import type { CancelPayrollPeriod } from "../../domain/usecases/cancel-payroll-period";
import type { CreatePayrollPeriod } from "../../domain/usecases/create-payroll-period";
import type { ListCompensationEmployees } from "../../domain/usecases/list-compensation-employees";
import type { ListPayrollInputEmployees } from "../../domain/usecases/list-payroll-input-employees";
import type { ListPayrollPeriodEmployees } from "../../domain/usecases/list-payroll-period-employees";
import type { ListPayrollPeriods } from "../../domain/usecases/list-payroll-periods";
import type { LoadCompensationEditor } from "../../domain/usecases/load-compensation-editor";
import type { LoadCompensationReview } from "../../domain/usecases/load-compensation-review";
import type { LoadPayrollInputReview } from "../../domain/usecases/load-payroll-input-review";
import type { LoadPayrollInputRevision } from "../../domain/usecases/load-payroll-input-revision";
import type { LoadPayrollPeriod } from "../../domain/usecases/load-payroll-period";
import type { LoadPayrollPolicy } from "../../domain/usecases/load-payroll-policy";
import type { LoadPayrollPolicyEditor } from "../../domain/usecases/load-payroll-policy-editor";
import type { ReviewPayrollInputAction } from "../../domain/usecases/review-payroll-input-action";
import type { ReviewPayrollPeriodCancellation } from "../../domain/usecases/review-payroll-period-cancellation";
import type { ReviewPayrollPeriodCreation } from "../../domain/usecases/review-payroll-period-creation";
import type { SaveCompensation } from "../../domain/usecases/save-compensation";
import type { SavePayrollInput } from "../../domain/usecases/save-payroll-input";
import type { SavePayrollPolicy } from "../../domain/usecases/save-payroll-policy";
import type { VerifyPayrollInput } from "../../domain/usecases/verify-payroll-input";

export type PayrollUseCases = Readonly<{
  listPayrollInputEmployees: Pick<ListPayrollInputEmployees, "execute">;
  loadInputReview: Pick<LoadPayrollInputReview, "execute">;
  loadInputRevision: Pick<LoadPayrollInputRevision, "execute">;
  reviewInputAction: Pick<ReviewPayrollInputAction, "execute">;
  saveInput: Pick<SavePayrollInput, "execute">;
  verifyInput: Pick<VerifyPayrollInput, "execute">;

  listPeriods: Pick<ListPayrollPeriods, "execute">;
  loadPeriod: Pick<LoadPayrollPeriod, "execute">;
  listPeriodEmployees: Pick<ListPayrollPeriodEmployees, "execute">;
  reviewPeriodCreation: Pick<ReviewPayrollPeriodCreation, "execute">;
  createPeriod: Pick<CreatePayrollPeriod, "execute">;
  reviewPeriodCancellation: Pick<ReviewPayrollPeriodCancellation, "execute">;
  cancelPeriod: Pick<CancelPayrollPeriod, "execute">;

  loadPolicy: Pick<LoadPayrollPolicy, "execute">;
  loadPolicyEditor: Pick<LoadPayrollPolicyEditor, "execute">;
  savePolicy: Pick<SavePayrollPolicy, "execute">;
  listCompensationEmployees: Pick<ListCompensationEmployees, "execute">;
  loadCompensationReview: Pick<LoadCompensationReview, "execute">;
  loadCompensationEditor: Pick<LoadCompensationEditor, "execute">;
  saveCompensation: Pick<SaveCompensation, "execute">;
}>;
