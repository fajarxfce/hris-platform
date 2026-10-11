import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ShiftPage } from "../entities/shift-page";
import type { ShiftSearch } from "../entities/shift-search";
import { canReadShifts, parseShiftSearch } from "../policies/shift-policy";
import type { ShiftRepository } from "../repositories/shift-repository";

export class LoadShifts {
  constructor(private readonly shifts: ShiftRepository) {}
  execute(
    access: CompanyAccess,
    input: ShiftSearch,
    signal: AbortSignal,
  ): Promise<Result<ShiftPage>> {
    signal.throwIfAborted();
    if (!canReadShifts(access.permissions)) return Promise.resolve(failed("access_denied"));
    const search = parseShiftSearch(input);
    if (!search.ok) return Promise.resolve(search);
    return this.shifts.list(access.companyId, search.value, signal);
  }
}
