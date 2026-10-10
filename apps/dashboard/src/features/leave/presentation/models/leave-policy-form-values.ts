import type { LeavePolicyDefinition } from "../../domain/entities/leave-policy-definition";
import type { LeavePolicyFields } from "../controllers/leave-policy-editor-controller";

export type LeavePolicyFormValues = {
  code: string;
  name: string;
  effectiveFrom: string;
  paid: boolean;
  allowPartialDays: boolean;
  minServiceMonths: string;
  maxRequestDays: string;
  permanent: boolean;
  fixedTerm: boolean;
  active: boolean;
  attachmentRequired: boolean;
  reason: string;
  frequency: "NONE" | "MANUAL" | "MONTHLY" | "ANNUAL";
  daysPerPeriod: string;
  carryLimitDays: string;
};
export function leavePolicyFormValues(
  policy: LeavePolicyDefinition | null,
  today: string,
): LeavePolicyFormValues {
  return {
    code: policy?.code ?? "",
    name: policy?.name ?? "",
    effectiveFrom: policy?.effectiveFrom ?? today,
    paid: policy?.paid ?? true,
    allowPartialDays: policy?.allowPartialDays ?? true,
    minServiceMonths: String(policy?.minServiceMonths ?? 0),
    maxRequestDays: String(policy?.maxRequestDays ?? 30),
    permanent: policy?.allowedContracts.includes("PERMANENT") ?? true,
    fixedTerm: policy?.allowedContracts.includes("FIXED_TERM") ?? true,
    active: policy?.active ?? true,
    attachmentRequired: policy?.attachmentRequired ?? false,
    reason: "",
    frequency: policy?.accrual?.frequency ?? "NONE",
    daysPerPeriod: policy?.accrual?.daysPerPeriod ?? "",
    carryLimitDays: policy?.accrual?.carryLimitDays ?? "0",
  };
}
export function leavePolicyFields(values: LeavePolicyFormValues): LeavePolicyFields {
  return {
    code: values.code,
    name: values.name,
    effectiveFrom: values.effectiveFrom,
    paid: values.paid,
    allowPartialDays: values.allowPartialDays,
    minServiceMonths: /^\d{1,3}$/u.test(values.minServiceMonths)
      ? Number(values.minServiceMonths)
      : Number.NaN,
    maxRequestDays: /^\d{1,3}$/u.test(values.maxRequestDays)
      ? Number(values.maxRequestDays)
      : Number.NaN,
    allowedContracts: [
      ...(values.permanent ? ["PERMANENT" as const] : []),
      ...(values.fixedTerm ? ["FIXED_TERM" as const] : []),
    ],
    active: values.active,
    attachmentRequired: values.attachmentRequired,
    reason: values.reason,
    accrual:
      values.frequency === "NONE"
        ? null
        : {
            frequency: values.frequency,
            daysPerPeriod: values.frequency === "MANUAL" ? "0" : values.daysPerPeriod,
            carryLimitDays: values.carryLimitDays,
          },
  };
}
