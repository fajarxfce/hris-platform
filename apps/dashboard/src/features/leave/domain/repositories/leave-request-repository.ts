import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type {
  LeaveRequestId,
  LeaveRequestPage,
  LeaveRequestQuery,
} from "../entities/leave-request";
import type { LeaveRequestDetails } from "../entities/leave-request-details";

export interface LeaveRequestRepository {
  list(
    company: CompanyId,
    query: LeaveRequestQuery,
    signal: AbortSignal,
  ): Promise<Result<LeaveRequestPage>>;
  get(
    company: CompanyId,
    id: LeaveRequestId,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<Result<LeaveRequestDetails>>;
}
