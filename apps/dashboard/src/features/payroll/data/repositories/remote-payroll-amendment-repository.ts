import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { PayrollAmendmentSave } from "../../domain/entities/payroll-amendment-preparation";
import type { PayrollAmendmentSearch } from "../../domain/entities/payroll-amendment-search";
import type { PayrollAmendmentRepository } from "../../domain/repositories/payroll-amendment-repository";
import type { PayrollAmendmentDataSource } from "../datasources/payroll-amendment-data-source";
import { toPayrollAmendmentCalculation } from "../mappers/payroll-amendment-calculation-mapper";
import {
  toPayrollAmendment,
  toPayrollAmendmentDetails,
  toPayrollAmendmentHistory,
  toPayrollAmendmentPage,
} from "../mappers/payroll-amendment-mapper";
import { toPayrollAmendmentTermsDto } from "../mappers/payroll-amendment-preparation-mapper";
export class RemotePayrollAmendmentRepository implements PayrollAmendmentRepository {
  constructor(private readonly source: PayrollAmendmentDataSource) {}
  save(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollAmendmentSave,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.save(
        company,
        id,
        operation,
        {
          ...toPayrollAmendmentTermsDto(input),
          firstAssessmentId: input.firstAssessmentId,
          expectedVersion: input.expectedVersion,
          reason: input.reason,
        },
        signal,
      );
      if (receipt.id.toLowerCase() !== id || receipt.version !== (input.expectedVersion ?? -1) + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: id, version: receipt.version });
    });
  }
  list(company: CompanyId, search: PayrollAmendmentSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollAmendmentPage(await this.source.list(company, search, signal), company, search),
    );
  }
  get(company: CompanyId, id: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollAmendmentDetails(await this.source.get(company, id, signal), company, id),
    );
  }
  history(company: CompanyId, id: string, after: number, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollAmendmentHistory(
        await this.source.history(company, id, after, signal),
        company,
        id,
        after,
      ),
    );
  }
  revision(company: CompanyId, id: string, version: number, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollAmendment(
        await this.source.revision(company, id, version, signal),
        company,
        id,
        version,
      ),
    );
  }
  calculation(company: CompanyId, id: string, calculationId: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollAmendmentCalculation(
        await this.source.calculation(company, id, calculationId, signal),
        company,
        id,
        calculationId,
      ),
    );
  }
}
