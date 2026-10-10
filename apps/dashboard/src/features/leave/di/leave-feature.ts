import type { HttpClient } from "../../../core/data/http/http-client";
import type { FileRepository } from "../../../core/domain/files/file-repository";
import { HttpLeavePolicyDataSource } from "../data/datasources/http-leave-policy-data-source";
import { HttpLeaveRequestDataSource } from "../data/datasources/http-leave-request-data-source";
import { RemoteLeavePolicyRepository } from "../data/repositories/remote-leave-policy-repository";
import { RemoteLeaveRequestRepository } from "../data/repositories/remote-leave-request-repository";
import { DecideLeaveRequest } from "../domain/usecases/decide-leave-request";
import { DownloadLeaveAttachment } from "../domain/usecases/download-leave-attachment";
import { LoadLeavePolicies } from "../domain/usecases/load-leave-policies";
import { LoadLeavePolicy } from "../domain/usecases/load-leave-policy";
import { LoadLeaveRequest } from "../domain/usecases/load-leave-request";
import { LoadLeaveRequests } from "../domain/usecases/load-leave-requests";
import { RequestLeaveCancellation } from "../domain/usecases/request-leave-cancellation";
import { ReviewLeaveAction } from "../domain/usecases/review-leave-action";
import { SaveLeavePolicy } from "../domain/usecases/save-leave-policy";
import { WithdrawLeaveRequest } from "../domain/usecases/withdraw-leave-request";

export function createLeaveFeature(
  http: HttpClient,
  files: Pick<FileRepository, "downloadBinary">,
) {
  const requests = new RemoteLeaveRequestRepository(new HttpLeaveRequestDataSource(http));
  const policies = new RemoteLeavePolicyRepository(new HttpLeavePolicyDataSource(http));
  return {
    savePolicy: new SaveLeavePolicy(policies),
    loadPolicies: new LoadLeavePolicies(policies),
    loadPolicy: new LoadLeavePolicy(policies),
    downloadAttachment: new DownloadLeaveAttachment(requests, files),
    loadRequests: new LoadLeaveRequests(requests),
    loadRequest: new LoadLeaveRequest(requests),
    reviewAction: new ReviewLeaveAction(requests),
    decide: new DecideLeaveRequest(requests),
    withdraw: new WithdrawLeaveRequest(requests),
    requestCancellation: new RequestLeaveCancellation(requests),
  };
}
