import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { RoleTemplateId, RoleTemplatePage } from "../entities/role-template";
import type { RoleTemplateChange } from "../entities/role-template-change";
import type { RoleTemplateReview } from "../entities/role-template-review";

export interface RoleTemplateRepository {
  permissions(company: CompanyId, signal: AbortSignal): Promise<Result<readonly string[]>>;
  list(
    company: CompanyId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<RoleTemplatePage>>;
  get(
    company: CompanyId,
    id: RoleTemplateId,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<Result<RoleTemplateReview>>;
  save(
    company: CompanyId,
    operation: OperationId,
    change: RoleTemplateChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
