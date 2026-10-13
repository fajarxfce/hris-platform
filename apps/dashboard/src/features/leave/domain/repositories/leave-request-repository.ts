import type { BinaryFileContent } from "../../../../core/domain/files/binary-file";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { LeaveAttachment } from "../entities/leave-attachment";
import type {
  LeaveRequestId,
  LeaveRequestPage,
  LeaveRequestQuery,
} from "../entities/leave-request";
import type { LeaveRequestChange, LeaveRequestDecision } from "../entities/leave-request-action";
import type { LeaveRequestDetails } from "../entities/leave-request-details";
import type { LeaveRequestSubmission } from "../entities/leave-request-submission";

export interface LeaveRequestRepository {
  submit(
    company: CompanyId,
    operation: OperationId,
    command: LeaveRequestSubmission,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  downloadAttachment(
    company: CompanyId,
    request: LeaveRequestId,
    attachment: LeaveAttachment,
    signal: AbortSignal,
  ): Promise<Result<BinaryFileContent>>;
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
