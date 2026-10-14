import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { CompensationChange } from "../entities/compensation-change";
import { canChangeCompensation } from "../policies/compensation-access-policy";
import { normalizeCompensation, validateCompensation } from "../policies/compensation-validation";
import type { CompensationRepository } from "../repositories/compensation-repository";

export class SaveCompensation {
  constructor(private readonly compensations: CompensationRepository) {}
  execute(
    access: CompanyAccess,
    employee: string,
    operation: OperationId,
    input: CompensationChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canChangeCompensation(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(employee) || !isUuid(operation))
      return Promise.resolve(failed("invalid_compensation"));
    const change = normalizeCompensation(input);
    const failure = validateCompensation(change);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.compensations.save(
      access.companyId,
      employee.toLowerCase(),
      operation,
      change,
      signal,
    );
  }
}
