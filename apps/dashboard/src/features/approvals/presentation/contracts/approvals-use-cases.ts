import type { LoadApprovalAssignees } from "../../domain/usecases/load-approval-assignees";
import type { LoadApprovalInbox } from "../../domain/usecases/load-approval-inbox";
import type { LoadApprovalRequest } from "../../domain/usecases/load-approval-request";
import type { LoadApprovalTemplate } from "../../domain/usecases/load-approval-template";
import type { LoadApprovalTemplates } from "../../domain/usecases/load-approval-templates";
import type { SaveApprovalTemplate } from "../../domain/usecases/save-approval-template";

export type ApprovalsUseCases = Readonly<{
  loadInbox: Pick<LoadApprovalInbox, "execute">;
  loadRequest: Pick<LoadApprovalRequest, "execute">;
  loadTemplates: Pick<LoadApprovalTemplates, "execute">;
  loadTemplate: Pick<LoadApprovalTemplate, "execute">;
  saveTemplate: Pick<SaveApprovalTemplate, "execute">;
  loadAssignees: Pick<LoadApprovalAssignees, "execute">;
}>;
