import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { AccountId, CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { CompanyMembershipChange } from "../../domain/entities/company-membership-change";
import type { CompanyMemberRepository } from "../../domain/repositories/company-member-repository";
import type { CompanyMemberDataSource } from "../datasources/company-member-data-source";
import { toCompanyMemberGrant, toCompanyMemberPage } from "../mappers/company-member-mapper";

export class RemoteCompanyMemberRepository implements CompanyMemberRepository {
  constructor(private readonly source: CompanyMemberDataSource) {}
  save(
    companyId: CompanyId,
    operation: OperationId,
    input: CompanyMembershipChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const { accountId, permissions, roleTemplates, ...fields } = input;
      const receipt = await this.source.save(
        companyId,
        accountId,
        operation,
        {
          ...fields,
          permissions: [...permissions],
          roleTemplates: roleTemplates.map((role) => ({ ...role })),
        },
        signal,
      );
      if (receipt.id.toLowerCase() !== accountId || receipt.version !== input.expectedVersion + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
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
