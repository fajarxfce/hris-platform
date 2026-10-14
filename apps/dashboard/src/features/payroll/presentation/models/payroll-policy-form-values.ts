import type { PayrollPolicyEditorContext } from "../../domain/usecases/load-payroll-policy-editor";
import type { PayrollPolicyFields } from "../controllers/payroll-policy-editor-controller";

export type PayrollPolicyFormValues = Omit<PayrollPolicyFields, "reviewReferences"> & {
  references: string;
};
export function payrollPolicyFormValues(
  context: PayrollPolicyEditorContext | null,
  month: string,
): PayrollPolicyFormValues {
  const current = context?.current;
  return {
    effectiveFrom: current?.effectiveFrom ?? month,
    effectiveUntil: current?.effectiveUntil ?? `${month.slice(0, 4)}-12`,
    incomeTaxRuleId: current?.incomeTaxRuleId ?? context?.rules[0]?.id ?? "",
    insuranceRuleId: current?.insuranceRuleId ?? "ID-BPJS-PU-v1",
    minimumMonthlyWage: current?.minimumMonthlyWage ?? "",
    healthWageCap: current?.healthWageCap ?? "",
    pensionWageCap: current?.pensionWageCap ?? "",
    contributionRounding: current?.contributionRounding ?? "HALF_UP",
    references: current?.reviewReferences.join("\n") ?? "",
    reason: "",
  };
}
export function payrollPolicyFields(values: PayrollPolicyFormValues): PayrollPolicyFields {
  const { references, ...fields } = values;
  return {
    ...fields,
    reviewReferences: references
      .split(/\r?\n/u)
      .map((reference) => reference.trim())
      .filter(Boolean)
      .slice(0, 11),
  };
}
