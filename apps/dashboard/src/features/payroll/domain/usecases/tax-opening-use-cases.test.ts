import { describe, expect, it, vi } from "vitest";
import {
  openingCompany,
  openingEmployee,
  openingReview,
  openingTerms,
  openingYear,
} from "../../../../../tests/fixtures/tax-openings";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toTaxOpeningReview } from "../../data/mappers/tax-opening-mapper";
import type { TaxOpeningChange } from "../entities/tax-opening-change";
import { validateTaxOpeningTerms } from "../policies/tax-opening-validation";
import type { TaxOpeningRepository } from "../repositories/tax-opening-repository";
import { ListTaxOpeningEmployees } from "./list-tax-opening-employees";
import { LoadTaxOpeningReview } from "./load-tax-opening-review";
import { LoadTaxOpeningRevision } from "./load-tax-opening-revision";
import { ReviewTaxOpeningAction } from "./review-tax-opening-action";
import { SaveTaxOpening } from "./save-tax-opening";
import { VerifyTaxOpening } from "./verify-tax-opening";

const access: CompanyAccess = {
    companyId: openingCompany as CompanyId,
    permissions: ["payroll.calculate"],
  },
  operation = "ba900000-0000-4000-8000-000000000001" as OperationId,
  signal = new AbortController().signal;
