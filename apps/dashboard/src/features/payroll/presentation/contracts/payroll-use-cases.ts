import type { ListCompensationEmployees } from "../../domain/usecases/list-compensation-employees";
import type { LoadCompensationEditor } from "../../domain/usecases/load-compensation-editor";
import type { LoadCompensationReview } from "../../domain/usecases/load-compensation-review";
import type { LoadPayrollPolicy } from "../../domain/usecases/load-payroll-policy";
import type { LoadPayrollPolicyEditor } from "../../domain/usecases/load-payroll-policy-editor";
import type { SaveCompensation } from "../../domain/usecases/save-compensation";
import type { SavePayrollPolicy } from "../../domain/usecases/save-payroll-policy";

export type PayrollUseCases = Readonly<{
  loadPolicy: Pick<LoadPayrollPolicy, "execute">;
  loadPolicyEditor: Pick<LoadPayrollPolicyEditor, "execute">;
  savePolicy: Pick<SavePayrollPolicy, "execute">;
  listCompensationEmployees: Pick<ListCompensationEmployees, "execute">;
  loadCompensationReview: Pick<LoadCompensationReview, "execute">;
  loadCompensationEditor: Pick<LoadCompensationEditor, "execute">;
  saveCompensation: Pick<SaveCompensation, "execute">;
}>;
