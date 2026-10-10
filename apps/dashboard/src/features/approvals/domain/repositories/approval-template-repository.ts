import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type {
  ApprovalTemplate,
  ApprovalTemplateId,
  ApprovalTemplatePage,
  ApprovalTemplateSearch,
} from "../entities/approval-template";
import type { ApprovalTemplateChange } from "../entities/approval-template-change";

export interface ApprovalTemplateRepository {
  list(
    company: CompanyId,
    search: ApprovalTemplateSearch,
    signal: AbortSignal,
  ): Promise<Result<ApprovalTemplatePage>>;
  get(
    company: CompanyId,
    id: ApprovalTemplateId,
    revision: number | null,
    signal: AbortSignal,
  ): Promise<Result<ApprovalTemplate>>;
  save(
    company: CompanyId,
    operation: OperationId,
    change: ApprovalTemplateChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
