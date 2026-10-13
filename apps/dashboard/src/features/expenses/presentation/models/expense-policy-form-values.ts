import type { ExpensePolicyDefinition } from "../../domain/entities/expense-policy-definition";
import type { ExpensePolicyFields } from "../controllers/expense-policy-editor-controller";

export type ExpensePolicyFormValues = {
  code: string;
  name: string;
  effectiveFrom: string;
  maximumLineAmount: string;
  maximumClaimAmount: string;
  maximumAgeDays: string;
  receiptRequired: boolean;
  costCenterRequired: boolean;
  permanent: boolean;
  fixedTerm: boolean;
  active: boolean;
  reason: string;
};
export function expensePolicyFormValues(
  policy: ExpensePolicyDefinition | null,
  today: string,
): ExpensePolicyFormValues {
  return {
    code: policy?.code ?? "",
    name: policy?.name ?? "",
    effectiveFrom: policy?.effectiveFrom ?? today,
    maximumLineAmount: policy?.maximumLineAmount ?? "",
    maximumClaimAmount: policy?.maximumClaimAmount ?? "",
    maximumAgeDays: String(policy?.maximumAgeDays ?? 30),
    receiptRequired: policy?.receiptRequired ?? true,
    costCenterRequired: policy?.costCenterRequired ?? false,
    permanent: policy?.allowedContracts.includes("PERMANENT") ?? true,
    fixedTerm: policy?.allowedContracts.includes("FIXED_TERM") ?? true,
    active: policy?.active ?? true,
    reason: "",
  };
}
export function expensePolicyFields(values: ExpensePolicyFormValues): ExpensePolicyFields {
  return {
    code: values.code,
    name: values.name,
    effectiveFrom: values.effectiveFrom,
    maximumLineAmount: values.maximumLineAmount,
    maximumClaimAmount: values.maximumClaimAmount,
    maximumAgeDays: /^\d{1,3}$/u.test(values.maximumAgeDays)
      ? Number(values.maximumAgeDays)
      : Number.NaN,
    receiptRequired: values.receiptRequired,
    costCenterRequired: values.costCenterRequired,
    allowedContracts: [
      ...(values.permanent ? ["PERMANENT" as const] : []),
      ...(values.fixedTerm ? ["FIXED_TERM" as const] : []),
    ],
    active: values.active,
    reason: values.reason,
  };
}
