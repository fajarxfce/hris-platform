import type { PayrollAmendmentReceiptDto } from "../models/payroll-amendment-command-dto";
import type {
  PayrollAmendmentPublicationDetailsDto,
  PayrollAmendmentPublicationRecoveryDto,
  PayrollAmendmentPublicationStartDto,
} from "../models/payroll-amendment-publication-dto";

export interface PayrollAmendmentPublicationDataSource {
  list(
    company: string,
    amendment: string,
    signal: AbortSignal,
  ): Promise<readonly PayrollAmendmentPublicationDetailsDto[]>;
  start(
    company: string,
    amendment: string,
    id: string,
    operation: string,
    input: PayrollAmendmentPublicationStartDto,
    signal: AbortSignal,
  ): Promise<PayrollAmendmentReceiptDto>;
  recover(
    company: string,
    amendment: string,
    id: string,
    operation: string,
    input: PayrollAmendmentPublicationRecoveryDto,
    signal: AbortSignal,
  ): Promise<PayrollAmendmentReceiptDto>;
}
