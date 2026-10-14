import { describe, expect, it, vi } from "vitest";
import {
  periodCompany,
  periodCreation,
  periodDetails,
  periodEmployee,
  periodId,
} from "../../../../../tests/fixtures/payroll-periods";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollPeriodDetails } from "../../data/mappers/payroll-period-mapper";
import {
  validatePayrollPeriodCreation,
  validatePayrollPeriodFields,
  validPayrollPeriodSearch,
} from "../policies/payroll-period-policy";
import type { PayrollPeriodRepository } from "../repositories/payroll-period-repository";
import { CancelPayrollPeriod } from "./cancel-payroll-period";
import { CreatePayrollPeriod } from "./create-payroll-period";
import { ListPayrollPeriodEmployees } from "./list-payroll-period-employees";
import { ListPayrollPeriods } from "./list-payroll-periods";
import { LoadPayrollPeriod } from "./load-payroll-period";
import { ReviewPayrollPeriodCancellation } from "./review-payroll-period-cancellation";
import { ReviewPayrollPeriodCreation } from "./review-payroll-period-creation";

const access: CompanyAccess = {
    companyId: periodCompany as CompanyId,
    permissions: ["payroll.calculate"],
  },
  operation = "ea300000-0000-4000-8000-000000000001" as OperationId;
