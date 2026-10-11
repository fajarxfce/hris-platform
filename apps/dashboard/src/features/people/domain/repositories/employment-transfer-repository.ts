import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { EmployeeId } from "../entities/employee";
import type { EmploymentTransfer } from "../entities/employment-transfer";
import type { EmploymentTransferInput } from "../entities/employment-transfer-input";
import type { EmploymentTransferReview } from "../entities/employment-transfer-review";

export interface EmploymentTransferRepository {
  review(
    companyId: CompanyId,
    id: EmployeeId,
    targetCompanyId: CompanyId,
    signal: AbortSignal,
  ): Promise<Result<EmploymentTransferReview>>;
  history(
    companyId: CompanyId,
    id: EmployeeId,
    signal: AbortSignal,
  ): Promise<Result<readonly EmploymentTransfer[]>>;
  transfer(
    companyId: CompanyId,
    operation: OperationId,
    input: EmploymentTransferInput,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
