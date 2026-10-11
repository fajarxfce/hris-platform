import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../domain/entities/employee";
import type { EmploymentTransferInput } from "../../domain/entities/employment-transfer-input";
import type { EmploymentTransferRepository } from "../../domain/repositories/employment-transfer-repository";
import type { EmploymentTransferDataSource } from "../datasources/employment-transfer-data-source";
import {
  toEmploymentTransferInputDto,
  toEmploymentTransferReceipt,
  toEmploymentTransferReview,
  toEmploymentTransfers,
} from "../mappers/employment-transfer-mapper";

export class RemoteEmploymentTransferRepository implements EmploymentTransferRepository {
  constructor(private readonly source: EmploymentTransferDataSource) {}
  review(companyId: CompanyId, id: EmployeeId, targetCompanyId: CompanyId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toEmploymentTransferReview(
        await this.source.review(companyId, id, targetCompanyId, signal),
        companyId,
        id,
        targetCompanyId,
      ),
    );
  }
  history(companyId: CompanyId, id: EmployeeId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toEmploymentTransfers(await this.source.history(companyId, id, signal), companyId, id),
    );
  }
  transfer(
    companyId: CompanyId,
    operation: OperationId,
    input: EmploymentTransferInput,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toEmploymentTransferReceipt(
        await this.source.transfer(
          companyId,
          input.employeeId,
          operation,
          toEmploymentTransferInputDto(input),
          signal,
        ),
        input,
      ),
    );
  }
}
