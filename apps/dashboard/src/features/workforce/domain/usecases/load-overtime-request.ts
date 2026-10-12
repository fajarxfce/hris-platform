import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OvertimeRequestId } from "../entities/overtime-request";
import { isOvertimeHistoryCursor } from "../policies/overtime-read-policy";
import type { OvertimeRepository } from "../repositories/overtime-repository";

export class LoadOvertimeRequest {
  constructor(private readonly overtime: OvertimeRepository) {}
  async execute(
    access: CompanyAccess,
    id: string,
    historyAfter: string | null,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!isUuid(id)) return failed("overtime_request_not_found");
    if (historyAfter !== null && !isOvertimeHistoryCursor(historyAfter))
      return failed("invalid_page");
    // Assignment/delegation can authorize this record without granting a company directory.
    const result = await this.overtime.get(
      access.companyId,
      id.toLowerCase() as OvertimeRequestId,
      historyAfter,
      signal,
    );
    signal.throwIfAborted();
    return result;
  }
}
