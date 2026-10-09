import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ClientPolicyReview } from "../entities/client-policy-review";
import { canReadClientSettings, isClientPolicyVersion } from "../policies/client-settings-policy";
import type { ClientPolicyRepository } from "../repositories/client-policy-repository";

/** The effective/head pair is one server snapshot; an explicitly selected history record is immutable. */
export class LoadClientPolicyReview {
  constructor(private readonly policies: ClientPolicyRepository) {}

  async execute(
    access: CompanyAccess,
    version: string | null,
    signal: AbortSignal,
  ): Promise<Result<ClientPolicyReview>> {
    signal.throwIfAborted();
    if (!canReadClientSettings(access.permissions)) return failed("access_denied");
    if (version !== null && !isClientPolicyVersion(version)) return failed("invalid_revision");
    const settings = await this.policies.settings(access.companyId, signal);
    signal.throwIfAborted();
    if (!settings.ok) return settings;
    if (version === null)
      return success(Object.freeze({ settings: settings.value, selected: settings.value.latest }));
    const selected = await this.policies.revision(access.companyId, Number(version), signal);
    signal.throwIfAborted();
    return selected.ok
      ? success(Object.freeze({ settings: settings.value, selected: selected.value }))
      : selected;
  }
}
