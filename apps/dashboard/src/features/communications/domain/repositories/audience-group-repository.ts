import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { AudienceGroup, AudienceGroupId, AudienceGroupPage } from "../entities/audience-group";
import type { AudienceGroupChange } from "../entities/audience-group-change";

export interface AudienceGroupRepository {
  list(
    company: CompanyId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<AudienceGroupPage>>;
  get(
    company: CompanyId,
    id: AudienceGroupId,
    version: number | null,
    signal: AbortSignal,
  ): Promise<Result<AudienceGroup>>;
  save(
    company: CompanyId,
    operation: OperationId,
    change: AudienceGroupChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
