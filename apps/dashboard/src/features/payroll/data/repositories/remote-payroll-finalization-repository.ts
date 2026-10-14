import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { PayrollFinalizationStart } from "../../domain/entities/payroll-finalization-command";
import type { PayrollPublicationSearch } from "../../domain/entities/payroll-publication-search";
import type { PayrollFinalizationRepository } from "../../domain/repositories/payroll-finalization-repository";
import type { PayrollFinalizationDataSource } from "../datasources/payroll-finalization-data-source";
import {
  toPayrollFinalizationDetails,
  toPayrollFinalizationPreparation,
  toPayrollFinalizations,
  toPayrollPublicationPage,
} from "../mappers/payroll-finalization-mapper";
export class RemotePayrollFinalizationRepository implements PayrollFinalizationRepository {
  constructor(private readonly source: PayrollFinalizationDataSource) {}
  runs(company: CompanyId, search: PayrollPublicationSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollPublicationPage(await this.source.runs(company, search, signal), company, search),
    );
  }
  prepare(company: CompanyId, run: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollFinalizationPreparation(
        await this.source.prepare(company, run, signal),
        company,
        run,
      ),
    );
  }
  list(company: CompanyId, run: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollFinalizations(await this.source.list(company, run, signal), company, run),
    );
  }
  get(company: CompanyId, id: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollFinalizationDetails(await this.source.get(company, id, signal), company, id),
    );
  }
  start(
    company: CompanyId,
    run: string,
    operation: OperationId,
    input: PayrollFinalizationStart,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.start(company, run, operation, input, signal);
      if (receipt.id.toLowerCase() !== input.id || receipt.version !== 0)
        throw new InvalidHttpResponseError();
      return Object.freeze({ ...receipt, id: receipt.id.toLowerCase() });
    });
  }
}
