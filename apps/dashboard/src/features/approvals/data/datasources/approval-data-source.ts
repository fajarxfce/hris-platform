import type { ApprovalInboxDto, ApprovalRequestDto } from "../models/approval-request-dto";

export interface ApprovalDataSource {
  inbox(company: string, after: string | null, signal: AbortSignal): Promise<ApprovalInboxDto>;
  get(company: string, id: string, signal: AbortSignal): Promise<ApprovalRequestDto>;
}
