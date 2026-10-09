import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { HeadcountReport } from "../../domain/entities/headcount-report";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { HeadcountController } from "./headcount-controller";

const companyId = "c6dbef75-2e86-41cd-a3a5-8fb13b21a15f" as CompanyId;
const access = { companyId, permissions: ["reports.read", "people.read"] };
const asOf = "2026-10-01";
const counts = {
  employments: 3,
  persons: 3,
  active: 3,
  probation: 0,
  suspended: 0,
  permanent: 3,
  fixedTerm: 0,
};
const report: HeadcountReport = {
  asOf,
  evaluatedAt: "2026-10-01T00:00:00Z",
  definitionVersion: "headcount.v1",
  totals: counts,
  companies: [{ companyId, counts }],
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

describe("headcount request ownership", () => {
  it("cancels superseded reads and ignores both late successes and late failures", async () => {
    for (const fail of [false, true]) {
      const old = pending<Result<HeadcountReport>>();
      const execute = vi
        .fn<ReportingUseCases["loadHeadcount"]["execute"]>()
        .mockReturnValueOnce(old.promise)
        .mockResolvedValue(success(report));
      const controller = new HeadcountController({ execute }, access, asOf);
      controller.activate();
      const stale = execute.mock.calls[0]?.[2];
      await controller.refresh();
      expect(stale?.aborted).toBe(true);
      if (fail) old.reject(new Error("Late source failure"));
      else
        old.resolve(
          success({
            ...report,
            totals: { ...counts, employments: 9, persons: 9, active: 9, permanent: 9 },
          }),
        );
      await Promise.resolve();
      expect(controller.getSnapshot()).toEqual({ stage: "ready", report, failure: null });
      controller.deactivate();
    }
  });

  it("copies its company selection and cancels the whole group when disposed", async () => {
    const selected = [companyId];
    const old = pending<Result<HeadcountReport>>();
    const execute = vi
      .fn<ReportingUseCases["loadHeadcount"]["execute"]>()
      .mockReturnValue(old.promise);
    const controller = new HeadcountController({ execute }, access, asOf, selected);
    selected.push("77ed1727-6b89-48f9-ae10-f5f3f87c93f0" as CompanyId);
    controller.activate();
    expect(execute.mock.calls[0]?.[3]).toEqual([companyId]);
    const signal = execute.mock.calls[0]?.[2];
    controller.deactivate();
    expect(signal?.aborted).toBe(true);
    old.resolve(success(report));
    await old.promise;
    expect(controller.getSnapshot().report).toBeNull();
  });

  it("clears private values and pending work on disposal and supports strict-mode remount", async () => {
    const old = pending<Result<HeadcountReport>>();
    const execute = vi
      .fn<ReportingUseCases["loadHeadcount"]["execute"]>()
      .mockReturnValueOnce(old.promise)
      .mockResolvedValue(success(report));
    const controller = new HeadcountController({ execute }, access, asOf);
    controller.activate();
    const stale = execute.mock.calls[0]?.[2];
    controller.deactivate();
    expect(stale?.aborted).toBe(true);
    expect(controller.getSnapshot().report).toBeNull();
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    old.resolve(failed("session_revoked"));
    await old.promise;
    expect(controller.getSnapshot().report).toEqual(report);
    controller.deactivate();
    await controller.refresh();
    expect(execute).toHaveBeenCalledTimes(2);
    expect(controller.getSnapshot().report).toBeNull();
  });

  it("removes old counts before refreshing and leaves none after access failure", async () => {
    const next = pending<Result<HeadcountReport>>();
    const execute = vi
      .fn<ReportingUseCases["loadHeadcount"]["execute"]>()
      .mockResolvedValueOnce(success(report))
      .mockReturnValueOnce(next.promise);
    const controller = new HeadcountController({ execute }, access, asOf);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    const read = controller.refresh();
    expect(controller.getSnapshot()).toEqual({ stage: "loading", report: null, failure: null });
    next.resolve(failed("company_access_denied"));
    await read;
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      report: null,
      failure: { code: "company_access_denied" },
    });
    controller.deactivate();
  });

  it("stops notifying a removed subscriber and contains unexpected adapter failures", async () => {
    const execute = vi
      .fn<ReportingUseCases["loadHeadcount"]["execute"]>()
      .mockRejectedValue(new Error("Internal fixture failure"));
    const controller = new HeadcountController({ execute }, access, asOf);
    const listener = vi.fn();
    const unsubscribe = controller.subscribe(listener);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("unavailable"));
    expect(controller.getSnapshot().failure?.code).toBe("unexpected_error");
    unsubscribe();
    listener.mockClear();
    controller.deactivate();
    expect(listener).not.toHaveBeenCalled();
  });
});
