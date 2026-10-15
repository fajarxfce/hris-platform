import type {
  LeaveAccrualBatchDto,
  LeaveBatchDetailsDto,
  LeaveBatchPageDto,
  LeaveBatchResumeDto,
  LeaveBatchReviewDto,
  LeaveBatchReviewQueryDto,
  LeaveYearCloseBatchDto,
} from "../models/leave-batch-dto";
import type { LeaveReceiptDto } from "../models/leave-receipt-dto";
import type { LeaveTypePageDto } from "../models/leave-type-dto";
export interface LeaveBatchDataSource {
  types(
    company: string,
    kind: "ACCRUAL" | "YEAR_CLOSE",
    after: string | null,
    signal: AbortSignal,
  ): Promise<LeaveTypePageDto>;
  review(
    company: string,
    query: LeaveBatchReviewQueryDto,
    signal: AbortSignal,
  ): Promise<LeaveBatchReviewDto>;
  list(company: string, after: string | null, signal: AbortSignal): Promise<LeaveBatchPageDto>;
  get(
    company: string,
    id: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<LeaveBatchDetailsDto>;
  accrue(
    company: string,
    operation: string,
    input: LeaveAccrualBatchDto,
    signal: AbortSignal,
  ): Promise<LeaveReceiptDto>;
  closeYear(
    company: string,
    operation: string,
    input: LeaveYearCloseBatchDto,
    signal: AbortSignal,
  ): Promise<LeaveReceiptDto>;
  resume(
    company: string,
    id: string,
    operation: string,
    input: LeaveBatchResumeDto,
    signal: AbortSignal,
  ): Promise<LeaveReceiptDto>;
}
