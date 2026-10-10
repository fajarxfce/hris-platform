import type { LoadApprovalAssignees } from "../../domain/usecases/load-approval-assignees";
import type { LoadApprovalDelegation } from "../../domain/usecases/load-approval-delegation";
import type { LoadApprovalDelegationForEdit } from "../../domain/usecases/load-approval-delegation-for-edit";
import type { LoadApprovalDelegations } from "../../domain/usecases/load-approval-delegations";
import type { LoadApprovalInbox } from "../../domain/usecases/load-approval-inbox";
import type { LoadApprovalRequest } from "../../domain/usecases/load-approval-request";
import type { LoadApprovalTemplate } from "../../domain/usecases/load-approval-template";
import type { LoadApprovalTemplates } from "../../domain/usecases/load-approval-templates";
import type { SaveApprovalDelegation } from "../../domain/usecases/save-approval-delegation";
import type { SaveApprovalTemplate } from "../../domain/usecases/save-approval-template";

export type ApprovalsUseCases = Readonly<{
  loadDelegations: Pick<LoadApprovalDelegations, "execute">;
  loadDelegation: Pick<LoadApprovalDelegation, "execute">;
  loadDelegationForEdit: Pick<LoadApprovalDelegationForEdit, "execute">;
  saveDelegation: Pick<SaveApprovalDelegation, "execute">;
  loadInbox: Pick<LoadApprovalInbox, "execute">;
  loadRequest: Pick<LoadApprovalRequest, "execute">;
  loadTemplates: Pick<LoadApprovalTemplates, "execute">;
  loadTemplate: Pick<LoadApprovalTemplate, "execute">;
  saveTemplate: Pick<SaveApprovalTemplate, "execute">;
  loadAssignees: Pick<LoadApprovalAssignees, "execute">;
}>;
