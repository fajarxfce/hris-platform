import type { CompanyMemberGrantDto, CompanyMemberPageDto } from "../models/company-member-dto";
import type {
  CompanyMembershipChangeDto,
  CompanyMembershipReceiptDto,
} from "../models/company-membership-change-dto";

export interface CompanyMemberDataSource {
  save(
    companyId: string,
    accountId: string,
    operation: string,
    input: CompanyMembershipChangeDto,
    signal: AbortSignal,
  ): Promise<CompanyMembershipReceiptDto>;
  list(companyId: string, after: string | null, signal: AbortSignal): Promise<CompanyMemberPageDto>;
  get(companyId: string, accountId: string, signal: AbortSignal): Promise<CompanyMemberGrantDto>;
}
