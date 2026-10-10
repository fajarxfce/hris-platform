import type { CompanyMemberGrantDto, CompanyMemberPageDto } from "../models/company-member-dto";

export interface CompanyMemberDataSource {
  list(companyId: string, after: string | null, signal: AbortSignal): Promise<CompanyMemberPageDto>;
  get(companyId: string, accountId: string, signal: AbortSignal): Promise<CompanyMemberGrantDto>;
}
