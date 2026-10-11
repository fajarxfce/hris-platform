import type { EmploymentChangeReceiptDto } from "../models/employment-change-dto";
import type {
  EmploymentTransferDto,
  EmploymentTransferInputDto,
  EmploymentTransferReviewDto,
} from "../models/employment-transfer-dto";

export interface EmploymentTransferDataSource {
  review(
    companyId: string,
    id: string,
    targetCompanyId: string,
    signal: AbortSignal,
  ): Promise<EmploymentTransferReviewDto>;
  history(companyId: string, id: string, signal: AbortSignal): Promise<EmploymentTransferDto[]>;
  transfer(
    companyId: string,
    id: string,
    operation: string,
    input: EmploymentTransferInputDto,
    signal: AbortSignal,
  ): Promise<EmploymentChangeReceiptDto>;
}
