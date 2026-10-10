import type { LeavePolicyReviewDto, LeaveTypePageDto } from "../models/leave-type-dto";

export interface LeavePolicyDataSource {
  list(
    company: string,
    active: boolean | null,
    after: string | null,
    signal: AbortSignal,
  ): Promise<LeaveTypePageDto>;
  get(
    company: string,
    id: string,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<LeavePolicyReviewDto>;
}