const search = { from: "2026-01", until: "2026-12", status: null, after: null };
function fixture() {
  const record = toPayrollPeriodDetails(periodDetails(), access.companyId, periodId, null, null);
  return {
    signal: new AbortController().signal,
    record,
    repository: {
      list: vi
        .fn<PayrollPeriodRepository["list"]>()
        .mockResolvedValue(success({ items: [record.period], nextCursor: null })),
      details: vi.fn<PayrollPeriodRepository["details"]>().mockResolvedValue(success(record)),
      employees: vi
        .fn<PayrollPeriodRepository["employees"]>()
        .mockResolvedValue(success({ items: [periodEmployee()], nextCursor: null })),
      create: vi
        .fn<PayrollPeriodRepository["create"]>()
        .mockResolvedValue(success({ id: periodId, version: 0 })),
      cancel: vi
        .fn<PayrollPeriodRepository["cancel"]>()
        .mockResolvedValue(success({ id: periodId, version: 1 })),
    },
  };
}
describe("payroll period policy", () => {
  it("separates reading and preparation from generic company, salary and self-service authority", async () => {
    const { repository, signal } = fixture();
    for (const permission of ["payroll.read", "payroll.calculate", "payroll.review"]) {
      const actor = { ...access, permissions: [permission] };
      expect((await new ListPayrollPeriods(repository).execute(actor, search, signal)).ok).toBe(
        true,
      );
      expect(
        (await new ListPayrollPeriodEmployees(repository).execute(actor, "", null, signal)).ok,
      ).toBe(true);
      expect(
        (await new LoadPayrollPeriod(repository).execute(actor, periodId, null, null, signal)).ok,
      ).toBe(true);
      expect(
        (
          await new CreatePayrollPeriod(repository).execute(
            actor,
            operation,
            periodCreation(),
            signal,
          )
        ).ok,
      ).toBe(permission === "payroll.calculate");
    }
    for (const permission of [
      "company.manage",
      "payroll.compensation.manage",
      "payroll.self.read",
    ]) {
      const actor = { ...access, permissions: [permission] };
      expect(await new ListPayrollPeriods(repository).execute(actor, search, signal)).toMatchObject(
        { ok: false, failure: { code: "access_denied" } },
      );
      expect(new ReviewPayrollPeriodCreation().execute(actor, periodCreation())).toMatchObject({
        ok: false,
        failure: { code: "access_denied" },
      });
    }
  });
  it("bounds calendar months and actual planned dates including leap years", () => {
    expect(
      validatePayrollPeriodFields({ ...periodCreation(), plannedPaymentDate: "2026-12-01" }),
    ).toBeNull();
    for (const plannedPaymentDate of ["2026-08-31", "2026-12-02", "2026-02-30", "2101-01-01"])
      expect(
        validatePayrollPeriodFields({ ...periodCreation(), plannedPaymentDate })?.fields,
      ).toHaveProperty("plannedPaymentDate");
    expect(
      validatePayrollPeriodFields({
        ...periodCreation(),
        earningsMonth: "2024-02",
        plannedPaymentDate: "2024-02-29",
      }),
    ).toBeNull();
    expect(
      validatePayrollPeriodFields({ ...periodCreation(), earningsMonth: "2026-13" }),
    ).not.toBeNull();
    expect(validPayrollPeriodSearch({ ...search, until: "2028-12" })).toBe(true);
    expect(validPayrollPeriodSearch({ ...search, until: "2029-01" })).toBe(false);
    expect(validPayrollPeriodSearch({ ...search, until: "2025-12" })).toBe(false);
  });
  it("rejects duplicate, foreign-format and excessive participant selections before I/O", async () => {
    const { repository, signal } = fixture(),
      create = new CreatePayrollPeriod(repository);
    for (const employeeIds of [
      [],
      [periodEmployee().id, periodEmployee().id.toUpperCase()],
      ["bad"],
      Array.from({ length: 5001 }, (_, i) => periodEmployee(i + 1).id),
    ])
      expect(
        (await create.execute(access, operation, { ...periodCreation(), employeeIds }, signal)).ok,
      ).toBe(false);
    expect(repository.create).not.toHaveBeenCalled();
    expect(
      validatePayrollPeriodCreation({
        ...periodCreation(),
        employeeIds: Array.from({ length: 5000 }, (_, i) => periodEmployee(i + 1).id),
      }),
    ).toBeNull();
    const input = periodCreation(),
      review = new ReviewPayrollPeriodCreation().execute(access, {
        ...input,
        employeeIds: [...input.employeeIds].reverse(),
        reason: " Review ",
      });
    expect(review).toMatchObject({
      ok: true,
      value: { reason: "Review", employeeIds: input.employeeIds },
    });
    if (review.ok) expect(Object.isFrozen(review.value.employeeIds)).toBe(true);
  });
  it("keeps literal bounded lookup and rejects malformed cursors and observed versions", async () => {
    const { repository, signal } = fixture();
    await new ListPayrollPeriodEmployees(repository).execute(access, "  %_  ", "PAY-050", signal);
    expect(repository.employees).toHaveBeenCalledWith(access.companyId, "%_", "PAY-050", signal);
    for (const after of [" ", "a".repeat(33)])
      expect(
        (await new ListPayrollPeriodEmployees(repository).execute(access, "", after, signal)).ok,
      ).toBe(false);
    expect(
      (await new LoadPayrollPeriod(repository).execute(access, periodId, null, "256", signal)).ok,
    ).toBe(false);
    for (const version of [-1, 0.5, 255])
      expect(
        (
          await new CancelPayrollPeriod(repository).execute(
            access,
            periodId,
            operation,
            version,
            "Reason",
            signal,
          )
        ).ok,
      ).toBe(false);
    expect(repository.cancel).not.toHaveBeenCalled();
  });
  it("loads a fresh draft for cancellation and refuses calculated or cancelled periods", async () => {
    const { repository, signal, record } = fixture(),
      review = new ReviewPayrollPeriodCancellation(repository);
    expect((await review.execute(access, periodId, signal)).ok).toBe(true);
    repository.details.mockResolvedValue(
      success({ ...record, period: { ...record.period, status: "CANCELLED" } }),
    );
    expect(await review.execute(access, periodId, signal)).toMatchObject({
      ok: false,
      failure: { code: "payroll_period_not_draft" },
    });
    repository.details.mockResolvedValue(
      success({ ...record, period: { ...record.period, version: 255 } }),
    );
    expect(await review.execute(access, periodId, signal)).toMatchObject({
      ok: false,
      failure: { code: "payroll_period_revision_limit" },
    });
  });
  it("propagates cancellation before any repository request", () => {
    const { repository } = fixture(),
      owner = new AbortController();
    owner.abort();
    expect(() =>
      new CreatePayrollPeriod(repository).execute(
        access,
        operation,
        periodCreation(),
        owner.signal,
      ),
    ).toThrow();
    expect(() =>
      new LoadPayrollPeriod(repository).execute(access, periodId, null, null, owner.signal),
    ).toThrow();
    expect(repository.create).not.toHaveBeenCalled();
    expect(repository.details).not.toHaveBeenCalled();
  });
});
