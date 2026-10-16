import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { RoleTemplateId } from "../../domain/entities/role-template";
import type { RoleTemplateChange } from "../../domain/entities/role-template-change";
import type { RoleTemplateRepository } from "../../domain/repositories/role-template-repository";
import type { RoleTemplateDataSource } from "../datasources/role-template-data-source";
import {
  toRolePermissions,
  toRoleTemplatePage,
  toRoleTemplateReview,
} from "../mappers/role-template-mapper";
export class RemoteRoleTemplateRepository implements RoleTemplateRepository {
  constructor(private readonly source: RoleTemplateDataSource) {}
  permissions(company: CompanyId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toRolePermissions(await this.source.permissions(company, signal)),
    );
  }
  list(company: CompanyId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toRoleTemplatePage(await this.source.list(company, after, signal), company, after),
    );
  }
  get(company: CompanyId, id: RoleTemplateId, historyAfter: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toRoleTemplateReview(
        await this.source.get(company, id, historyAfter, signal),
        company,
        id,
        historyAfter,
      ),
    );
  }
  save(
    company: CompanyId,
    operation: OperationId,
    change: RoleTemplateChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const { id, permissions, ...fields } = change;
      const receipt = await this.source.save(
        company,
        id,
        operation,
        { ...fields, permissions: [...permissions] },
        signal,
      );
      if (receipt.id.toLowerCase() !== id || receipt.version !== (change.expectedVersion ?? -1) + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
}
