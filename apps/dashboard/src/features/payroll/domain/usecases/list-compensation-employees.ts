import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadCompensation } from "../policies/compensation-access-policy";
import type { CompensationRepository } from "../repositories/compensation-repository";

export class ListCompensationEmployees {
  constructor(private readonly compensations: CompensationRepository) {}
  execute(access: CompanyAccess, query: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadCompensation(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (query.length > 120 || (after !== null && (after.trim().length === 0 || after.length > 32)))
      return Promise.resolve(failed("invalid_page"));
    return this.compensations.employees(access.companyId, query.trim(), after, signal);
  }
}
