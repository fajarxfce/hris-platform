import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type {
  PayrollInputChange,
  PayrollInputVerification,
} from "../../domain/entities/payroll-input-change";
import type { PayrollInputSearch } from "../../domain/entities/payroll-input-review";
import type { PayrollInputRepository } from "../../domain/repositories/payroll-input-repository";
import type { PayrollInputDataSource } from "../datasources/payroll-input-data-source";
import {
  toPayrollInput,
  toPayrollInputChangeDto,
  toPayrollInputEmployees,
  toPayrollInputReview,
} from "../mappers/payroll-input-mapper";
export class RemotePayrollInputRepository implements PayrollInputRepository {
  constructor(private readonly source: PayrollInputDataSource) {}
  employees(company: CompanyId, search: PayrollInputSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollInputEmployees(
        await this.source.employees(
          company,
          search.month,
          search.query,
          search.status,
          search.after,
          signal,
        ),
        search,
      ),
    );
  }
  review(
    company: CompanyId,
    employee: string,
    month: string,
    after: string | null,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toPayrollInputReview(
        await this.source.review(company, employee, month, after, signal),
        company,
        employee,
        month,
        after,
      ),
    );
  }
  revision(
    company: CompanyId,
    employee: string,
    month: string,
    revision: number,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const value = toPayrollInput(
        await this.source.revision(company, employee, month, revision, signal),
        company,
        employee,
        month,
      );
      if (value.version !== revision) throw new InvalidHttpResponseError();
      return value;
    });
  }
  save(
    company: CompanyId,
    employee: string,
    month: string,
    operation: OperationId,
    input: PayrollInputChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.save(
        company,
        employee,
        month,
        operation,
        toPayrollInputChangeDto(input),
        signal,
      );
      if (receipt.version !== (input.expectedVersion ?? -1) + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
  verify(
    company: CompanyId,
    employee: string,
    month: string,
    operation: OperationId,
    input: PayrollInputVerification,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.verify(
        company,
        employee,
        month,
        operation,
        { expectedVersion: input.expectedVersion, reason: input.reason },
        signal,
      );
      if (receipt.version !== input.expectedVersion + 1) throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
}
