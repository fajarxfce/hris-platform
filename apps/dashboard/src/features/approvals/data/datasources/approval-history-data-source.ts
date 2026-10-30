import type { ApprovalHistoryDto } from "../models/approval-history-dto";
export interface ApprovalHistoryDataSource {
  get(
    company: string,
    id: string,
    after: number | null,
    expectedVersion: number | null,
    signal: AbortSignal,
  ): Promise<ApprovalHistoryDto>;
}
