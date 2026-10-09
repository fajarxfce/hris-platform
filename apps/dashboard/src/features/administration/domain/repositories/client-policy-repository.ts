import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { ClientPolicyRevision } from "../entities/client-policy-revision";
import type { ClientPolicySettings } from "../entities/client-policy-settings";

export interface ClientPolicyRepository {
  settings(companyId: CompanyId, signal: AbortSignal): Promise<Result<ClientPolicySettings>>;
  revision(
    companyId: CompanyId,
    version: number,
    signal: AbortSignal,
  ): Promise<Result<ClientPolicyRevision>>;
}
