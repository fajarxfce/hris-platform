import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ClientPolicyChange } from "../entities/client-policy-change";
import { normalizeClientPolicyChange } from "../policies/client-policy-change-policy";
import { canReadClientSettings } from "../policies/client-settings-policy";
import type { ClientPolicyRepository } from "../repositories/client-policy-repository";

export class SaveClientPolicy {
  constructor(private readonly policies: ClientPolicyRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: ClientPolicyChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadClientSettings(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_client_policy"));
    const normalized = normalizeClientPolicyChange(input);
    if (!normalized.ok) return Promise.resolve(normalized);
    return this.policies.save(access.companyId, operation, normalized.value, signal);
  }
}
