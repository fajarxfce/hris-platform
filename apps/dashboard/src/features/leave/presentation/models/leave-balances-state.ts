import type { Failure } from "../../../../core/domain/result";
import type { EmployeeLeaveBalances } from "../../domain/entities/employee-leave-balances";

export type LeaveBalancesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: EmployeeLeaveBalances | null;
  failure: Failure | null;
}>;
export const initialLeaveBalancesState: LeaveBalancesState = {
  stage: "loading",
  page: null,
  failure: null,
};
