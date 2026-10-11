import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ShiftDefinition, ShiftId } from "../entities/shift-definition";
import { canReadShifts } from "../policies/shift-policy";
import type { ShiftRepository } from "../repositories/shift-repository";

export class LoadShift {
  constructor(private readonly shifts: ShiftRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<ShiftDefinition>> {
    signal.throwIfAborted();
    if (!canReadShifts(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("shift_not_found"));
    return this.shifts.get(access.companyId, id.toLowerCase() as ShiftId, signal);
  }
}
