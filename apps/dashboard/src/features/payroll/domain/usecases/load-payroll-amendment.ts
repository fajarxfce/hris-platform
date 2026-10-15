import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendment } from "../entities/payroll-amendment";
import type { PayrollAmendmentCalculation } from "../entities/payroll-amendment-calculation";
import type {
  PayrollAmendmentInspection,
  PayrollAmendmentSelection,
} from "../entities/payroll-amendment-search";
import {
  canReadPayrollAmendments,
  validPayrollAmendmentRevision,
} from "../policies/payroll-amendment-access-policy";
import type { PayrollAmendmentRepository } from "../repositories/payroll-amendment-repository";
export class LoadPayrollAmendment {
  constructor(private readonly amendments: PayrollAmendmentRepository) {}
  async execute(
    access: CompanyAccess,
    amendmentId: string,
    selection: PayrollAmendmentSelection,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentInspection>> {
    signal.throwIfAborted();
    if (!canReadPayrollAmendments(access.permissions)) return failed("access_denied");
    if (!isUuid(amendmentId)) return failed("payroll_amendment_not_found");
    if (
      !validPayrollAmendmentRevision(selection.revision) ||
      !validPayrollAmendmentRevision(selection.after)
    )
      return failed("invalid_page");
    const id = amendmentId.toLowerCase();
    const loaded = await this.amendments.get(access.companyId, id, signal);
    signal.throwIfAborted();
    if (!loaded.ok) return loaded;
    const current = loaded.value;
    let selected: PayrollAmendment = current.amendment;
    if (selection.revision !== null && selection.revision !== selected.summary.version) {
      if (selection.revision > selected.summary.version)
        return failed("payroll_amendment_not_found");
      const revision = await this.amendments.revision(
        access.companyId,
        id,
        selection.revision,
        signal,
      );
      signal.throwIfAborted();
      if (!revision.ok) return revision;
      selected = revision.value;
    }
    let history = current.history;
    if (selection.after !== null) {
      const page = await this.amendments.history(access.companyId, id, selection.after, signal);
      signal.throwIfAborted();
      if (!page.ok) return page;
      // Later immutable revisions do not replace the current aggregate observed by this load.
      history = Object.freeze({
        items: Object.freeze(
          page.value.items.filter((row) => row.version <= current.amendment.summary.version),
        ),
        nextCursor:
          page.value.nextCursor !== null &&
          Number(page.value.nextCursor) < current.amendment.summary.version
            ? page.value.nextCursor
            : null,
      });
    }
    let calculation: PayrollAmendmentCalculation | null = null;
    if (selected.summary.latestCalculationId !== null) {
      const attempt = await this.amendments.calculation(
        access.companyId,
        id,
        selected.summary.latestCalculationId,
        signal,
      );
      signal.throwIfAborted();
      if (!attempt.ok) return attempt;
      calculation = attempt.value;
    }
    return { ok: true, value: Object.freeze({ current, selected, history, calculation }) };
  }
}
