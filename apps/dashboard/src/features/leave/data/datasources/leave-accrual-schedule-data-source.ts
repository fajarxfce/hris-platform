import type {
  LeaveAccrualScheduleCreationDto,
  LeaveAccrualScheduleDetailsDto,
  LeaveAccrualScheduleQueryDto,
  LeaveAccrualScheduleReviewDto,
  LeaveAccrualSchedulesDto,
} from "../models/leave-accrual-schedule-dto";
import type { LeaveReceiptDto } from "../models/leave-receipt-dto";
export interface LeaveAccrualScheduleDataSource {
  review(
    company: string,
    selection: LeaveAccrualScheduleQueryDto,
    signal: AbortSignal,
  ): Promise<LeaveAccrualScheduleReviewDto>;
  list(
    company: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<LeaveAccrualSchedulesDto>;
  get(company: string, id: string, signal: AbortSignal): Promise<LeaveAccrualScheduleDetailsDto>;
  create(
    company: string,
    operation: string,
    input: LeaveAccrualScheduleCreationDto,
    signal: AbortSignal,
  ): Promise<LeaveReceiptDto>;
}
