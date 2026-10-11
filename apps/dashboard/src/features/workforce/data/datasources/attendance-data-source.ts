import type {
  AttendanceCorrectionChangeDto,
  AttendanceDayDto,
  AttendanceReceiptDto,
  AttendanceReviewDto,
} from "../models/attendance-dto";
import type { CalendarRangeDto } from "../models/calendar-dto";

export interface AttendanceDataSource {
  days(
    company: string,
    employee: string,
    range: CalendarRangeDto,
    signal: AbortSignal,
  ): Promise<readonly AttendanceDayDto[]>;
  review(
    company: string,
    id: string,
    operation: string,
    body: AttendanceReviewDto,
    signal: AbortSignal,
  ): Promise<AttendanceReceiptDto>;
  correct(
    company: string,
    employee: string,
    operation: string,
    body: AttendanceCorrectionChangeDto,
    signal: AbortSignal,
  ): Promise<AttendanceReceiptDto>;
}
