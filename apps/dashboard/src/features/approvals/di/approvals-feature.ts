import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpApprovalAdministrationDataSource } from "../data/datasources/http-approval-administration-data-source";
import { HttpApprovalDataSource } from "../data/datasources/http-approval-data-source";
import { RemoteApprovalAssigneeRepository } from "../data/repositories/remote-approval-assignee-repository";
import { RemoteApprovalRepository } from "../data/repositories/remote-approval-repository";
import { RemoteApprovalTemplateRepository } from "../data/repositories/remote-approval-template-repository";
import { LoadApprovalAssignees } from "../domain/usecases/load-approval-assignees";
import { LoadApprovalInbox } from "../domain/usecases/load-approval-inbox";
import { LoadApprovalRequest } from "../domain/usecases/load-approval-request";
import { LoadApprovalTemplate } from "../domain/usecases/load-approval-template";
import { LoadApprovalTemplates } from "../domain/usecases/load-approval-templates";
import { SaveApprovalTemplate } from "../domain/usecases/save-approval-template";

export function createApprovalsFeature(http: HttpClient) {
  const approvals = new RemoteApprovalRepository(new HttpApprovalDataSource(http));
  const administration = new HttpApprovalAdministrationDataSource(http);
  const templates = new RemoteApprovalTemplateRepository(administration);
  const assignees = new RemoteApprovalAssigneeRepository(administration);
  return {
    loadInbox: new LoadApprovalInbox(approvals),
    loadRequest: new LoadApprovalRequest(approvals),
    loadTemplates: new LoadApprovalTemplates(templates),
    loadTemplate: new LoadApprovalTemplate(templates),
    saveTemplate: new SaveApprovalTemplate(templates),
    loadAssignees: new LoadApprovalAssignees(assignees),
  };
}
