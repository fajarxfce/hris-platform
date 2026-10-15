import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentPublications } from "../entities/payroll-amendment-publications";
import { canReadPayrollAmendments } from "../policies/payroll-amendment-access-policy";
import {
  amendmentPublicationStartFailure,
  canPublishPayrollAmendments,
} from "../policies/payroll-amendment-publication-policy";
import type { PayrollAmendmentPublicationRepository } from "../repositories/payroll-amendment-publication-repository";
import type { PayrollAmendmentRepository } from "../repositories/payroll-amendment-repository";

export class LoadPayrollAmendmentPublications {
  constructor(
    private readonly amendments: PayrollAmendmentRepository,
    private readonly publications: PayrollAmendmentPublicationRepository,
  ) {}
  async execute(
    access: CompanyAccess,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentPublications>> {
    signal.throwIfAborted();
    if (!canReadPayrollAmendments(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("payroll_amendment_not_found");
    const amendmentId = id.toLowerCase(),
      current = await this.amendments.get(access.companyId, amendmentId, signal);
    signal.throwIfAborted();
    if (!current.ok) return current;
    const attempts = await this.publications.list(access.companyId, amendmentId, signal);
    signal.throwIfAborted();
    if (!attempts.ok) return attempts;
    // A worker may finish after the first read. The bounded list owns its newer amendment snapshot.
    const amendment = attempts.value.at(-1)?.amendment ?? current.value.amendment.summary;
    if (
      amendment.version < current.value.amendment.summary.version ||
      amendment.employee.id !== current.value.amendment.summary.employee.id ||
      amendment.firstAssessmentId !== current.value.amendment.summary.firstAssessmentId ||
      amendment.firstMonth !== current.value.amendment.summary.firstMonth
    )
      return failed("payroll_amendment_publication_changed");
    if (attempts.value.length === 0 && amendment.latestPublicationId !== null)
      return failed("payroll_amendment_publication_changed");
    return success(
      Object.freeze({
        amendment,
        attempts: attempts.value,
        availableActions: Object.freeze(
          canPublishPayrollAmendments(access.permissions) &&
            !amendmentPublicationStartFailure(amendment, attempts.value)
            ? ["START" as const]
            : [],
        ),
      }),
    );
  }
}
