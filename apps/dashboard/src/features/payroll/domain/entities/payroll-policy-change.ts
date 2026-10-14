import type { PayrollPolicy } from "./payroll-policy";

export type PayrollPolicyChange = Readonly<
  Omit<PayrollPolicy, "companyId" | "version" | "appliedRevision"> & {
    expectedVersion: number | null;
    reason: string;
  }
>;
