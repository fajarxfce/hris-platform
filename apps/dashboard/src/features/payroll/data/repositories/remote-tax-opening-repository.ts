import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type {
  TaxOpeningChange,
  TaxOpeningVerification,
} from "../../domain/entities/tax-opening-change";
import type { TaxOpeningSearch } from "../../domain/entities/tax-opening-review";
import type { TaxOpeningRepository } from "../../domain/repositories/tax-opening-repository";
import type { TaxOpeningDataSource } from "../datasources/tax-opening-data-source";
import {
  toTaxOpening,
  toTaxOpeningChangeDto,
  toTaxOpeningEmployees,
  toTaxOpeningReview,
} from "../mappers/tax-opening-mapper";
export class RemoteTaxOpeningRepository implements TaxOpeningRepository {
  constructor(private readonly source: TaxOpeningDataSource) {}
  employees(company: CompanyId, search: TaxOpeningSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toTaxOpeningEmployees(
        await this.source.employees(
          company,
          search.year,
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
    year: string,
    after: string | null,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toTaxOpeningReview(
        await this.source.review(company, employee, year, after, signal),
        company,
        employee,
        year,
        after,
      ),
    );
  }
  revision(
    company: CompanyId,
    employee: string,
    year: string,
    revision: number,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const value = toTaxOpening(
        await this.source.revision(company, employee, year, revision, signal),
        company,
        employee,
        year,
      );
      if (value.version !== revision) throw new InvalidHttpResponseError();
      return value;
    });
  }
  save(
    company: CompanyId,
    employee: string,
    year: string,
    operation: OperationId,
    input: TaxOpeningChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.save(
        company,
        employee,
        year,
        operation,
        toTaxOpeningChangeDto(input),
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
    year: string,
    operation: OperationId,
    input: TaxOpeningVerification,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.verify(
        company,
        employee,
        year,
        operation,
        { expectedVersion: input.expectedVersion, reason: input.reason },
        signal,
      );
      if (receipt.version !== input.expectedVersion + 1) throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
}
