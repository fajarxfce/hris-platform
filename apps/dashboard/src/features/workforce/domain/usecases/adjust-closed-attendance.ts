import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AttendanceAdjustmentChange } from "../entities/attendance-adjustment-change";
import {
  canAdjustClosedAttendance,
  normalizeAttendanceAdjustment,
} from "../policies/attendance-adjustment-policy";
import type { AttendanceAdjustmentRepository } from "../repositories/attendance-adjustment-repository";

export class AdjustClosedAttendance {
  constructor(private readonly adjustments: AttendanceAdjustmentRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: AttendanceAdjustmentChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canAdjustClosedAttendance(access.permissions) || input.companyId !== access.companyId)
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_operation_id"));
    const change = normalizeAttendanceAdjustment(input);
    return change.ok
      ? this.adjustments.adjust(access.companyId, operation, change.value, signal)
      : Promise.resolve(change);
  }
}
