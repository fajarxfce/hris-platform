import type { LeaveBalance } from "./leave-balance";

export type LeaveBalanceSummary = Readonly<{
  typeId: string;
  typeCode: string;
  typeName: string;
  balance: LeaveBalance;
}>;
