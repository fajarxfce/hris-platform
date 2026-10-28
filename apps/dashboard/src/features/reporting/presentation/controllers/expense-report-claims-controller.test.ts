import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { ExpenseReportClaimPage } from "../../domain/entities/expense-report-claim";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { ExpenseReportClaimsController } from "./expense-report-claims-controller";

const companyId = "c6dbef75-2e86-41cd-a3a5-8fb13b21a15f" as CompanyId;
const access = { companyId, permissions: ["reports.read", "expenses.read"] };
const date = "2026-09-30";
const report: ExpenseReportClaimPage = {
  companyId,
  from: date,
  until: date,
  status: null,
  evaluatedAt: "2026-10-01T00:00:00Z",
  definitionVersion: "expenses.v1",
  currency: "IDR",
  items: [],
  nextCursor: null,
};
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((accept, fail) => {
    resolve = accept;
    reject = fail;
  });
  return { promise, resolve, reject };
}

describe("expense report request ownership", () => {
  it("cancels replaced reads and ignores late successes and failures", async () => {
    for (const rejected of [true, false]) {
      const old = pending<Result<ExpenseReportClaimPage>>();
      const execute = vi
        .fn<ReportingUseCases["loadExpenseClaims"]["execute"]>()
        .mockReturnValueOnce(old.promise)
        .mockResolvedValue(success(report));
      const controller = new ExpenseReportClaimsController({ execute }, access, {
        from: date,
        until: date,
        status: null,
        after: null,
      });
      controller.activate();
      const signal = execute.mock.calls[0]?.[2];
      await controller.refresh();
      expect(signal?.aborted).toBe(true);
      if (rejected) old.reject(new Error("Late failure"));
      else old.resolve(success({ ...report, nextCursor: "late" }));
      await Promise.resolve();
      expect(controller.getSnapshot()).toEqual({ stage: "ready", page: report, failure: null });
      controller.deactivate();
    }
  });

  it("disposes observers and pending work, then admits only a new mounted read", async () => {
    const old = pending<Result<ExpenseReportClaimPage>>();
    const execute = vi
      .fn<ReportingUseCases["loadExpenseClaims"]["execute"]>()
      .mockReturnValueOnce(old.promise)
      .mockResolvedValue(success(report));
    const controller = new ExpenseReportClaimsController({ execute }, access, {
      from: date,
      until: date,
      status: null,
      after: null,
    });
    const listener = vi.fn();
    const unsubscribe = controller.subscribe(listener);
    controller.activate();
    const signal = execute.mock.calls[0]?.[2];
    unsubscribe();
    listener.mockClear();
    controller.deactivate();
    expect(signal?.aborted).toBe(true);
    expect(listener).not.toHaveBeenCalled();
    expect(controller.getSnapshot().page).toBeNull();
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    old.resolve(failed("session_revoked"));
    await old.promise;
    expect(controller.getSnapshot().page).toEqual(report);
    controller.deactivate();
    await controller.refresh();
    expect(execute).toHaveBeenCalledTimes(2);
  });

  it("keeps denied counts absent through a pending/failed retry and reopens after admission", async () => {
    const retry = pending<Result<ExpenseReportClaimPage>>();
    const execute = vi
      .fn<ReportingUseCases["loadExpenseClaims"]["execute"]>()
      .mockResolvedValueOnce(success(report))
      .mockResolvedValueOnce(failed("company_access_denied"))
      .mockReturnValueOnce(retry.promise)
      .mockResolvedValueOnce(success(report));
    const controller = new ExpenseReportClaimsController({ execute }, access, {
      from: date,
      until: date,
      status: null,
      after: null,
    });
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    await controller.refresh();
    expect(controller.getSnapshot().page).toBeNull();
    const read = controller.refresh();
    expect(controller.getSnapshot().page).toBeNull();
    retry.resolve(failed("network_unavailable"));
    await read;
    expect(controller.getSnapshot().page).toBeNull();
    await controller.refresh();
    expect(controller.getSnapshot().page).toEqual(report);
    controller.deactivate();
  });
});
