import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { AttendanceCorrectionChange } from "../entities/attendance-correction-change";
import type { AttendanceReviewChange } from "../entities/attendance-review-change";
import type { CalendarRange } from "../entities/calendar-range";
import type { EmployeeAttendance } from "../entities/employee-attendance";

export interface AttendanceRepository {
  days(
    company: CompanyId,
    employee: EmployeeId,
    range: CalendarRange,
    signal: AbortSignal,
  ): Promise<Result<EmployeeAttendance>>;
  review(
    company: CompanyId,
    operation: OperationId,
    change: AttendanceReviewChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  correct(
    company: CompanyId,
    operation: OperationId,
    change: AttendanceCorrectionChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
