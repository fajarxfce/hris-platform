import type {
  PayrollFinalizationDetailsDto,
  PayrollFinalizationPreparationDto,
  PayrollFinalizationReceiptDto,
  PayrollFinalizationStartDto,
  PayrollFinalizationsDto,
  PayrollPublicationPageDto,
  PayrollPublicationSearchDto,
} from "../models/payroll-finalization-dto";
export interface PayrollFinalizationDataSource {
  runs(
    company: string,
    search: PayrollPublicationSearchDto,
    signal: AbortSignal,
  ): Promise<PayrollPublicationPageDto>;
  prepare(
    company: string,
    run: string,
    signal: AbortSignal,
  ): Promise<PayrollFinalizationPreparationDto>;
  list(company: string, run: string, signal: AbortSignal): Promise<PayrollFinalizationsDto>;
  get(company: string, id: string, signal: AbortSignal): Promise<PayrollFinalizationDetailsDto>;
  start(
    company: string,
    run: string,
    operation: string,
    input: PayrollFinalizationStartDto,
    signal: AbortSignal,
  ): Promise<PayrollFinalizationReceiptDto>;
}
