import { describe, expect, it, vi } from "vitest";
import {
  inputCompany,
  inputEmployee,
  inputJob,
  inputMonth,
  inputReview,
  inputTerms,
} from "../../../../../tests/fixtures/payroll-inputs";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollInputReview } from "../../data/mappers/payroll-input-mapper";
import type { PayrollInputChange } from "../entities/payroll-input-change";
import { validatePayrollInputTerms } from "../policies/payroll-input-validation";
import type { PayrollInputRepository } from "../repositories/payroll-input-repository";
import { ListPayrollInputEmployees } from "./list-payroll-input-employees";
import { LoadPayrollInputReview } from "./load-payroll-input-review";
import { LoadPayrollInputRevision } from "./load-payroll-input-revision";
import { ReviewPayrollInputAction } from "./review-payroll-input-action";
import { SavePayrollInput } from "./save-payroll-input";
import { VerifyPayrollInput } from "./verify-payroll-input";

const access: CompanyAccess = {
    companyId: inputCompany as CompanyId,
    permissions: ["payroll.calculate"],
  },
  operation = "ba900000-0000-4000-8000-000000000001" as OperationId,
  signal = new AbortController().signal;
const input = (): PayrollInputChange => ({
  workJobId: inputJob,
  workAdjustmentId: null,
  expectedWorkPeriodVersion: 2,
  expectedEmploymentVersion: 7,
  expectedVersion: null,
  terms: inputTerms(),
  reason: "Reviewed input",
});
function fixture() {
  const review = toPayrollInputReview(
    inputReview(),
    access.companyId,
    inputEmployee,
    inputMonth,
    null,
  );
  return {
    review,
    repository: {
      employees: vi
        .fn<PayrollInputRepository["employees"]>()
        .mockResolvedValue({ ok: true, value: { items: [], nextCursor: null } }),
      review: vi
        .fn<PayrollInputRepository["review"]>()
        .mockResolvedValue({ ok: true, value: review }),
      revision: vi.fn<PayrollInputRepository["revision"]>(),
      save: vi
        .fn<PayrollInputRepository["save"]>()
        .mockResolvedValue({ ok: true, value: { id: inputEmployee, version: 0 } }),
      verify: vi
        .fn<PayrollInputRepository["verify"]>()
        .mockResolvedValue({ ok: true, value: { id: inputEmployee, version: 1 } }),
    },
  };
}
describe("monthly input application policy", () => {
  it("keeps preparation, verification and unrelated administration separate", async () => {
    const { repository } = fixture(),
      read = new LoadPayrollInputReview(repository),
      prepare = new SavePayrollInput(repository),
      verify = new VerifyPayrollInput(repository);
    for (const permission of ["payroll.read", "payroll.calculate", "payroll.review"])
      expect(
        (
          await read.execute(
            { ...access, permissions: [permission] },
            inputEmployee,
            inputMonth,
            null,
            signal,
          )
        ).ok,
      ).toBe(true);
    for (const permission of ["company.manage", "payroll.self.read", "payroll.policy.manage"])
      expect(
        await read.execute(
          { ...access, permissions: [permission] },
          inputEmployee,
          inputMonth,
          null,
          signal,
        ),
      ).toEqual(failed("access_denied"));
    expect(
      await verify.execute(
        access,
        inputEmployee,
        inputMonth,
        operation,
        { expectedVersion: 0, reason: "Review" },
        signal,
      ),
    ).toEqual(failed("access_denied"));
    expect(
      await prepare.execute(
        { ...access, permissions: ["payroll.review"] },
        inputEmployee,
        inputMonth,
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
      review = new ReviewPayrollInputAction(repository);
    expect((await review.execute(access, inputEmployee, inputMonth, "PREPARE", signal)).ok).toBe(
      true,
    );
    expect(
      await review.execute(
        { ...access, permissions: ["payroll.review"] },
        inputEmployee,
        inputMonth,
        "VERIFY",
        signal,
      ),
    ).toEqual(failed("payroll_input_action_unavailable"));
    const abort = new AbortController();
    repository.review.mockImplementation(async () => {
      abort.abort();
      return failed("access_denied");
    });
    await expect(
      review.execute(access, inputEmployee, inputMonth, "PREPARE", abort.signal),
    ).rejects.toThrow();
  });
  it("rejects invalid identifiers, months, cursors and searches before I/O", async () => {
    const { repository } = fixture();
    expect(
      (
        await new ListPayrollInputEmployees(repository).execute(
          access,
          { month: inputMonth, query: "x".repeat(121), status: null, after: null },
          signal,
        )
      ).ok,
    ).toBe(false);
    expect(
      (
        await new LoadPayrollInputReview(repository).execute(
          access,
          inputEmployee,
          "2101-01",
          null,
          signal,
        )
      ).ok,
    ).toBe(false);
    expect(
      (
        await new LoadPayrollInputRevision(repository).execute(
          access,
          inputEmployee,
          inputMonth,
          1000,
          signal,
        )
      ).ok,
    ).toBe(false);
    expect(
      (
        await new LoadPayrollInputReview(repository).execute(
          access,
          "../outside",
          inputMonth,
          null,
          signal,
        )
      ).ok,
    ).toBe(false);
    expect(repository.review).not.toHaveBeenCalled();
    expect(repository.revision).not.toHaveBeenCalled();
  });
  it("normalizes an immutable exact-money command without mutating its fields", async () => {
    const { repository } = fixture(),
      save = new SavePayrollInput(repository),
      change = input();
    const source = {
      ...change,
      terms: {
        ...change.terms,
        earnings: [{ code: " bonus ", name: " Bonus ", amount: " 2000000.25 ", taxable: true }],
      },
      reason: " Reviewed input ",
    };
    expect(
      (
        await save.execute(
          access,
          inputEmployee.toUpperCase(),
          inputMonth,
          operation,
          source,
          signal,
        )
      ).ok,
    ).toBe(true);
    const saved = repository.save.mock.calls[0]?.[4];
    expect(saved?.terms.earnings[0]).toEqual({
      code: "BONUS",
      name: "Bonus",
      amount: "2000000.25",
      taxable: true,
    });
    expect(saved?.reason).toBe("Reviewed input");
    expect(source.terms.earnings[0]?.code).toBe(" bonus ");
    expect(Object.isFrozen(saved?.terms.earnings[0])).toBe(true);
  });
  it.each(["0", "-1", "1e6", "1,000", "50000000000.01", "0.001"])(
    "rejects invalid positive amount %s",
    async (amount) => {
      const { repository } = fixture(),
        change = input();
      const result = await new SavePayrollInput(repository).execute(
        access,
        inputEmployee,
        inputMonth,
        operation,
        {
          ...change,
          terms: {
            ...change.terms,
            earnings: [{ code: "PAY", name: "Pay", amount, taxable: true }],
          },
        },
        signal,
      );
      expect(result.ok).toBe(false);
      expect(repository.save).not.toHaveBeenCalled();
    },
  );
  it("enforces totals, half-day overlap, valid dates and explicit noncash values", () => {
    const t = inputTerms();
    expect(
      validatePayrollInputTerms(inputMonth, {
        ...t,
        earnings: [{ code: "A", name: "A", amount: "50000000000", taxable: true }],
        nonCashTaxable: "0.01",
      })?.code,
    ).toBe("payroll_amount_limit");
    expect(
      validatePayrollInputTerms(inputMonth, {
        ...t,
        dayResolutions: [
          { workDate: "2026-09-02", portion: "FULL", disposition: "PAID", reference: "Review" },
          ...t.dayResolutions,
        ],
      })?.fields["terms.dayResolutions"],
    ).toBe("overlap");
    expect(
      validatePayrollInputTerms(inputMonth, { ...t, nonCashTaxable: "" })?.fields[
        "terms.nonCashTaxable"
      ],
    ).toBe("out_of_range");
    expect(
      validatePayrollInputTerms(inputMonth, { ...t, scheduledMonthUnits: "31.5" }),
    ).not.toBeNull();
    expect(
      validatePayrollInputTerms(inputMonth, {
        ...t,
        dayResolutions: [
          { workDate: "2026-09-31", portion: "FULL", disposition: "UNPAID", reference: "Review" },
        ],
      }),
    ).not.toBeNull();
  });
  it("accepts supported holiday evidence and rejects fractional promises or invalid service dates", () => {
    const t = inputTerms(),
      holiday = {
        kind: "EID_FITR" as const,
        holidayDate: "2026-03-20",
        continuousServiceFrom: "2025-01-01",
        priorPayment: "NOT_PAID" as const,
        reviewReference: "Reviewed holiday",
        promisedAmount: "2500000.00",
      };
    const terms = { ...t, dayResolutions: [], holidayAllowance: holiday };
    expect(validatePayrollInputTerms("2026-03", terms)).toBeNull();
    expect(
      validatePayrollInputTerms("2026-03", {
        ...terms,
        holidayAllowance: { ...holiday, promisedAmount: "1.50" },
      }),
    ).not.toBeNull();
    expect(
      validatePayrollInputTerms("2026-03", {
        ...terms,
        holidayAllowance: { ...holiday, continuousServiceFrom: holiday.holidayDate },
      }),
    ).not.toBeNull();
    expect(validatePayrollInputTerms(inputMonth, terms)).not.toBeNull();
  });
});
