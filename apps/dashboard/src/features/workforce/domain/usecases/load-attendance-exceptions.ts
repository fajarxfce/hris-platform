import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AttendanceExceptionPage } from "../entities/attendance-exception-page";
import type { AttendanceExceptionSearchInput } from "../entities/attendance-exception-search";
import { parseAttendanceExceptionSearch } from "../policies/attendance-exception-policy";
import { canReadAttendance } from "../policies/attendance-policy";
import type { AttendanceRepository } from "../repositories/attendance-repository";

export class LoadAttendanceExceptions {
  constructor(private readonly attendance: AttendanceRepository) {}
  async execute(
    access: CompanyAccess,
    input: AttendanceExceptionSearchInput,
    signal: AbortSignal,
  ): Promise<Result<AttendanceExceptionPage>> {
    signal.throwIfAborted();
    if (!canReadAttendance(access.permissions)) return failed("access_denied");
    const search = parseAttendanceExceptionSearch(input);
    if (!search.ok) return search;
    const result = await this.attendance.exceptions(access.companyId, search.value, signal);
    signal.throwIfAborted();
    return result;
  }
}
