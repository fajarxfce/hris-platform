import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../domain/entities/employee";
import type { EmployeeCreation } from "../../domain/entities/employee-creation";
import type { EmployeeSearch } from "../../domain/entities/employee-search";
import type { EmploymentCancellation } from "../../domain/entities/employment-cancellation";
import type { EmploymentChange } from "../../domain/entities/employment-change";
import type { EmployeeRepository } from "../../domain/repositories/employee-repository";
import type { EmployeeDataSource } from "../datasources/employee-data-source";
import {
  toEmployeeCreationDto,
  toEmployeeCreationReceipt,
} from "../mappers/employee-creation-mapper";
import { toEmployee, toEmployeePage } from "../mappers/employee-mapper";
import {
  toEmploymentCancellationDto,
  toEmploymentCancellationReceipt,
} from "../mappers/employment-cancellation-mapper";
import {
  toEmploymentChangeDto,
  toEmploymentChangeReceipt,
} from "../mappers/employment-change-mapper";
import { toEmploymentDetails } from "../mappers/employment-details-mapper";
import { toEmploymentHistoryPage } from "../mappers/employment-history-mapper";
import { toEmploymentRevisionDetails } from "../mappers/employment-revision-details-mapper";

export class RemoteEmployeeRepository implements EmployeeRepository {
  constructor(private readonly source: EmployeeDataSource) {}
  revision(companyId: CompanyId, id: EmployeeId, revision: number, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toEmploymentRevisionDetails(
        await this.source.revision(companyId, id, revision, signal),
        id,
        revision,
      ),
    );
  }
  cancelRevision(
    companyId: CompanyId,
    operation: OperationId,
    input: EmploymentCancellation,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toEmploymentCancellationReceipt(
        await this.source.cancelRevision(
          companyId,
          input.employeeId,
          input.revision,
          operation,
          toEmploymentCancellationDto(input),
          signal,
        ),
        input,
      ),
    );
  }
  details(companyId: CompanyId, id: EmployeeId, asOf: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toEmploymentDetails(
        await this.source.details(companyId, id, asOf, signal),
        companyId,
        id,
        asOf,
      ),
    );
  }
  revise(
    companyId: CompanyId,
    operation: OperationId,
    change: EmploymentChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toEmploymentChangeReceipt(
        await this.source.revise(
          companyId,
          change.employeeId,
          operation,
          toEmploymentChangeDto(change),
          signal,
        ),
        change,
      ),
    );
  }
  create(
    companyId: CompanyId,
    operation: OperationId,
    input: EmployeeCreation,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toEmployeeCreationReceipt(
        await this.source.create(companyId, operation, toEmployeeCreationDto(input), signal),
        input,
      ),
    );
  }
  list(companyId: CompanyId, search: EmployeeSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toEmployeePage(await this.source.list(companyId, search, signal), companyId, search),
    );
  }
  get(companyId: CompanyId, id: EmployeeId, asOf: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toEmployee(await this.source.get(companyId, id, asOf, signal), companyId, asOf, id),
    );
  }
  history(companyId: CompanyId, id: EmployeeId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toEmploymentHistoryPage(await this.source.history(companyId, id, after, signal), after),
    );
  }
}
