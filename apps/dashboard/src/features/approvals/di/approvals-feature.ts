import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpApprovalDataSource } from "../data/datasources/http-approval-data-source";
import { RemoteApprovalRepository } from "../data/repositories/remote-approval-repository";
import { LoadApprovalInbox } from "../domain/usecases/load-approval-inbox";
import { LoadApprovalRequest } from "../domain/usecases/load-approval-request";

export function createApprovalsFeature(http: HttpClient) {
  const approvals = new RemoteApprovalRepository(new HttpApprovalDataSource(http));
  return {
    loadInbox: new LoadApprovalInbox(approvals),
    loadRequest: new LoadApprovalRequest(approvals),
  };
}
