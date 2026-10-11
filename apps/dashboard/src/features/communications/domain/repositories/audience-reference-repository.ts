import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { AudienceReferencePage } from "../entities/audience-reference";
import type { AudienceReferenceSearch } from "../entities/audience-reference-search";

export interface AudienceReferenceRepository {
  list(
    company: CompanyId,
    search: AudienceReferenceSearch,
    signal: AbortSignal,
  ): Promise<Result<AudienceReferencePage>>;
}
