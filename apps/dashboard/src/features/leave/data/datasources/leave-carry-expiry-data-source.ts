import type {
  LeaveCarryExpiryDetailsDto,
  LeaveCarryExpiryPageDto,
  LeaveCarryExpiryResumeDto,
} from "../models/leave-carry-expiry-dto";
import type { LeaveReceiptDto } from "../models/leave-receipt-dto";
export interface LeaveCarryExpiryDataSource {
  list(
    company: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<LeaveCarryExpiryPageDto>;
  get(
    company: string,
    id: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<LeaveCarryExpiryDetailsDto>;
  resume(
    company: string,
    id: string,
    operation: string,
    input: LeaveCarryExpiryResumeDto,
    signal: AbortSignal,
  ): Promise<LeaveReceiptDto>;
}
