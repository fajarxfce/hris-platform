import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ShiftChange } from "../entities/shift-change";
import { normalizeShiftChange } from "../policies/shift-change-policy";
import { canManageShifts } from "../policies/shift-policy";
import type { ShiftRepository } from "../repositories/shift-repository";

export class SaveShift {
  constructor(private readonly shifts: ShiftRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: ShiftChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!canManageShifts(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_shift_change"));
    const change = normalizeShiftChange(input);
    if (!change.ok) return Promise.resolve(change);
    return this.shifts.save(access.companyId, operation, change.value, signal);
  }
}
