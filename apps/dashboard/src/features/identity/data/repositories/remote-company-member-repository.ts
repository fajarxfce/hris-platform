import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { CompanyMemberRepository } from "../../domain/repositories/company-member-repository";
import type { CompanyMemberDataSource } from "../datasources/company-member-data-source";
import { toCompanyMemberGrant, toCompanyMemberPage } from "../mappers/company-member-mapper";

export class RemoteCompanyMemberRepository implements CompanyMemberRepository {
  constructor(private readonly source: CompanyMemberDataSource) {}
  list(companyId: CompanyId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toCompanyMemberPage(await this.source.list(companyId, after, signal), after),
    );
  }
  get(companyId: CompanyId, accountId: AccountId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toCompanyMemberGrant(await this.source.get(companyId, accountId, signal), accountId),
    );
  }
}
