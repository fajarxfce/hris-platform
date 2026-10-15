import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendment } from "../entities/payroll-amendment";
import type {
  PayrollAmendmentEditorContext,
  PayrollAmendmentEditorTarget,
} from "../entities/payroll-amendment-preparation";
import {
  canAuthorPayrollAmendments,
  sameAmendmentSources,
} from "../policies/payroll-amendment-authoring-policy";
import type { PayrollAmendmentRepository } from "../repositories/payroll-amendment-repository";
import type { PayrollAmendmentSourceRepository } from "../repositories/payroll-amendment-source-repository";
export class LoadPayrollAmendmentEditor {
  constructor(
    private readonly amendments: PayrollAmendmentRepository,
    private readonly sources: PayrollAmendmentSourceRepository,
  ) {}
  async execute(
    access: CompanyAccess,
    target: PayrollAmendmentEditorTarget,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentEditorContext>> {
    signal.throwIfAborted();
    if (!canAuthorPayrollAmendments(access.permissions)) return failed("access_denied");
    let current: PayrollAmendment | null = null;
    let first: string;
    if (target.kind === "edit") {
      if (!isUuid(target.amendmentId)) return failed("payroll_amendment_not_found");
      const result = await this.amendments.get(
        access.companyId,
        target.amendmentId.toLowerCase(),
        signal,
      );
      signal.throwIfAborted();
      if (!result.ok) return result;
      if (!result.value.availableActions.includes("EDIT"))
        return failed("payroll_amendment_not_editable");
      current = result.value.amendment;
      first = current.summary.firstAssessmentId;
    } else {
      if (!isUuid(target.assessmentId)) return failed("payroll_assessment_not_found");
      first = target.assessmentId.toLowerCase();
    }
    const result = await this.sources.prepare(access.companyId, first, signal);
    signal.throwIfAborted();
    if (!result.ok) return result;
    return {
      ok: true,
      value: Object.freeze({
        preparation: result.value,
        current,
        sourcesChanged:
          current !== null &&
          !sameAmendmentSources(current.terms.sourceVersions, result.value.sourceVersions),
      }),
    };
  }
}
