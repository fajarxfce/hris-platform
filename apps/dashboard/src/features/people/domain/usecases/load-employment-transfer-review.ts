import { type CompanyId, isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../entities/employee";
import { canTransferEmployment } from "../policies/employment-transfer-policy";
import type { EmploymentTransferRepository } from "../repositories/employment-transfer-repository";

export class LoadEmploymentTransferReview {
  constructor(private readonly transfers: EmploymentTransferRepository) {}
  execute(access: CompanyAccess, id: string, targetCompanyId: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canTransferEmployment(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (
      !isUuid(id) ||
      !isUuid(targetCompanyId) ||
      targetCompanyId.toLowerCase() === access.companyId
    )
      return Promise.resolve(failed("invalid_employment_transfer"));
    return this.transfers.review(
      access.companyId,
      id.toLowerCase() as EmployeeId,
      targetCompanyId.toLowerCase() as CompanyId,
      signal,
    );
  }
}
