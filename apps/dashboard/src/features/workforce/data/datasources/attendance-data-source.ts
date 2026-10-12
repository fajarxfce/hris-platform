import type {
  AttendanceCorrectionChangeDto,
  AttendanceDayDto,
  AttendanceReceiptDto,
  AttendanceReviewDto,
} from "../models/attendance-dto";
import type { CalendarRangeDto } from "../models/calendar-dto";

export interface AttendanceDataSource {
  corrections(
    company: string,
    employee: string,
    workDate: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<AttendanceCorrectionPageDto>;
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

import type { AttendanceCorrectionPageDto } from "../models/attendance-correction-page-dto";
