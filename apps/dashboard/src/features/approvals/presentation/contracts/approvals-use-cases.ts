import type { LoadApprovalInbox } from "../../domain/usecases/load-approval-inbox";
import type { LoadApprovalRequest } from "../../domain/usecases/load-approval-request";

export type ApprovalsUseCases = Readonly<{
  loadInbox: Pick<LoadApprovalInbox, "execute">;
  loadRequest: Pick<LoadApprovalRequest, "execute">;
}>;
