import { describe, expect, it, vi } from "vitest";
import {
  payrollCompany,
  payrollPolicyPage,
  payrollPolicyRecord,
  payrollRule,
} from "../../../../../tests/fixtures/payroll-policies";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollPolicyReview } from "../../data/mappers/payroll-policy-mapper";
import type { PayrollPolicyChange } from "../entities/payroll-policy-change";
import { payrollAmountMinor } from "../policies/payroll-policy-validation";
import type { PayrollPolicyRepository } from "../repositories/payroll-policy-repository";
import { LoadPayrollPolicy } from "./load-payroll-policy";
import { LoadPayrollPolicyEditor } from "./load-payroll-policy-editor";
import { SavePayrollPolicy } from "./save-payroll-policy";

const access: CompanyAccess = {
  companyId: payrollCompany as CompanyId,
  permissions: ["payroll.policy.manage"],
};
const operation = "c1000000-0000-4000-8000-000000000001" as OperationId;
const change: PayrollPolicyChange = {
  ...payrollPolicyRecord(0).current,
  expectedVersion: null,
  reason: "Annual policy review",
};
function fixture() {
  const review = toPayrollPolicyReview(
    payrollPolicyPage(payrollPolicyRecord()),
    access.companyId,
    "2026-06",
    null,
  );
  const repository = {
    review: vi
      .fn<PayrollPolicyRepository["review"]>()
      .mockResolvedValue({ ok: true, value: review }),
    rules: vi
      .fn<PayrollPolicyRepository["rules"]>()
      .mockResolvedValue({ ok: true, value: [payrollRule] }),
    save: vi
      .fn<PayrollPolicyRepository["save"]>()
      .mockResolvedValue({ ok: true, value: { id: payrollCompany, version: 0 } }),
  };
  return { repository, signal: new AbortController().signal };
}
describe("payroll policy application policy", () => {
  it("separates policy reads, compensation administration and policy writes", async () => {
    const { repository, signal } = fixture();
    const load = new LoadPayrollPolicy(repository),
      save = new SavePayrollPolicy(repository);
    for (const permission of ["payroll.read", "payroll.compensation.manage"])
      expect(
        (await load.execute({ ...access, permissions: [permission] }, "2026-06", null, signal)).ok,
      ).toBe(true);
    for (const permission of ["company.manage", "payroll.self.read", "payroll.calculate"])
      expect(
        await load.execute({ ...access, permissions: [permission] }, "2026-06", null, signal),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await save.execute({ ...access, permissions: ["payroll.read"] }, operation, change, signal),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(repository.review).toHaveBeenCalledTimes(2);
    expect(repository.save).not.toHaveBeenCalled();
  });
  it("rejects invalid months, cursors and cancellation before HTTP", async () => {
    const { repository, signal } = fixture(),
      load = new LoadPayrollPolicy(repository);
    for (const month of ["2023-12", "2101-01", "2026-13", "2026-1", "2026-01-01"])
      expect((await load.execute(access, month, null, signal)).ok).toBe(false);
    for (const after of ["-1", "1000", "01", "1e2", "0&limit=500"])
      expect((await load.execute(access, "2026-06", after, signal)).ok).toBe(false);
    expect(() => load.execute(access, "2026-06", null, AbortSignal.abort())).toThrow();
    expect(repository.review).not.toHaveBeenCalled();
  });
  it("loads the current head for editing even when the selected month uses another revision", async () => {
    const { repository, signal } = fixture();
    const result = await new LoadPayrollPolicyEditor(repository).execute(access, "2026-06", signal);
    expect(result).toMatchObject({
      ok: true,
      value: { current: { version: 23, appliedRevision: 23, effectiveFrom: "2027-01" } },
    });
    repository.review.mockResolvedValue(failed("mfa_required"));
    repository.rules.mockClear();
    expect(
      await new LoadPayrollPolicyEditor(repository).execute(access, "2026-06", signal),
    ).toMatchObject({ ok: false, failure: { code: "mfa_required" } });
    expect(repository.rules).not.toHaveBeenCalled();
  });
  it("stops editor acquisition when its owner is cancelled between repositories", async () => {
    const { repository } = fixture(),
      owner = new AbortController();
    repository.review.mockImplementation(async () => {
      owner.abort();
      return failed("unavailable");
    });
    await expect(
      new LoadPayrollPolicyEditor(repository).execute(access, "2026-06", owner.signal),
    ).rejects.toBeDefined();
    expect(repository.rules).not.toHaveBeenCalled();
  });
  it("preserves exact amounts and enforces the maximum without floating point", async () => {
    const { repository, signal } = fixture(),
      save = new SavePayrollPolicy(repository);
    expect(payrollAmountMinor("49999999999.99")).toBe(4_999_999_999_999n);
    expect(
      (
        await save.execute(
          access,
          operation,
          {
            ...change,
            minimumMonthlyWage: " 49999999999.99 ",
            healthWageCap: "50000000000.00",
            reason: " Annual policy review ",
          },
          signal,
        )
      ).ok,
    ).toBe(true);
    expect(repository.save).toHaveBeenCalledWith(
      access.companyId,
      operation,
      expect.objectContaining({
        minimumMonthlyWage: "49999999999.99",
        healthWageCap: "50000000000.00",
        reason: "Annual policy review",
      }),
      signal,
    );
    repository.save.mockClear();
    for (const minimumMonthlyWage of ["50000000000.01", "1e8", "1.001", "0", "-1", "1,000", "0001"])
      expect(
        (await save.execute(access, operation, { ...change, minimumMonthlyWage }, signal)).ok,
      ).toBe(false);
    expect(repository.save).not.toHaveBeenCalled();
  });
  it("rejects cross-year periods, low health caps, missing references and invalid revisions", async () => {
    const { repository, signal } = fixture(),
      save = new SavePayrollPolicy(repository);
    for (const update of [
      { effectiveUntil: "2028-01" },
      { healthWageCap: "100" },
      { reviewReferences: [] },
      { reviewReferences: ["javascript:alert(1)"] },
      { reviewReferences: Array.from({ length: 11 }, () => "https://example.test/reference") },
      { expectedVersion: 1000 },
      { reason: " " },
    ])
      expect((await save.execute(access, operation, { ...change, ...update }, signal)).ok).toBe(
        false,
      );
    expect(repository.save).not.toHaveBeenCalled();
  });
});
