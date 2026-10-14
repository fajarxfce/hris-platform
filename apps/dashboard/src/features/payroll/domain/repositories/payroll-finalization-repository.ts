import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { PayrollFinalization } from "../entities/payroll-finalization";
import type { PayrollFinalizationStart } from "../entities/payroll-finalization-command";
import type { PayrollFinalizationDetails } from "../entities/payroll-finalization-details";
import type { PayrollFinalizationPreparation } from "../entities/payroll-finalization-preparation";
import type {
  PayrollPublicationPage,
  PayrollPublicationSearch,
} from "../entities/payroll-publication-search";
export interface PayrollFinalizationRepository {
  runs(
    company: CompanyId,
    search: PayrollPublicationSearch,
    signal: AbortSignal,
  ): Promise<Result<PayrollPublicationPage>>;
  prepare(
    company: CompanyId,
    run: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollFinalizationPreparation>>;
  list(
    company: CompanyId,
    run: string,
    signal: AbortSignal,
  ): Promise<Result<readonly PayrollFinalization[]>>;
  get(
    company: CompanyId,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollFinalizationDetails>>;
  start(
    company: CompanyId,
    run: string,
    operation: OperationId,
    input: PayrollFinalizationStart,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
