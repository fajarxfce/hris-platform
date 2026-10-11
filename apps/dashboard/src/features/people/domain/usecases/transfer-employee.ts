import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmploymentTransferInput } from "../entities/employment-transfer-input";
import {
  canTransferEmployment,
  normalizeEmploymentTransfer,
} from "../policies/employment-transfer-policy";
import type { EmploymentTransferRepository } from "../repositories/employment-transfer-repository";

export class TransferEmployee {
  constructor(private readonly transfers: EmploymentTransferRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: EmploymentTransferInput,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canTransferEmployment(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_employment_transfer"));
    const normalized = normalizeEmploymentTransfer(access.companyId, input);
    if (!normalized.ok) return Promise.resolve(normalized);
    return this.transfers.transfer(access.companyId, operation, normalized.value, signal);
  }
}
