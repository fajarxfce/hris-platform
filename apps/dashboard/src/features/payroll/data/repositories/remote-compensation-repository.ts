import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { CompensationChange } from "../../domain/entities/compensation-change";
import type { CompensationRepository } from "../../domain/repositories/compensation-repository";
import type { CompensationDataSource } from "../datasources/compensation-data-source";
import {
  toCompensationChangeDto,
  toCompensationEmployees,
  toCompensationReview,
} from "../mappers/compensation-mapper";
export class RemoteCompensationRepository implements CompensationRepository {
  constructor(private readonly source: CompensationDataSource) {}
  employees(company: CompanyId, query: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toCompensationEmployees(await this.source.employees(company, query, after, signal), after),
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
      toCompensationReview(
        await this.source.review(company, employee, month, after, signal),
        company,
        employee,
        month,
        after,
      ),
    );
  }
  save(
    company: CompanyId,
    employee: string,
    operation: OperationId,
    input: CompensationChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.save(
        company,
        employee,
        operation,
        toCompensationChangeDto(input),
        signal,
      );
      if (
        receipt.id.toLowerCase() !== employee ||
        receipt.version !== (input.expectedVersion ?? -1) + 1
      )
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
}
