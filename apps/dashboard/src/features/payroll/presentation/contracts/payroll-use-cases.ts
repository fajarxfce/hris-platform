import type { LoadPayrollPolicy } from "../../domain/usecases/load-payroll-policy";
import type { LoadPayrollPolicyEditor } from "../../domain/usecases/load-payroll-policy-editor";
import type { SavePayrollPolicy } from "../../domain/usecases/save-payroll-policy";

export type PayrollUseCases = Readonly<{
  loadPolicy: Pick<LoadPayrollPolicy, "execute">;
  loadPolicyEditor: Pick<LoadPayrollPolicyEditor, "execute">;
  savePolicy: Pick<SavePayrollPolicy, "execute">;
}>;
