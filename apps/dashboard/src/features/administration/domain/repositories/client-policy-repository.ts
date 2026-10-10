import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { ClientPolicyChange } from "../entities/client-policy-change";
import type { ClientPolicyRevision } from "../entities/client-policy-revision";
import type { ClientPolicySettings } from "../entities/client-policy-settings";

export interface ClientPolicyRepository {
  save(
    companyId: CompanyId,
    operation: OperationId,
    input: ClientPolicyChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  settings(companyId: CompanyId, signal: AbortSignal): Promise<Result<ClientPolicySettings>>;
  revision(
    companyId: CompanyId,
    version: number,
    signal: AbortSignal,
  ): Promise<Result<ClientPolicyRevision>>;
}
