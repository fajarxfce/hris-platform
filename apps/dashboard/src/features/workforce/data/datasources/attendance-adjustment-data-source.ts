import type {
  AttendanceAdjustmentCommandDto,
  AttendanceAdjustmentDetailsDto,
  AttendanceAdjustmentPageDto,
  AttendanceAdjustmentReviewDto,
} from "../models/attendance-adjustment-dto";
import type { AttendanceReceiptDto } from "../models/attendance-dto";

export interface AttendanceAdjustmentDataSource {
  review(
    company: string,
    employee: string,
    date: string,
    signal: AbortSignal,
  ): Promise<AttendanceAdjustmentReviewDto>;
  history(
    company: string,
    employee: string,
    month: string,
    before: string | null,
    signal: AbortSignal,
  ): Promise<AttendanceAdjustmentPageDto>;
  details(
    company: string,
    employee: string,
    id: string,
    signal: AbortSignal,
  ): Promise<AttendanceAdjustmentDetailsDto>;
  adjust(
    company: string,
    employee: string,
    operation: string,
    body: AttendanceAdjustmentCommandDto,
    signal: AbortSignal,
  ): Promise<AttendanceReceiptDto>;
}
