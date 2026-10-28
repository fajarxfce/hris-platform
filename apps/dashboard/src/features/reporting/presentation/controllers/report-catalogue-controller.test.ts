import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { ReportCatalogue } from "../../domain/entities/report-catalogue";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { ReportCatalogueController } from "./report-catalogue-controller";

const companyId = "77ed1727-6b89-48f9-ae10-f5f3f87c93f0" as CompanyId;
const access = { companyId, permissions: ["reports.read", "people.read"] };
const report: ReportCatalogue = {
  companyId,
  evaluatedAt: "2026-10-01T00:00:00Z",
  reports: [{ kind: "HEADCOUNT", definitionVersion: "headcount.v1" }],
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

describe("report catalogue ownership", () => {
  it("cancels replaced reads and ignores late successes and failures", async () => {
    for (const rejected of [true, false]) {
      const old = pending<Result<ReportCatalogue>>();
      const execute = vi
        .fn<ReportingUseCases["loadCatalogue"]["execute"]>()
        .mockReturnValueOnce(old.promise)
        .mockResolvedValue(success(report));
      const controller = new ReportCatalogueController({ execute }, access);
      controller.activate();
      const signal = execute.mock.calls[0]?.[1];
      await controller.refresh();
      expect(signal?.aborted).toBe(true);
      if (rejected) old.reject(new Error("Late failure"));
      else old.resolve(success({ ...report, reports: [] }));
      await Promise.resolve();
      expect(controller.getSnapshot()).toEqual({ stage: "ready", report, failure: null });
      controller.deactivate();
    }
  });

  it("disposes observers and pending work, then admits only a new mounted read", async () => {
    const old = pending<Result<ReportCatalogue>>();
    const execute = vi
      .fn<ReportingUseCases["loadCatalogue"]["execute"]>()
      .mockReturnValueOnce(old.promise)
      .mockResolvedValue(success(report));
    const controller = new ReportCatalogueController({ execute }, access);
    const listener = vi.fn();
    const unsubscribe = controller.subscribe(listener);
    controller.activate();
    const signal = execute.mock.calls[0]?.[1];
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
    const retry = pending<Result<ReportCatalogue>>();
    const execute = vi
      .fn<ReportingUseCases["loadCatalogue"]["execute"]>()
      .mockResolvedValueOnce(success(report))
      .mockResolvedValueOnce(failed("company_access_denied"))
      .mockReturnValueOnce(retry.promise)
      .mockResolvedValueOnce(success(report));
    const controller = new ReportCatalogueController({ execute }, access);
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
