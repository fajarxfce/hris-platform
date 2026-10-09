import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ClientPolicyRepository } from "../../domain/repositories/client-policy-repository";
import type { ClientPolicyDataSource } from "../datasources/client-policy-data-source";
import { toClientPolicyRevision } from "../mappers/client-policy-revision-mapper";
import { toClientPolicySettings } from "../mappers/client-policy-settings-mapper";

export class RemoteClientPolicyRepository implements ClientPolicyRepository {
  constructor(private readonly source: ClientPolicyDataSource) {}

  settings(companyId: CompanyId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toClientPolicySettings(await this.source.settings(companyId, signal), companyId),
    );
  }

  revision(companyId: CompanyId, version: number, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toClientPolicyRevision(
        await this.source.revision(companyId, version, signal),
        companyId,
        version,
      ),
    );
  }
}
