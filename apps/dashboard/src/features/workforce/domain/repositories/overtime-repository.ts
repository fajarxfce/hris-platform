import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { OvertimePage } from "../entities/overtime-page";
import type { OvertimeRequestId } from "../entities/overtime-request";
import type { OvertimeRequestDetails } from "../entities/overtime-request-details";
import type { OvertimeSearch } from "../entities/overtime-search";

export interface OvertimeRepository {
  list(
    company: CompanyId,
    search: OvertimeSearch,
    signal: AbortSignal,
  ): Promise<Result<OvertimePage>>;
  get(
    company: CompanyId,
    id: OvertimeRequestId,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<Result<OvertimeRequestDetails>>;
}
