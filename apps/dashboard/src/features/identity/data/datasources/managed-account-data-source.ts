import type { AccountAccessChangeDto } from "../models/account-access-change-dto";
import type {
  AccountAccessReceiptDto,
  ManagedAccountPageDto,
  ManagedAccountReviewDto,
} from "../models/managed-account-dto";
export interface ManagedAccountDataSource {
  list(query: string, after: string | null, signal: AbortSignal): Promise<ManagedAccountPageDto>;
  get(id: string, signal: AbortSignal): Promise<ManagedAccountReviewDto>;
  save(
    id: string,
    operation: string,
    input: AccountAccessChangeDto,
    signal: AbortSignal,
  ): Promise<AccountAccessReceiptDto>;
}
