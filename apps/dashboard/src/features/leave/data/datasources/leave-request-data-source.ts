import type { LeaveRequestDetailsDto } from "../models/leave-request-details-dto";
import type { LeaveRequestPageDto } from "../models/leave-request-summary-dto";

export interface LeaveRequestDataSource {
  list(
    company: string,
    employee: string | null,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ): Promise<LeaveRequestPageDto>;
  get(
    company: string,
    id: string,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<LeaveRequestDetailsDto>;
}
