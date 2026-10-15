import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type {
  PayrollAmendmentPublicationRecovery,
  PayrollAmendmentPublicationStart,
} from "../entities/payroll-amendment-publication-command";
import type { PayrollAmendmentPublicationDetails } from "../entities/payroll-amendment-publication-details";

export interface PayrollAmendmentPublicationRepository {
  list(
    company: CompanyId,
    amendment: string,
    signal: AbortSignal,
  ): Promise<Result<readonly PayrollAmendmentPublicationDetails[]>>;
  start(
    company: CompanyId,
    amendment: string,
    operation: OperationId,
    input: PayrollAmendmentPublicationStart,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  recover(
    company: CompanyId,
    amendment: string,
    publication: string,
    operation: OperationId,
    input: PayrollAmendmentPublicationRecovery,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
