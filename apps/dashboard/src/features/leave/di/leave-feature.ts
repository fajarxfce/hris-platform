import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpLeaveRequestDataSource } from "../data/datasources/http-leave-request-data-source";
import { RemoteLeaveRequestRepository } from "../data/repositories/remote-leave-request-repository";
import { LoadLeaveRequest } from "../domain/usecases/load-leave-request";
import { LoadLeaveRequests } from "../domain/usecases/load-leave-requests";

export function createLeaveFeature(http: HttpClient) {
  const requests = new RemoteLeaveRequestRepository(new HttpLeaveRequestDataSource(http));
  return {
    loadRequests: new LoadLeaveRequests(requests),
    loadRequest: new LoadLeaveRequest(requests),
  };
}
