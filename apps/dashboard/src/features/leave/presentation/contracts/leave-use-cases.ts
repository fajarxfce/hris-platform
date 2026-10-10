import type { DecideLeaveRequest } from "../../domain/usecases/decide-leave-request";
import type { DownloadLeaveAttachment } from "../../domain/usecases/download-leave-attachment";
import type { LoadEmployeeLeaveBalances } from "../../domain/usecases/load-employee-leave-balances";
import type { LoadLeaveLedger } from "../../domain/usecases/load-leave-ledger";
import type { LoadLeavePolicies } from "../../domain/usecases/load-leave-policies";
import type { LoadLeavePolicy } from "../../domain/usecases/load-leave-policy";
import type { LoadLeaveRequest } from "../../domain/usecases/load-leave-request";
import type { LoadLeaveRequests } from "../../domain/usecases/load-leave-requests";
import type { RequestLeaveCancellation } from "../../domain/usecases/request-leave-cancellation";
import type { ReviewLeaveAction } from "../../domain/usecases/review-leave-action";
import type { SaveLeavePolicy } from "../../domain/usecases/save-leave-policy";
import type { WithdrawLeaveRequest } from "../../domain/usecases/withdraw-leave-request";

export type LeaveUseCases = Readonly<{
  loadBalances: Pick<LoadEmployeeLeaveBalances, "execute">;
  loadLedger: Pick<LoadLeaveLedger, "execute">;
  savePolicy: Pick<SaveLeavePolicy, "execute">;
  loadPolicies: Pick<LoadLeavePolicies, "execute">;
  loadPolicy: Pick<LoadLeavePolicy, "execute">;
  downloadAttachment: Pick<DownloadLeaveAttachment, "execute">;
  loadRequests: Pick<LoadLeaveRequests, "execute">;
  loadRequest: Pick<LoadLeaveRequest, "execute">;
  reviewAction: Pick<ReviewLeaveAction, "execute">;
  decide: Pick<DecideLeaveRequest, "execute">;
  withdraw: Pick<WithdrawLeaveRequest, "execute">;
  requestCancellation: Pick<RequestLeaveCancellation, "execute">;
}>;
