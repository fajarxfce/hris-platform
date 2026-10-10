import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { OrganizationUnitId } from "../../domain/entities/organization-unit";
import type { OrganizationUnitChange } from "../../domain/entities/organization-unit-change";
import type { OrganizationUnitSearch } from "../../domain/entities/organization-unit-search";
import type { OrganizationRepository } from "../../domain/repositories/organization-repository";
import type { OrganizationDataSource } from "../datasources/organization-data-source";
import {
  toOrganizationChangeDto,
  toOrganizationReceipt,
} from "../mappers/organization-change-mapper";
import {
  toOrganizationUnitDetails,
  toOrganizationUnitPage,
} from "../mappers/organization-unit-mapper";

export class RemoteOrganizationRepository implements OrganizationRepository {
  constructor(private readonly source: OrganizationDataSource) {}
  save(
    company: CompanyId,
    operation: OperationId,
    change: OrganizationUnitChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toOrganizationReceipt(
        await this.source.save(
          company,
          change.id,
          operation,
          toOrganizationChangeDto(change),
          signal,
        ),
        change,
      ),
    );
  }
  list(company: CompanyId, search: OrganizationUnitSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toOrganizationUnitPage(await this.source.list(company, search, signal), company, search),
    );
  }
  get(company: CompanyId, id: OrganizationUnitId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toOrganizationUnitDetails(await this.source.get(company, id, signal), company, id),
    );
  }
}
