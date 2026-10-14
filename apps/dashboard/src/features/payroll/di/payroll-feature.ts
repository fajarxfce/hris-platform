import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpPayrollPolicyDataSource } from "../data/datasources/http-payroll-policy-data-source";
import { RemotePayrollPolicyRepository } from "../data/repositories/remote-payroll-policy-repository";
import { LoadPayrollPolicy } from "../domain/usecases/load-payroll-policy";
import { LoadPayrollPolicyEditor } from "../domain/usecases/load-payroll-policy-editor";
import { SavePayrollPolicy } from "../domain/usecases/save-payroll-policy";
import type { PayrollUseCases } from "../presentation/contracts/payroll-use-cases";

export function createPayrollFeature(http: HttpClient): PayrollUseCases {
  const policies = new RemotePayrollPolicyRepository(new HttpPayrollPolicyDataSource(http));
  return Object.freeze({
    loadPolicy: new LoadPayrollPolicy(policies),
    loadPolicyEditor: new LoadPayrollPolicyEditor(policies),
    savePolicy: new SavePayrollPolicy(policies),
  });
}
