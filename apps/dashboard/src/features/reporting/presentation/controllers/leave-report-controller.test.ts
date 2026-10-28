import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { LeaveReport } from "../../domain/entities/leave-report";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { LeaveReportController } from "./leave-report-controller";

const companyId = "c6dbef75-2e86-41cd-a3a5-8fb13b21a15f" as CompanyId;
const access = { companyId, permissions: ["reports.read", "leave.read"] };
const date = "2026-09-30";
const counts = { requests: 1, employments: 1, days: "0.5" };
const report: LeaveReport = {
  companyId,
  from: date,
  until: date,
  evaluatedAt: "2026-10-01T00:00:00Z",
  definitionVersion: "leave.v1",
  totals: counts,
  statuses: [{ status: "PENDING", counts }],
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

describe("leave report ownership", () => {
  it("cancels replaced reads and ignores late successes and failures", async () => {
    for (const rejected of [true, false]) {
      const old = pending<Result<LeaveReport>>();
      const execute = vi
        .fn<ReportingUseCases["loadLeave"]["execute"]>()
        .mockReturnValueOnce(old.promise)
        .mockResolvedValue(success(report));
      const controller = new LeaveReportController({ execute }, access, date, date);
      controller.activate();
      const signal = execute.mock.calls[0]?.[3];
      await controller.refresh();
      expect(signal?.aborted).toBe(true);
      if (rejected) old.reject(new Error("Late failure"));
      else old.resolve(success({ ...report, totals: { ...counts, requests: 99 } }));
      await Promise.resolve();
      expect(controller.getSnapshot()).toEqual({ stage: "ready", report, failure: null });
      controller.deactivate();
    }
  });

  it("disposes observers and pending work, then admits only a new mounted read", async () => {
    const old = pending<Result<LeaveReport>>();
    const execute = vi
      .fn<ReportingUseCases["loadLeave"]["execute"]>()
      .mockReturnValueOnce(old.promise)
      .mockResolvedValue(success(report));
    const controller = new LeaveReportController({ execute }, access, date, date);
    const listener = vi.fn();
    const unsubscribe = controller.subscribe(listener);
    controller.activate();
    const signal = execute.mock.calls[0]?.[3];
    unsubscribe();
    listener.mockClear();
    controller.deactivate();
    expect(signal?.aborted).toBe(true);
    expect(listener).not.toHaveBeenCalled();
    expect(controller.getSnapshot().report).toBeNull();
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    old.resolve(failed("session_revoked"));
    await old.promise;
    expect(controller.getSnapshot().report).toEqual(report);
    controller.deactivate();
    await controller.refresh();
    expect(execute).toHaveBeenCalledTimes(2);
  });

  it("keeps denied counts absent through a pending/failed retry and reopens after admission", async () => {
    const retry = pending<Result<LeaveReport>>();
    const execute = vi
      .fn<ReportingUseCases["loadLeave"]["execute"]>()
      .mockResolvedValueOnce(success(report))
      .mockResolvedValueOnce(failed("company_access_denied"))
      .mockReturnValueOnce(retry.promise)
      .mockResolvedValueOnce(success(report));
    const controller = new LeaveReportController({ execute }, access, date, date);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    await controller.refresh();
    expect(controller.getSnapshot().report).toBeNull();
    const read = controller.refresh();
    expect(controller.getSnapshot().report).toBeNull();
    retry.resolve(failed("network_unavailable"));
    await read;
    expect(controller.getSnapshot().report).toBeNull();
    await controller.refresh();
    expect(controller.getSnapshot().report).toEqual(report);
    controller.deactivate();
  });
});