const input = (): TaxOpeningChange => ({
  expectedEmploymentVersion: 7,
  expectedVersion: null,
  terms: openingTerms(),
  reason: "Reviewed input",
});
function fixture() {
  const review = toTaxOpeningReview(
    openingReview(),
    access.companyId,
    openingEmployee,
    openingYear,
    null,
  );
  return {
    review,
    repository: {
      employees: vi
        .fn<TaxOpeningRepository["employees"]>()
        .mockResolvedValue({ ok: true, value: { items: [], nextCursor: null } }),
      review: vi
        .fn<TaxOpeningRepository["review"]>()
        .mockResolvedValue({ ok: true, value: review }),
      revision: vi.fn<TaxOpeningRepository["revision"]>(),
      save: vi
        .fn<TaxOpeningRepository["save"]>()
        .mockResolvedValue({ ok: true, value: { id: openingEmployee, version: 0 } }),
      verify: vi
        .fn<TaxOpeningRepository["verify"]>()
        .mockResolvedValue({ ok: true, value: { id: openingEmployee, version: 1 } }),
    },
  };
}
describe("monthly input application policy", () => {
  it("keeps preparation, verification and unrelated administration separate", async () => {
    const { repository } = fixture(),
      read = new LoadTaxOpeningReview(repository),
      prepare = new SaveTaxOpening(repository),
      verify = new VerifyTaxOpening(repository);
    for (const permission of ["payroll.read", "payroll.calculate", "payroll.review"])
      expect(
        (
          await read.execute(
            { ...access, permissions: [permission] },
            openingEmployee,
            openingYear,
            null,
            signal,
          )
        ).ok,
      ).toBe(true);
    for (const permission of ["company.manage", "payroll.self.read", "payroll.policy.manage"])
      expect(
        await read.execute(
          { ...access, permissions: [permission] },
          openingEmployee,
          openingYear,
          null,
          signal,
        ),
      ).toEqual(failed("access_denied"));
    expect(
      await verify.execute(
        access,
        openingEmployee,
        openingYear,
        operation,
        { expectedVersion: 0, reason: "Review" },
        signal,
      ),
    ).toEqual(failed("access_denied"));
    expect(
      await prepare.execute(
        { ...access, permissions: ["payroll.review"] },
        openingEmployee,
        openingYear,
        operation,
        input(),
        signal,
      ),
    ).toEqual(failed("access_denied"));
    expect(repository.save).not.toHaveBeenCalled();
    expect(repository.verify).not.toHaveBeenCalled();
  });
  it("requires the server action hint after reviewing current scope and supports late cancellation", async () => {
    const { repository } = fixture(),
      review = new ReviewTaxOpeningAction(repository);
    expect((await review.execute(access, openingEmployee, openingYear, "PREPARE", signal)).ok).toBe(
      true,
    );
    expect(
      await review.execute(
        { ...access, permissions: ["payroll.review"] },
        openingEmployee,
        openingYear,
        "VERIFY",
        signal,
      ),
    ).toEqual(failed("payroll_tax_opening_action_unavailable"));
    const abort = new AbortController();
    repository.review.mockImplementation(async () => {
      abort.abort();
      return failed("access_denied");
    });
    await expect(
      review.execute(access, openingEmployee, openingYear, "PREPARE", abort.signal),
    ).rejects.toThrow();
  });
  it("rejects invalid identifiers, months, cursors and searches before I/O", async () => {
    const { repository } = fixture();
    expect(
      (
        await new ListTaxOpeningEmployees(repository).execute(
          access,
          { year: openingYear, query: "x".repeat(121), status: null, after: null },
          signal,
        )
      ).ok,
    ).toBe(false);
    expect(
      (
        await new LoadTaxOpeningReview(repository).execute(
          access,
          openingEmployee,
          "2101-01",
          null,
          signal,
        )
      ).ok,
    ).toBe(false);
    expect(
      (
        await new LoadTaxOpeningRevision(repository).execute(
          access,
          openingEmployee,
          openingYear,
          1000,
          signal,
        )
      ).ok,
    ).toBe(false);
    expect(
      (
        await new LoadTaxOpeningReview(repository).execute(
          access,
          "../outside",
          openingYear,
          null,
          signal,
        )
      ).ok,
    ).toBe(false);
    expect(repository.review).not.toHaveBeenCalled();
    expect(repository.revision).not.toHaveBeenCalled();
  });
  it("normalizes immutable annual amounts without changing the original fields", async () => {
    const { repository } = fixture(),
      change = input();
    const original = {
      ...change,
      reason: " Reviewed history ",
      terms: {
        ...change.terms,
        history: { ...change.terms.history, previousEmployerNet: " 600000000000.00 " },
      },
    };
    expect(
      (
        await new SaveTaxOpening(repository).execute(
          access,
          openingEmployee.toUpperCase(),
          openingYear,
          operation,
          original,
          signal,
        )
      ).ok,
    ).toBe(true);
    const saved = repository.save.mock.calls[0]?.[4];
    expect(saved?.terms.history.previousEmployerNet).toBe("600000000000.00");
    expect(saved?.reason).toBe("Reviewed history");
    expect(original.terms.history.previousEmployerNet).toBe(" 600000000000.00 ");
    expect(Object.isFrozen(saved?.terms.history)).toBe(true);
  });
  it.each(["", "-1", "1e6", "1,000", "600000000000.01", "0.001"])(
    "rejects invalid annual amount %s before transport",
    async (amount) => {
      const { repository } = fixture(),
        change = input();
      expect(
        (
          await new SaveTaxOpening(repository).execute(
            access,
            openingEmployee,
            openingYear,
            operation,
            {
              ...change,
              terms: {
                ...change.terms,
                history: { ...change.terms.history, previousEmployerNet: amount },
              },
            },
            signal,
          )
        ).ok,
      ).toBe(false);
      expect(repository.save).not.toHaveBeenCalled();
    },
  );
  it("requires consistent explicit totals, employment months and resident history", () => {
    const t = openingTerms();
    expect(
      validateTaxOpeningTerms(openingYear, { ...t, history: { ...t.history, employmentMonths: 0 } })
        ?.fields["terms.history"],
    ).toBe("inconsistent");
    expect(
      validateTaxOpeningTerms(openingYear, { ...t, history: { ...t.history, employmentMonths: 7 } })
        ?.fields["terms.history.employmentMonths"],
    ).toBe("out_of_range");
    expect(
      validateTaxOpeningTerms(openingYear, {
        ...t,
        history: { ...t.history, retirementContributions: "60000001" },
      })?.fields["terms.history"],
    ).toBe("inconsistent");
    expect(
      validateTaxOpeningTerms(openingYear, {
        ...t,
        residency: "NON_RESIDENT",
        history: { ...t.history, previousEmployerNet: "1" },
      })?.fields["terms.history"],
    ).toBe("non_resident_reconciliation_not_applicable");
    const zero = {
      ...t,
      throughMonth: 0,
      history: {
        taxableGross: "0",
        retirementContributions: "0",
        qualifiedDonations: "0",
        withheld: "0",
        employmentMonths: 0,
        previousEmployerNet: "0",
        previousEmployerWithheld: "0",
      },
    };
    expect(validateTaxOpeningTerms(openingYear, zero)).toBeNull();
    expect(
      validateTaxOpeningTerms(openingYear, {
        ...zero,
        history: { ...zero.history, previousEmployerNet: "1" },
      })?.fields["terms.history"],
    ).toBe("no_previous_month");
  });
});
