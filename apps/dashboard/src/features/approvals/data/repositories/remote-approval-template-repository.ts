import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type {
  ApprovalTemplateId,
  ApprovalTemplateSearch,
} from "../../domain/entities/approval-template";
import type { ApprovalTemplateChange } from "../../domain/entities/approval-template-change";
import type { ApprovalTemplateRepository } from "../../domain/repositories/approval-template-repository";
import type { ApprovalAdministrationDataSource } from "../datasources/approval-administration-data-source";
import { toApprovalTemplate, toApprovalTemplatePage } from "../mappers/approval-template-mapper";

export class RemoteApprovalTemplateRepository implements ApprovalTemplateRepository {
  constructor(private readonly source: ApprovalAdministrationDataSource) {}
  list(company: CompanyId, search: ApprovalTemplateSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toApprovalTemplatePage(await this.source.templates(company, search, signal), company, search),
    );
  }
  get(company: CompanyId, id: ApprovalTemplateId, revision: number | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const template = toApprovalTemplate(
        await this.source.template(company, id, revision, signal),
        company,
      );
      if (template.id !== id || (revision !== null && template.revision !== revision))
        throw new InvalidHttpResponseError();
      return template;
    });
  }
  save(
    company: CompanyId,
    operation: OperationId,
    change: ApprovalTemplateChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.saveTemplate(
        company,
        change.id,
        operation,
        {
          name: change.name,
          kind: change.kind,
          active: change.active,
          expectedVersion: change.expectedVersion,
          effectiveFrom: change.effectiveFrom,
          category: change.category,
          minimumAmount: change.minimumAmount,
          stages: change.stages.map((stage) => ({ ...stage, accountIds: [...stage.accountIds] })),
          reason: change.reason,
        },
        signal,
      );
      if (
        receipt.id.toLowerCase() !== change.id ||
        receipt.version !== (change.expectedVersion ?? -1) + 1
      )
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
}
