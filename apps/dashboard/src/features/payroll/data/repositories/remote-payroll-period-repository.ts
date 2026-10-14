import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { PayrollPeriodCreation } from "../../domain/entities/payroll-period-creation";
import type { PayrollPeriodSearch } from "../../domain/entities/payroll-period-search";
import type { PayrollPeriodRepository } from "../../domain/repositories/payroll-period-repository";
import type { PayrollPeriodDataSource } from "../datasources/payroll-period-data-source";
import {
  toPayrollPeriodDetails,
  toPayrollPeriodEmployees,
  toPayrollPeriods,
} from "../mappers/payroll-period-mapper";
export class RemotePayrollPeriodRepository implements PayrollPeriodRepository {
  constructor(private readonly source: PayrollPeriodDataSource) {}
  list(company: CompanyId, search: PayrollPeriodSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollPeriods(
        await this.source.list(
          company,
          search.from,
          search.until,
          search.status,
          search.after,
          signal,
        ),
        company,
        search,
      ),
    );
  }
  details(
    company: CompanyId,
    id: string,
    memberAfter: string | null,
    historyAfter: string | null,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toPayrollPeriodDetails(
        await this.source.details(company, id, memberAfter, historyAfter, signal),
        company,
        id,
        memberAfter,
        historyAfter,
      ),
    );
  }
  employees(company: CompanyId, query: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollPeriodEmployees(await this.source.employees(company, query, after, signal), after),
    );
  }
  create(
    company: CompanyId,
    operation: OperationId,
    input: PayrollPeriodCreation,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.create(
        company,
        operation,
        { ...input, employeeIds: [...input.employeeIds] },
        signal,
      );
      if (receipt.id.toLowerCase() !== input.id || receipt.version !== 0)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
  cancel(
    company: CompanyId,
    id: string,
    operation: OperationId,
    version: number,
    reason: string,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.cancel(company, id, operation, version, reason, signal);
      if (receipt.id.toLowerCase() !== id || receipt.version !== version + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
}
