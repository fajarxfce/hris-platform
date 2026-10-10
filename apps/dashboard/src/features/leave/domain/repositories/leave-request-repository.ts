import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type {
  LeaveRequestId,
  LeaveRequestPage,
  LeaveRequestQuery,
} from "../entities/leave-request";
import type { LeaveRequestChange, LeaveRequestDecision } from "../entities/leave-request-action";
import type { LeaveRequestDetails } from "../entities/leave-request-details";

export interface LeaveRequestRepository {
  decide(
    company: CompanyId,
    operation: OperationId,
    command: LeaveRequestDecision,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  withdraw(
    company: CompanyId,
    operation: OperationId,
    command: LeaveRequestChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  requestCancellation(
    company: CompanyId,
    operation: OperationId,
    command: LeaveRequestChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
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
