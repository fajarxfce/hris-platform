import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { AttendanceAdjustmentChange } from "../../domain/entities/attendance-adjustment-change";
import type { AttendanceAdjustmentRepository } from "../../domain/repositories/attendance-adjustment-repository";
import type { AttendanceAdjustmentDataSource } from "../datasources/attendance-adjustment-data-source";
import {
  toAttendanceAdjustmentCommand,
  toAttendanceAdjustmentDetails,
  toAttendanceAdjustmentPage,
  toAttendanceAdjustmentReview,
} from "../mappers/attendance-adjustment-mapper";
import { toAttendanceReceipt } from "../mappers/attendance-mapper";

export class RemoteAttendanceAdjustmentRepository implements AttendanceAdjustmentRepository {
  constructor(private readonly source: AttendanceAdjustmentDataSource) {}
  review(company: CompanyId, employee: EmployeeId, date: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toAttendanceAdjustmentReview(
        await this.source.review(company, employee, date, signal),
        company,
        employee,
        date,
      ),
    );
  }
  history(
    company: CompanyId,
    employee: EmployeeId,
    month: string,
    before: string | null,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toAttendanceAdjustmentPage(
        await this.source.history(company, employee, month, before, signal),
        month,
        before,
      ),
    );
  }
  details(
    company: CompanyId,
    employee: EmployeeId,
    month: string,
    id: string,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toAttendanceAdjustmentDetails(
        await this.source.details(company, employee, id, signal),
        company,
        employee,
        month,
        id,
      ),
    );
  }
  adjust(
    company: CompanyId,
    operation: OperationId,
    change: AttendanceAdjustmentChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toAttendanceReceipt(
        await this.source.adjust(
          company,
          change.employeeId,
          operation,
          toAttendanceAdjustmentCommand(change),
          signal,
        ),
        change.expectedAdjustmentRevision + 1,
      ),
    );
  }
}
