import type { ApprovalReassignmentDto } from "../models/approval-reassignment-dto";
import type { ApprovalInboxDto, ApprovalRequestDto } from "../models/approval-request-dto";
import type { ApprovalAdministrationReceiptDto } from "../models/approval-template-dto";

export interface ApprovalDataSource {
  inbox(company: string, after: string | null, signal: AbortSignal): Promise<ApprovalInboxDto>;
  get(company: string, id: string, signal: AbortSignal): Promise<ApprovalRequestDto>;
  reassign(
    company: string,
    id: string,
    operation: string,
    body: ApprovalReassignmentDto,
    signal: AbortSignal,
  ): Promise<ApprovalAdministrationReceiptDto>;
}
