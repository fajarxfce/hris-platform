import type { BinaryResponseDto } from "../../../../core/data/http/binary-response-dto";
import type { LeaveAttachmentDto } from "../models/leave-attachment-dto";
import type {
  LeaveReceiptDto,
  LeaveRequestChangeDto,
  LeaveRequestDecisionDto,
} from "../models/leave-request-change-dto";
import type { LeaveRequestDetailsDto } from "../models/leave-request-details-dto";
import type { LeaveRequestPageDto } from "../models/leave-request-summary-dto";

export interface LeaveRequestDataSource {
  downloadAttachment(
    company: string,
    request: string,
    attachment: Pick<LeaveAttachmentDto, "revisionId" | "mediaType" | "size" | "sha256">,
    signal: AbortSignal,
  ): Promise<BinaryResponseDto>;
  decide(
    company: string,
    id: string,
    operation: string,
    body: LeaveRequestDecisionDto,
    signal: AbortSignal,
  ): Promise<LeaveReceiptDto>;
  withdraw(
    company: string,
    id: string,
    operation: string,
    body: LeaveRequestChangeDto,
    signal: AbortSignal,
  ): Promise<LeaveReceiptDto>;
  requestCancellation(
    company: string,
    id: string,
    operation: string,
    body: LeaveRequestChangeDto,
    signal: AbortSignal,
  ): Promise<LeaveReceiptDto>;
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
