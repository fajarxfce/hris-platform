import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AttendanceCorrectionChange } from "../entities/attendance-correction-change";
import { canCorrectAttendance, normalizeAttendanceCorrection } from "../policies/attendance-policy";
import type { AttendanceRepository } from "../repositories/attendance-repository";

export class CorrectAttendance {
  constructor(private readonly attendance: AttendanceRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: AttendanceCorrectionChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!canCorrectAttendance(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_operation_id"));
    const change = normalizeAttendanceCorrection(input);
    return change.ok
      ? this.attendance.correct(access.companyId, operation, change.value, signal)
      : Promise.resolve(change);
  }
}
