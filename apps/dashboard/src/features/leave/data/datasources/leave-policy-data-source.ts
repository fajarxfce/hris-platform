import type { LeavePolicyChangeDto } from "../models/leave-policy-change-dto";
import type { LeaveReceiptDto } from "../models/leave-receipt-dto";
import type { LeavePolicyReviewDto, LeaveTypePageDto } from "../models/leave-type-dto";

export interface LeavePolicyDataSource {
  save(
    company: string,
    id: string,
    operation: string,
    change: LeavePolicyChangeDto,
    signal: AbortSignal,
  ): Promise<LeaveReceiptDto>;
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
