import { describe, expect, it, vi } from "vitest";
import {
  calculationCompany,
  calculationId,
  calculationRun,
} from "../../../../../tests/fixtures/payroll-calculations";
import {
  comparisonBaseline,
  comparisonFixture,
} from "../../../../../tests/fixtures/payroll-comparison";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toPayrollRun } from "../../data/mappers/payroll-calculation-mapper";
import { toPayrollRunComparison } from "../../data/mappers/payroll-run-comparison-mapper";
import type {
  PayrollComparisonContext,
  PayrollRunComparison,
} from "../../domain/entities/payroll-run-comparison";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollComparisonController } from "./payroll-comparison-controller";

const access = { companyId: calculationCompany as CompanyId, permissions: ["payroll.read"] };
const context: PayrollComparisonContext = {
  run: toPayrollRun(
    calculationRun({ totalEmployees: 3, processed: 3, succeeded: 3 }),
    access.companyId,
  ),
  month: "2026-08",
  periods: { items: [], nextCursor: null },
};
const value = () =>
  toPayrollRunComparison(comparisonFixture(), calculationId, comparisonBaseline, null, false);
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
const controller = (
  context: PayrollUseCases["loadComparisonContext"]["execute"],
  compare: PayrollUseCases["loadComparison"]["execute"],
  baseline: string | null = comparisonBaseline,
) =>
  new PayrollComparisonController(
    { loadComparisonContext: { execute: context }, loadComparison: { execute: compare } },
    access,
    calculationId,
    baseline,
    null,
    null,
    null,
    false,
  );
describe("payroll comparison resource ownership", () => {
  it("does not compare before choosing a baseline and removes a disposed context", async () => {
    const pending = deferred<Result<PayrollComparisonContext>>(),
      load = vi.fn<PayrollUseCases["loadComparisonContext"]["execute"]>(() => pending.promise),
      compare = vi.fn(),
      c = controller(load, compare, null);
    c.activate();
    c.deactivate();
    pending.resolve(success(context));
    await pending.promise;
    await Promise.resolve();
    expect(c.getSnapshot().context).toBeNull();
    expect(compare).not.toHaveBeenCalled();
    expect(load.mock.calls[0]?.[0]).toEqual(access);
  });
  it("abandons an old monetary result, closes its selected employee and ignores late errors", async () => {
    const old = deferred<Result<PayrollRunComparison>>(),
      current = deferred<Result<PayrollRunComparison>>();
    const load = vi.fn().mockResolvedValue(success(context)),
      compare = vi
        .fn()
        .mockResolvedValueOnce(success(value()))
        .mockReturnValueOnce(old.promise)
        .mockReturnValueOnce(current.promise),
      c = controller(load, compare);
    c.activate();
    await Promise.resolve();
    await Promise.resolve();
    c.select(value().employees.items[0]?.employmentId ?? "");
    expect(c.getSnapshot().selected).not.toBeNull();
    const replaced = c.refresh();
    expect(c.getSnapshot().selected).toBeNull();
    expect(c.getSnapshot().comparison).toBeNull();
    await Promise.resolve();
    const fresh = c.refresh();
    await Promise.resolve();
    current.resolve(success(value()));
    await fresh;
    old.resolve(failed("session_revoked"));
    await replaced;
    expect(c.getSnapshot()).toMatchObject({ stage: "ready", failure: null });
    expect((compare.mock.calls[1]?.[5] as AbortSignal | undefined)?.aborted).toBe(true);
    c.deactivate();
    expect(c.getSnapshot().comparison).toBeNull();
  });
  it("clears context on a failed comparison and never schedules an automatic retry", async () => {
    const load = vi.fn().mockResolvedValue(success(context)),
      compare = vi.fn().mockResolvedValue(failed("access_denied")),
      c = controller(load, compare);
    c.activate();
    await Promise.resolve();
    await Promise.resolve();
    expect(c.getSnapshot()).toMatchObject({
      stage: "unavailable",
      context: null,
      comparison: null,
      failure: { code: "access_denied" },
    });
    expect(compare).toHaveBeenCalledTimes(1);
    c.deactivate();
  });
});
