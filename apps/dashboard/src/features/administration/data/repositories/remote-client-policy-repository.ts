import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { ClientPolicyChange } from "../../domain/entities/client-policy-change";
import type { ClientPolicyRepository } from "../../domain/repositories/client-policy-repository";
import type { ClientPolicyDataSource } from "../datasources/client-policy-data-source";
import {
  toClientPolicyChangeDto,
  toClientPolicyReceipt,
} from "../mappers/client-policy-change-mapper";
import { toClientPolicyRevision } from "../mappers/client-policy-revision-mapper";
import { toClientPolicySettings } from "../mappers/client-policy-settings-mapper";

export class RemoteClientPolicyRepository implements ClientPolicyRepository {
  constructor(private readonly source: ClientPolicyDataSource) {}

  save(
    companyId: CompanyId,
    operation: OperationId,
    input: ClientPolicyChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toClientPolicyReceipt(
        await this.source.save(companyId, operation, toClientPolicyChangeDto(input), signal),
        companyId,
        input.expectedVersion,
      ),
    );
  }

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
