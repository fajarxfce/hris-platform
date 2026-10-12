import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { AttendanceCorrectionChange } from "../../domain/entities/attendance-correction-change";
import type { AttendanceReviewChange } from "../../domain/entities/attendance-review-change";
import type { CalendarRange } from "../../domain/entities/calendar-range";
import type { AttendanceRepository } from "../../domain/repositories/attendance-repository";
import type { AttendanceDataSource } from "../datasources/attendance-data-source";
import { toAttendanceCorrectionPage } from "../mappers/attendance-correction-page-mapper";
import { toAttendanceReceipt, toEmployeeAttendance } from "../mappers/attendance-mapper";

export class RemoteAttendanceRepository implements AttendanceRepository {
  constructor(private readonly source: AttendanceDataSource) {}
  corrections(
    company: CompanyId,
    employee: EmployeeId,
    workDate: string,
    after: string | null,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toAttendanceCorrectionPage(
        await this.source.corrections(company, employee, workDate, after, signal),
        company,
        employee,
        workDate,
        after,
      ),
    );
  }
  days(company: CompanyId, employee: EmployeeId, range: CalendarRange, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toEmployeeAttendance(
        await this.source.days(company, employee, range, signal),
        company,
        employee,
        range,
      ),
    );
  }
  review(
    company: CompanyId,
    operation: OperationId,
    change: AttendanceReviewChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toAttendanceReceipt(
        await this.source.review(
          company,
          change.entryId,
          operation,
          {
            version: change.version,
            decision: change.decision,
            reason: change.reason,
          },
          signal,
        ),
        change.version + 1,
        change.entryId,
      ),
    );
  }
  correct(
    company: CompanyId,
    operation: OperationId,
    change: AttendanceCorrectionChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toAttendanceReceipt(
        await this.source.correct(
          company,
          change.employeeId,
          operation,
          {
            workDate: change.workDate,
            clockIn: change.clockIn,
            clockOut: change.clockOut,
            breakMinutes: change.breakMinutes,
            expectedVersion: change.expectedVersion,
            reason: change.reason,
          },
          signal,
        ),
        change.expectedVersion === null ? 0 : change.expectedVersion + 1,
      ),
    );
  }
}
