import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type {
  PayrollCalculationRecovery,
  PayrollCalculationStart,
} from "../../domain/entities/payroll-calculation-command";
import type { PayrollCalculationRepository } from "../../domain/repositories/payroll-calculation-repository";
import type { PayrollCalculationDataSource } from "../datasources/payroll-calculation-data-source";
import {
  toPayrollCalculationReview,
  toPayrollRunDetails,
  toPayrollRuns,
} from "../mappers/payroll-calculation-mapper";
import { toPayrollRunEmployee } from "../mappers/payroll-run-employee-mapper";
export class RemotePayrollCalculationRepository implements PayrollCalculationRepository {
  constructor(private readonly source: PayrollCalculationDataSource) {}
  review(company: CompanyId, period: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollCalculationReview(
        await this.source.review(company, period, signal),
        company,
        period,
      ),
    );
  }
  runs(company: CompanyId, period: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollRuns(await this.source.runs(company, period, signal), company, period),
    );
  }
  run(company: CompanyId, id: string, after: string | null, limit: number, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollRunDetails(
        await this.source.run(company, id, after, limit, signal),
        company,
        id,
        after,
        limit,
      ),
    );
  }
  employee(company: CompanyId, id: string, employee: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollRunEmployee(
        await this.source.employee(company, id, employee, signal),
        company,
        id,
        employee,
      ),
    );
  }
  start(
    company: CompanyId,
    period: string,
    operation: OperationId,
    input: PayrollCalculationStart,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.start(company, period, operation, input, signal);
      if (receipt.id.toLowerCase() !== input.id || receipt.version !== 0)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
  resume(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollCalculationRecovery,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.resume(company, id, operation, input, signal);
      if (receipt.id.toLowerCase() !== id || receipt.version !== input.expectedVersion + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
  abandon(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollCalculationRecovery,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.abandon(company, id, operation, input, signal);
      if (receipt.id.toLowerCase() !== id || receipt.version !== input.expectedVersion + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
}
