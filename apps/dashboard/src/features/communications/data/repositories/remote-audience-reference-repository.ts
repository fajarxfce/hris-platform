import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { AudienceReferenceSearch } from "../../domain/entities/audience-reference-search";
import type { AudienceReferenceRepository } from "../../domain/repositories/audience-reference-repository";
import type { AudienceReferenceDataSource } from "../datasources/audience-reference-data-source";
import { toAudienceReferencePage } from "../mappers/audience-reference-mapper";

export class RemoteAudienceReferenceRepository implements AudienceReferenceRepository {
  constructor(private readonly source: AudienceReferenceDataSource) {}
  list(company: CompanyId, search: AudienceReferenceSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toAudienceReferencePage(
        await this.source.list(
          company,
          {
            kind: search.kind,
            query: search.query,
            ids: [...search.ids],
            after: search.after,
          },
          signal,
        ),
        search,
      ),
    );
  }
}
