import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { WorkPeriodCommand } from "../../domain/entities/work-period-command";
import type { WorkPeriodEmployeeSearch } from "../../domain/entities/work-period-employee-search";
import type { WorkPeriodRepository } from "../../domain/repositories/work-period-repository";
import type { WorkPeriodDataSource } from "../datasources/work-period-data-source";
import { toWorkPeriodCommandDto, toWorkPeriodReceipt } from "../mappers/work-period-command-mapper";
import { toWorkPeriodOverview } from "../mappers/work-period-overview-mapper";
import { toWorkPeriodSnapshot } from "../mappers/work-period-snapshot-mapper";
import { toWorkforceEmployeePage } from "../mappers/workforce-employee-mapper";

export class RemoteWorkPeriodRepository implements WorkPeriodRepository {
  constructor(private readonly source: WorkPeriodDataSource) {}
  overview(company: CompanyId, month: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toWorkPeriodOverview(await this.source.overview(company, month, signal), company, month),
    );
  }
  employees(company: CompanyId, search: WorkPeriodEmployeeSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toWorkforceEmployeePage(
        await this.source.employees(company, search, signal),
        company,
        search.after,
      ),
    );
  }
  snapshot(company: CompanyId, month: string, employee: EmployeeId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toWorkPeriodSnapshot(
        await this.source.snapshot(company, month, employee, signal),
        company,
        month,
        employee,
      ),
    );
  }
  close(
    company: CompanyId,
    operation: OperationId,
    command: WorkPeriodCommand,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toWorkPeriodReceipt(
        await this.source.close(
          company,
          command.month,
          operation,
          toWorkPeriodCommandDto(command),
          signal,
        ),
        command,
        "close",
      ),
    );
  }
  recover(
    company: CompanyId,
    operation: OperationId,
    command: WorkPeriodCommand,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toWorkPeriodReceipt(
        await this.source.recover(
          company,
          command.month,
          operation,
          toWorkPeriodCommandDto(command),
          signal,
        ),
        command,
        "recover",
      ),
    );
  }
}
