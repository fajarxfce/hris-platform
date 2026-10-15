import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type {
  PayrollAmendmentPublicationRecovery,
  PayrollAmendmentPublicationStart,
} from "../../domain/entities/payroll-amendment-publication-command";
import type { PayrollAmendmentPublicationRepository } from "../../domain/repositories/payroll-amendment-publication-repository";
import type { PayrollAmendmentPublicationDataSource } from "../datasources/payroll-amendment-publication-data-source";
import { toPayrollAmendmentReceipt } from "../mappers/payroll-amendment-command-mapper";
import { toPayrollAmendmentPublications } from "../mappers/payroll-amendment-publication-mapper";

export class RemotePayrollAmendmentPublicationRepository
  implements PayrollAmendmentPublicationRepository
{
  constructor(private readonly source: PayrollAmendmentPublicationDataSource) {}
  list(company: CompanyId, amendment: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollAmendmentPublications(
        await this.source.list(company, amendment, signal),
        company,
        amendment,
      ),
    );
  }
  start(
    company: CompanyId,
    amendment: string,
    operation: OperationId,
    input: PayrollAmendmentPublicationStart,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toPayrollAmendmentReceipt(
        await this.source.start(
          company,
          amendment,
          input.publicationId,
          operation,
          {
            expectedVersion: input.expectedVersion,
            reviewId: input.reviewId,
            expectedReviewVersion: input.expectedReviewVersion,
            expectedApprovalVersion: input.expectedApprovalVersion,
            calculationId: input.calculationId,
            reason: input.reason,
          },
          signal,
        ),
        amendment,
        input.expectedVersion,
      ),
    );
  }
  recover(
    company: CompanyId,
    amendment: string,
    publication: string,
    operation: OperationId,
    input: PayrollAmendmentPublicationRecovery,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toPayrollAmendmentReceipt(
        await this.source.recover(
          company,
          amendment,
          publication,
          operation,
          {
            expectedVersion: input.expectedVersion,
            expectedJobVersion: input.expectedJobVersion,
            reason: input.reason,
          },
          signal,
        ),
        amendment,
        input.expectedVersion,
      ),
    );
  }
}
