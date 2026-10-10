import type { LoadLeaveRequest } from "../../domain/usecases/load-leave-request";
import type { LoadLeaveRequests } from "../../domain/usecases/load-leave-requests";

export type LeaveUseCases = Readonly<{
  loadRequests: Pick<LoadLeaveRequests, "execute">;
  loadRequest: Pick<LoadLeaveRequest, "execute">;
}>;
