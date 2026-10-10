import type {
  ApprovalAssigneePageDto,
  ApprovalAssigneeSearchDto,
} from "../models/approval-assignee-dto";
import type {
  ApprovalAdministrationReceiptDto,
  ApprovalTemplateChangeDto,
  ApprovalTemplateDto,
  ApprovalTemplatePageDto,
  ApprovalTemplateSearchDto,
} from "../models/approval-template-dto";

export interface ApprovalAdministrationDataSource {
  templates(
    company: string,
    search: ApprovalTemplateSearchDto,
    signal: AbortSignal,
  ): Promise<ApprovalTemplatePageDto>;
  template(
    company: string,
    id: string,
    revision: number | null,
    signal: AbortSignal,
  ): Promise<ApprovalTemplateDto>;
  saveTemplate(
    company: string,
    id: string,
    operation: string,
    change: ApprovalTemplateChangeDto,
    signal: AbortSignal,
  ): Promise<ApprovalAdministrationReceiptDto>;
  assignees(
    company: string,
    search: ApprovalAssigneeSearchDto,
    signal: AbortSignal,
  ): Promise<ApprovalAssigneePageDto>;
}
