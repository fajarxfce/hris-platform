import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AttendanceReviewChange } from "../entities/attendance-review-change";
import { canReviewAttendance, normalizeAttendanceReview } from "../policies/attendance-policy";
import type { AttendanceRepository } from "../repositories/attendance-repository";

export class ReviewAttendance {
  constructor(private readonly attendance: AttendanceRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: AttendanceReviewChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!canReviewAttendance(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_operation_id"));
    const change = normalizeAttendanceReview(input);
    return change.ok
      ? this.attendance.review(access.companyId, operation, change.value, signal)
      : Promise.resolve(change);
  }
}
