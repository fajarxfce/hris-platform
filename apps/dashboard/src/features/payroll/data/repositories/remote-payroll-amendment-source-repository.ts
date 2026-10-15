import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollAmendmentTerms } from "../../domain/entities/payroll-amendment";
import type { PayrollAmendmentSourceSearch } from "../../domain/entities/payroll-amendment-preparation";
import { sameAmendmentSources } from "../../domain/policies/payroll-amendment-authoring-policy";
import type { PayrollAmendmentSourceRepository } from "../../domain/repositories/payroll-amendment-source-repository";
import type { PayrollAmendmentSourceDataSource } from "../datasources/payroll-amendment-source-data-source";
import { toPayrollAmendmentPreview } from "../mappers/payroll-amendment-calculation-mapper";
import {
  toPayrollAmendmentCandidates,
  toPayrollAmendmentPreparation,
  toPayrollAmendmentTermsDto,
} from "../mappers/payroll-amendment-preparation-mapper";
export class RemotePayrollAmendmentSourceRepository implements PayrollAmendmentSourceRepository {
  constructor(private readonly source: PayrollAmendmentSourceDataSource) {}
  list(company: CompanyId, search: PayrollAmendmentSourceSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollAmendmentCandidates(
        await this.source.list(company, search, signal),
        company,
        search,
      ),
    );
  }
  prepare(company: CompanyId, assessment: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollAmendmentPreparation(
        await this.source.prepare(company, assessment, signal),
        company,
        assessment,
      ),
    );
  }
  preview(
    company: CompanyId,
    assessment: string,
    terms: PayrollAmendmentTerms,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const preview = toPayrollAmendmentPreview(
        await this.source.preview(company, assessment, toPayrollAmendmentTermsDto(terms), signal),
      );
      if (
        preview.firstAssessmentId !== assessment ||
        !sameAmendmentSources(preview.sourceVersions, terms.sourceVersions)
      )
        throw new InvalidHttpResponseError();
      return preview;
    });
  }
}
