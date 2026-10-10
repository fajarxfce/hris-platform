import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveBalanceSummary } from "./leave-balance-summary";
import type { LeaveEmployeeReference } from "./leave-employee-reference";

export type EmployeeLeaveBalances = Readonly<{
  companyId: CompanyId;
  employee: LeaveEmployeeReference;
  year: number;
  items: readonly LeaveBalanceSummary[];
  nextCursor: string | null;
}>;
