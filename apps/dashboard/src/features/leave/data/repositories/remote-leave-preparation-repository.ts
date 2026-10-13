import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveRequestSelection } from "../../domain/entities/leave-request-selection";
import type { LeavePreparationRepository } from "../../domain/repositories/leave-preparation-repository";
import type { LeavePreparationDataSource } from "../datasources/leave-preparation-data-source";
import {
  toLeaveEvidenceOptions,
  toLeavePreparationDirectory,
} from "../mappers/leave-preparation-mapper";
import { toLeaveRequestPreview } from "../mappers/leave-preview-mapper";
import { toLeaveRequestTypes } from "../mappers/leave-request-type-mapper";
import { toLeaveSelectionDto } from "../mappers/leave-submission-mapper";

export class RemoteLeavePreparationRepository implements LeavePreparationRepository {
  constructor(private readonly source: LeavePreparationDataSource) {}
  types(company: CompanyId, asOf: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveRequestTypes(
        await this.source.types(company, asOf, after, signal),
        company,
        asOf,
        after,
      ),
    );
  }
  employees(company: CompanyId, query: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeavePreparationDirectory(
        await this.source.employees(company, query, after, signal),
        company,
        after,
      ),
    );
  }
  evidence(company: CompanyId, employee: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveEvidenceOptions(
        await this.source.evidence(company, employee, after, signal),
        company,
        employee,
        after,
      ),
    );
  }
  preview(company: CompanyId, selection: LeaveRequestSelection, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveRequestPreview(
        await this.source.preview(company, toLeaveSelectionDto(selection), signal),
        company,
        selection,
      ),
    );
  }
}
