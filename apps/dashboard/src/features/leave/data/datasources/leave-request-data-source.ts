import type {
  LeaveReceiptDto,
  LeaveRequestChangeDto,
  LeaveRequestDecisionDto,
} from "../models/leave-request-change-dto";
import type { LeaveRequestDetailsDto } from "../models/leave-request-details-dto";
import type { LeaveRequestPageDto } from "../models/leave-request-summary-dto";

export interface LeaveRequestDataSource {
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
