import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess, Session } from "../entities/session";
import type { IdentityRepository } from "../repositories/identity-repository";

export class LoadCompanyAccess {
  constructor(private readonly identities: IdentityRepository) {}

  async execute(
    session: Session,
    companyId: CompanyId,
    signal: AbortSignal,
  ): Promise<Result<CompanyAccess>> {
    if (!session.companies.some((company) => company.id === companyId))
      return failed("company_access_denied");
    const result = await this.identities.access(companyId, signal);
    if (result.ok && result.value.companyId !== companyId) return failed("invalid_response");
    return result;
  }
}
