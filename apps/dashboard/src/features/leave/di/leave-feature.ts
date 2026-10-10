import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpLeaveRequestDataSource } from "../data/datasources/http-leave-request-data-source";
import { RemoteLeaveRequestRepository } from "../data/repositories/remote-leave-request-repository";
import { DecideLeaveRequest } from "../domain/usecases/decide-leave-request";
import { LoadLeaveRequest } from "../domain/usecases/load-leave-request";
import { LoadLeaveRequests } from "../domain/usecases/load-leave-requests";
import { RequestLeaveCancellation } from "../domain/usecases/request-leave-cancellation";
import { ReviewLeaveAction } from "../domain/usecases/review-leave-action";
import { WithdrawLeaveRequest } from "../domain/usecases/withdraw-leave-request";

export function createLeaveFeature(http: HttpClient) {
  const requests = new RemoteLeaveRequestRepository(new HttpLeaveRequestDataSource(http));
  return {
    loadRequests: new LoadLeaveRequests(requests),
    loadRequest: new LoadLeaveRequest(requests),
    reviewAction: new ReviewLeaveAction(requests),
    decide: new DecideLeaveRequest(requests),
    withdraw: new WithdrawLeaveRequest(requests),
    requestCancellation: new RequestLeaveCancellation(requests),
  };
}
