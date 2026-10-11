import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { AudienceGroupId } from "../../domain/entities/audience-group";
import type { AudienceGroupChange } from "../../domain/entities/audience-group-change";
import type { AudienceGroupRepository } from "../../domain/repositories/audience-group-repository";
import type { AudienceGroupDataSource } from "../datasources/audience-group-data-source";
import { toAudienceGroup, toAudienceGroupPage } from "../mappers/audience-group-mapper";

export class RemoteAudienceGroupRepository implements AudienceGroupRepository {
  constructor(private readonly source: AudienceGroupDataSource) {}
  list(company: CompanyId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toAudienceGroupPage(await this.source.list(company, after, signal), after),
    );
  }
  get(company: CompanyId, id: AudienceGroupId, revision: number | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toAudienceGroup(await this.source.get(company, id, revision, signal), id, revision),
    );
  }
  save(
    company: CompanyId,
    operation: OperationId,
    input: AudienceGroupChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.save(
        company,
        input.id,
        operation,
        {
          expectedVersion: input.expectedVersion,
          name: input.name,
          active: input.active,
          employmentIds: [...input.employmentIds],
          reason: input.reason,
        },
        signal,
      );
      if (
        receipt.id.toLowerCase() !== input.id ||
        receipt.version !== (input.expectedVersion ?? -1) + 1 ||
        receipt.version > 999
      )
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
}
