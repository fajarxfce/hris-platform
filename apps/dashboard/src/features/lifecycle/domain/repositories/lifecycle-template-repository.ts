import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { LifecycleTemplate, LifecycleTemplateId } from "../entities/lifecycle-template";
import type { LifecycleTemplateChange } from "../entities/lifecycle-template-change";
import type { LifecycleTemplatePage } from "../entities/lifecycle-template-page";

export interface LifecycleTemplateRepository {
  list(
    company: CompanyId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LifecycleTemplatePage>>;
  get(
    company: CompanyId,
    id: LifecycleTemplateId,
    signal: AbortSignal,
  ): Promise<Result<LifecycleTemplate>>;
  save(
    company: CompanyId,
    operation: OperationId,
    change: LifecycleTemplateChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
