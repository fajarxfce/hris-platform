import type { OvertimeDetailsDto } from "../models/overtime-details-dto";
import type { OvertimePageDto, OvertimeSearchDto } from "../models/overtime-request-dto";

export interface OvertimeDataSource {
  list(company: string, search: OvertimeSearchDto, signal: AbortSignal): Promise<OvertimePageDto>;
  get(
    company: string,
    id: string,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<OvertimeDetailsDto>;
}
