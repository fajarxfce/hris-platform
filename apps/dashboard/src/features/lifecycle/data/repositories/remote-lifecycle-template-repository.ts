import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { LifecycleTemplateId } from "../../domain/entities/lifecycle-template";
import type { LifecycleTemplateChange } from "../../domain/entities/lifecycle-template-change";
import type { LifecycleTemplateRepository } from "../../domain/repositories/lifecycle-template-repository";
import type { LifecycleTemplateDataSource } from "../datasources/lifecycle-template-data-source";
import {
  toLifecycleTemplateChangeDto,
  toLifecycleTemplateReceipt,
} from "../mappers/lifecycle-template-change-mapper";
import {
  toLifecycleTemplateDetails,
  toLifecycleTemplatePage,
} from "../mappers/lifecycle-template-mapper";

export class RemoteLifecycleTemplateRepository implements LifecycleTemplateRepository {
  constructor(private readonly source: LifecycleTemplateDataSource) {}
  list(company: CompanyId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLifecycleTemplatePage(await this.source.list(company, after, signal), company, after),
    );
  }
  get(company: CompanyId, id: LifecycleTemplateId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLifecycleTemplateDetails(await this.source.get(company, id, signal), company, id),
    );
  }
  save(
    company: CompanyId,
    operation: OperationId,
    change: LifecycleTemplateChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toLifecycleTemplateReceipt(
        await this.source.save(
          company,
          change.id,
          operation,
          toLifecycleTemplateChangeDto(change),
          signal,
        ),
        change,
      ),
    );
  }
}
