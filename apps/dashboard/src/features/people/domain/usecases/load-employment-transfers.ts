import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../entities/employee";
import type { EmploymentTransferRepository } from "../repositories/employment-transfer-repository";

export class LoadEmploymentTransfers {
  constructor(private readonly transfers: EmploymentTransferRepository) {}
  execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!access.permissions.includes("people.read"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("employee_not_found"));
    return this.transfers.history(access.companyId, id.toLowerCase() as EmployeeId, signal);
  }
}
