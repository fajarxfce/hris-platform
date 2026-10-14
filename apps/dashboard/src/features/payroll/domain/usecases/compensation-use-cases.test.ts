import { assert, describe, expect, it, vi } from "vitest";
import {
  compensationCompany,
  compensationEmployee,
  compensationPage,
  compensationRecord,
  compensationTerms,
} from "../../../../../tests/fixtures/compensations";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toCompensationReview } from "../../data/mappers/compensation-mapper";
import type { CompensationChange } from "../entities/compensation-change";
import type { CompensationRepository } from "../repositories/compensation-repository";
import { ListCompensationEmployees } from "./list-compensation-employees";
import { LoadCompensationEditor } from "./load-compensation-editor";
import { LoadCompensationReview } from "./load-compensation-review";
import { SaveCompensation } from "./save-compensation";

const access: CompanyAccess = {
  companyId: compensationCompany as CompanyId,
  permissions: ["payroll.compensation.manage"],
};
const operation = "ca200000-0000-4000-8000-000000000001" as OperationId;
function input(): CompensationChange {
  return {
    effectiveFrom: "2026-06",
    terms: compensationTerms(),
    expectedVersion: null,
    expectedEmploymentVersion: 7,
    reason: "Reviewed employee compensation",
  };
}
function fixture() {
  const review = toCompensationReview(
    compensationPage(compensationRecord()),
    access.companyId,
    compensationEmployee,
    "2026-06",
    null,
  );
  const repository = {
    employees: vi
      .fn<CompensationRepository["employees"]>()
      .mockResolvedValue({ ok: true, value: { items: [review.employee], nextCursor: null } }),
    review: vi
      .fn<CompensationRepository["review"]>()
      .mockResolvedValue({ ok: true, value: review }),
    save: vi
      .fn<CompensationRepository["save"]>()
      .mockResolvedValue({ ok: true, value: { id: compensationEmployee, version: 0 } }),
  };
  return { repository, signal: new AbortController().signal, review };
}
describe("compensation application policy", () => {
  it("separates company administration, reading and independent salary changes", async () => {
    const { repository, signal } = fixture(),
      read = new LoadCompensationReview(repository),
      editor = new LoadCompensationEditor(repository),
      save = new SaveCompensation(repository);
    for (const permission of ["payroll.read", "payroll.compensation.manage"])
      expect(
        (
          await read.execute(
            { ...access, permissions: [permission] },
            compensationEmployee,
            "2026-06",
            null,
            signal,
          )
        ).ok,
      ).toBe(true);
    for (const permission of ["company.manage", "payroll.policy.manage", "payroll.self.read"])
      expect(
        await read.execute(
          { ...access, permissions: [permission] },
          compensationEmployee,
          "2026-06",
          null,
          signal,
        ),
      ).toEqual(failed("access_denied"));
    expect(
      await editor.execute(
        { ...access, permissions: ["payroll.read"] },
        compensationEmployee,
        "2026-06",
        signal,
      ),
    ).toEqual(failed("access_denied"));
    expect(
      await save.execute(
        { ...access, permissions: ["payroll.read"] },
        compensationEmployee,
        operation,
        input(),
        signal,
      ),
    ).toEqual(failed("access_denied"));
    expect(repository.save).not.toHaveBeenCalled();
  });
  it("finds employees without a salary month and rejects invalid scope, paging and month before I/O", async () => {
    const { repository, signal } = fixture();
    expect(
      (
        await new ListCompensationEmployees(repository).execute(
          access,
          "  Rani_20%  ",
          null,
          signal,
        )
      ).ok,
    ).toBe(true);
    expect(repository.employees).toHaveBeenCalledWith(access.companyId, "Rani_20%", null, signal);
    expect(
      await new ListCompensationEmployees(repository).execute(
        access,
        "x".repeat(121),
        null,
        signal,
      ),
    ).toEqual(failed("invalid_page"));
    const read = new LoadCompensationReview(repository);
    expect(await read.execute(access, "../other", "2026-06", null, signal)).toEqual(
      failed("employee_not_found"),
    );
    expect(await read.execute(access, compensationEmployee, "2023-12", null, signal)).toEqual(
      failed("invalid_payroll_month"),
    );
    expect(await read.execute(access, compensationEmployee, "2026-06", "1000", signal)).toEqual(
      failed("invalid_page"),
    );
    expect(repository.review).not.toHaveBeenCalled();
  });
  it("checks the server's current independent-change capability and preserves read failures", async () => {
    const { repository, signal, review } = fixture();
    repository.review
      .mockResolvedValueOnce({ ok: true, value: { ...review, canChange: false } })
      .mockResolvedValueOnce(failed("mfa_required"));
    const editor = new LoadCompensationEditor(repository);
    expect(await editor.execute(access, compensationEmployee, "2026-06", signal)).toEqual(
      failed("compensation_change_unavailable"),
    );
    expect(await editor.execute(access, compensationEmployee, "2026-06", signal)).toEqual(
      failed("mfa_required"),
    );
  });
  it("does not release editor context after cancellation during acquisition", async () => {
    const { repository, review } = fixture(),
      abort = new AbortController();
    repository.review.mockImplementation(async () => {
      abort.abort();
      return { ok: true, value: review };
    });
    await expect(
      new LoadCompensationEditor(repository).execute(
        access,
        compensationEmployee,
        "2026-06",
        abort.signal,
      ),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
  it("owns nested normalized input and keeps decimals and both observed versions", async () => {
    const { repository, signal } = fixture(),
      change = input(),
      terms = compensationTerms();
    terms.fixedEarnings[0] = { code: " meal ", name: " Meal ", amount: " 0.01 ", taxable: false };
    terms.tax.residenceCountry = " id ";
    const source = {
      ...change,
      terms: { ...terms, basicSalary: "49999999999.99", insuranceWage: "1.00" },
    };
    expect(
      (
        await new SaveCompensation(repository).execute(
          access,
          compensationEmployee,
          operation,
          source,
          signal,
        )
      ).ok,
    ).toBe(true);
    const sent = repository.save.mock.calls[0]?.[3];
    assert(sent);
    expect(sent).toMatchObject({
      expectedVersion: null,
      expectedEmploymentVersion: 7,
      terms: {
        basicSalary: "49999999999.99",
        fixedEarnings: [{ code: "MEAL", amount: "0.01" }],
        tax: { residenceCountry: "ID" },
      },
    });
    source.terms.tax.residenceCountry = "US";
    terms.fixedEarnings.length = 0;
    expect(sent.terms.tax.residenceCountry).toBe("ID");
    expect(sent.terms.fixedEarnings).toHaveLength(1);
    expect(Object.isFrozen(sent.terms.tax)).toBe(true);
  });
  it.each([
    [
      "decimal precision",
      (v: CompensationChange) => ({ ...v, terms: { ...v.terms, basicSalary: "10.001" } }),
    ],
    [
      "oversized total",
      (v: CompensationChange) => ({ ...v, terms: { ...v.terms, basicSalary: "50000000000.00" } }),
    ],
    [
      "deductions exceed earnings",
      (v: CompensationChange) => ({
        ...v,
        terms: { ...v.terms, otherNetDeduction: "20000000.00" },
      }),
    ],
    [
      "unreviewed exemption",
      (v: CompensationChange) => ({ ...v, terms: { ...v.terms, insurancePrograms: [] } }),
    ],
    [
      "duplicate allowance",
      (v: CompensationChange) => ({
        ...v,
        terms: { ...v.terms, fixedEarnings: [...v.terms.fixedEarnings, ...v.terms.fixedEarnings] },
      }),
    ],
    [
      "country and residency",
      (v: CompensationChange) => ({
        ...v,
        terms: { ...v.terms, tax: { ...v.terms.tax, residenceCountry: "US" } },
      }),
    ],
    [
      "impossible verification date",
      (v: CompensationChange) => ({
        ...v,
        terms: { ...v.terms, tax: { ...v.terms.tax, verifiedOn: "2026-02-30" } },
      }),
    ],
    [
      "blank evidence",
      (v: CompensationChange) => ({
        ...v,
        terms: { ...v.terms, tax: { ...v.terms.tax, verificationReference: " " } },
      }),
    ],
    [
      "non-finite employee version",
      (v: CompensationChange) => ({ ...v, expectedEmploymentVersion: Number.MAX_SAFE_INTEGER + 1 }),
    ],
  ])("rejects %s before a command", async (_, change) => {
    const { repository, signal } = fixture();
    expect(
      (
        await new SaveCompensation(repository).execute(
          access,
          compensationEmployee,
          operation,
          change(input()),
          signal,
        )
      ).ok,
    ).toBe(false);
    expect(repository.save).not.toHaveBeenCalled();
  });
});
