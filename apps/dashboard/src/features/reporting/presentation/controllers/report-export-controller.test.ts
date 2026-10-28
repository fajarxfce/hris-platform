import { describe, expect, it, vi } from "vitest";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  exportAccess as access,
  exportCompany as company,
  exportFixture as fixture,
  exportId as id,
  exportNow as now,
  exportOperation as operation,
  exportSelection as selection,
} from "../../di/report-export-fixture";
import type { ReportExportDetails, ReportExportReceipt } from "../../domain/entities/report-export";
import type { ReportExportUseCases } from "../contracts/report-export-use-cases";
import { ReportExportController } from "./report-export-controller";
import { ReportExportHistoryController } from "./report-export-history-controller";

function setup(resource: string | null = null) {
  const readExports = {
    list: vi
      .fn<ReportExportUseCases["readExports"]["list"]>()
      .mockResolvedValue(success({ items: [], next: null })),
    get: vi.fn<ReportExportUseCases["readExports"]["get"]>().mockResolvedValue(success(fixture())),
  };
  const manageExports = {
    start: vi
      .fn<ReportExportUseCases["manageExports"]["start"]>()
      .mockResolvedValue(success({ id, version: 0 })),
    change: vi
      .fn<ReportExportUseCases["manageExports"]["change"]>()
      .mockResolvedValue(success({ id, version: 1 })),
  };
  const downloadExport = {
    execute: vi
      .fn<ReportExportUseCases["downloadExport"]["execute"]>()
      .mockResolvedValue(success(undefined)),
  };
  const next = vi.fn(() => operation),
    actions = { readExports, manageExports, downloadExport };
  return {
    ...actions,
    next,
    controller: new ReportExportController(actions, access, resource, next, now),
  };
}
const ready = async (c: ReportExportController) => {
  c.activate();
  await vi.waitFor(() => expect(c.getSnapshot().stage).toBe("ready"));
};

describe("owned export controller", () => {
  it("retains the exact start command across response loss, MFA, conflicts and visibility changes", async () => {
    const a = setup();
    await ready(a.controller);
    const selected = [company];
    a.manageExports.start
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_version"));
    await a.controller.start({
      ...selection,
      parameters: { ...selection.parameters, companies: selected },
    });
    selected.length = 0;
    expect(a.controller.getSnapshot().stage).toBe("unconfirmed");
    await a.controller.start(selection);
    await a.controller.refresh();
    expect(a.next).toHaveBeenCalledTimes(1);
    await a.controller.retry();
    a.controller.setVisible(false);
    await a.controller.retry();
    a.controller.setVisible(true);
    expect(a.controller.getSnapshot().stage).toBe("unconfirmed");
    await a.controller.retry();
    await a.controller.retry();
    expect(a.controller.getSnapshot()).toMatchObject({
      stage: "completed",
      receipt: { id, version: 0 },
    });
    expect(a.manageExports.start).toHaveBeenCalledTimes(4);
    for (const call of a.manageExports.start.mock.calls) {
      expect(call[1]).toBe(operation);
      expect(call[2].parameters.companies).toEqual([company]);
    }
    a.controller.deactivate();
  });
  it.each(["cancel", "resume"] as const)(
    "confirms %s at the observed version and recovers the original command",
    async (kind) => {
      const a = setup(id);
      a.readExports.get.mockResolvedValue(
        success(
          fixture(
            kind === "resume"
              ? { status: "STOPPED", jobStatus: "FAILED", version: 4 }
              : { version: 4 },
          ),
        ),
      );
      a.manageExports.change
        .mockResolvedValueOnce(failed("invalid_response"))
        .mockResolvedValueOnce(success({ id, version: 5 }));
      await ready(a.controller);
      await a.controller.confirm();
      expect(a.manageExports.change).not.toHaveBeenCalled();
      a.controller.review(kind);
      a.controller.dismiss();
      await a.controller.confirm();
      expect(a.manageExports.change).not.toHaveBeenCalled();
      a.controller.review(kind);
      await a.controller.confirm();
      expect(a.controller.getSnapshot().stage).toBe("unconfirmed");
      a.controller.review("cancel");
      await a.controller.retry();
      expect(a.manageExports.change.mock.calls.map((call) => [call[1], call[2], call[3]])).toEqual([
        [operation, kind, { id, expectedVersion: 4 }],
        [operation, kind, { id, expectedVersion: 4 }],
      ]);
      expect(a.readExports.get).toHaveBeenCalledTimes(2);
      a.controller.deactivate();
    },
  );
  it("requires a refreshed review after a definitive rejection; no automatic retries", async () => {
    const a = setup(id);
    a.manageExports.change.mockResolvedValue(failed("stale_version"));
    await ready(a.controller);
    a.controller.review("cancel");
    await a.controller.confirm();
    expect(a.controller.getSnapshot()).toMatchObject({ stage: "conflict", detail: null });
    await a.controller.retry();
    expect(a.manageExports.change).toHaveBeenCalledTimes(1);
    await a.controller.refresh();
    expect(a.controller.getSnapshot().stage).toBe("ready");
    a.controller.deactivate();
  });
  it("keeps a command unconfirmed when hidden while submitting and ignores late success", async () => {
    const a = setup();
    let resolve!: (value: Result<ReportExportReceipt>) => void;
    a.manageExports.start.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    await ready(a.controller);
    const pending = a.controller.start(selection);
    const signal = a.manageExports.start.mock.calls[0]?.[3];
    a.controller.setVisible(false);
    expect(signal?.aborted).toBe(true);
    resolve(success({ id, version: 0 }));
    await pending;
    expect(a.controller.getSnapshot().stage).toBe("unconfirmed");
    a.controller.setVisible(true);
    await a.controller.retry();
    expect(a.controller.getSnapshot().stage).toBe("completed");
    a.controller.deactivate();
  });
  it.each(["returned-failure", "sync-throw", "async-throw"])(
    "keeps uncertain %s commands and clears them on disposal",
    async (mode) => {
      const a = setup();
      if (mode === "returned-failure")
        a.manageExports.start.mockResolvedValueOnce(failed("database_failure"));
      else if (mode === "sync-throw")
        a.manageExports.start.mockImplementationOnce(() => {
          throw new Error("PRIVATE");
        });
      else a.manageExports.start.mockRejectedValueOnce(new Error("PRIVATE"));
      await ready(a.controller);
      await a.controller.start(selection);
      expect(a.controller.getSnapshot().stage).toBe("unconfirmed");
      expect(JSON.stringify(a.controller.getSnapshot())).not.toContain("PRIVATE");
      a.controller.deactivate();
      await a.controller.retry();
      expect(a.manageExports.start).toHaveBeenCalledTimes(1);
      expect(a.controller.getSnapshot().operationId).toBeNull();
    },
  );
  it("clears a revoked read and prevents an obsolete result from restoring metadata", async () => {
    const a = setup(id);
    let resolve!: (value: Result<ReportExportDetails>) => void;
    a.readExports.get.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    a.controller.activate();
    const signal = a.readExports.get.mock.calls[0]?.[2];
    a.controller.setVisible(false);
    expect(signal?.aborted).toBe(true);
    a.readExports.get.mockResolvedValueOnce(failed("access_denied"));
    a.controller.setVisible(true);
    await vi.waitFor(() => expect(a.controller.getSnapshot().stage).toBe("unavailable"));
    resolve(success(fixture()));
    await Promise.resolve();
    expect(a.controller.getSnapshot().detail).toBeNull();
    a.controller.deactivate();
  });
  it("bounds one download, cancels it on refresh/hiding and clears revoked content", async () => {
    const a = setup(id);
    a.readExports.get.mockResolvedValue(
      success(fixture({ status: "READY", jobStatus: "SUCCEEDED", bytes: 6 })),
    );
    let resolve!: (value: Result<void>) => void;
    a.downloadExport.execute.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    await ready(a.controller);
    const pending = a.controller.download();
    await a.controller.download();
    expect(a.downloadExport.execute).toHaveBeenCalledTimes(1);
    const signal = a.downloadExport.execute.mock.calls[0]?.[2];
    a.controller.cancelDownload();
    resolve(success(undefined));
    await pending;
    expect(signal?.aborted).toBe(true);
    expect(a.controller.getSnapshot().download).toBe("idle");
    a.downloadExport.execute.mockResolvedValueOnce(failed("mfa_required"));
    await a.controller.download();
    expect(a.controller.getSnapshot()).toMatchObject({ stage: "unavailable", detail: null });
    await a.controller.download();
    expect(a.downloadExport.execute).toHaveBeenCalledTimes(2);
    a.controller.deactivate();
  });
  it.each([
    { expired: true },
    { attempt: 8 },
    { jobStatus: "RUNNING" as const },
    { status: "READY" as const, jobStatus: "SUCCEEDED" as const },
  ])("does not offer recovery for an ineligible export %j", async (patch) => {
    const a = setup(id);
    a.readExports.get.mockResolvedValue(
      success(fixture({ status: "STOPPED", jobStatus: "FAILED", ...patch })),
    );
    await ready(a.controller);
    a.controller.review("resume");
    await a.controller.confirm();
    expect(a.manageExports.change).not.toHaveBeenCalled();
    a.controller.deactivate();
  });
});

describe("export history lifecycle", () => {
  it("replaces one page, retains only its keyset and resets to newest on refresh", async () => {
    const a = setup(),
      cursor = { id, createdAt: fixture().createdAt };
    a.readExports.list
      .mockResolvedValueOnce(success({ items: [fixture()], next: cursor }))
      .mockResolvedValueOnce(success({ items: [], next: null }));
    const c = new ReportExportHistoryController(a.readExports, access);
    c.activate();
    await vi.waitFor(() => expect(c.getSnapshot().history?.items).toHaveLength(1));
    await c.older();
    expect(c.getSnapshot()).toMatchObject({ history: { items: [], next: null }, older: true });
    expect(a.readExports.list.mock.calls[1]?.[1]).toEqual(cursor);
    await c.older();
    expect(a.readExports.list).toHaveBeenCalledTimes(2);
    await c.refresh();
    expect(a.readExports.list.mock.lastCall?.[1]).toBeNull();
    c.deactivate();
    expect(c.getSnapshot().history).toBeNull();
  });
  it("stops hidden reads, clears metadata, suppresses late results and reacquires once", async () => {
    const a = setup();
    let resolve!: (value: Awaited<ReturnType<typeof a.readExports.list>>) => void;
    a.readExports.list.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const c = new ReportExportHistoryController(a.readExports, access);
    c.activate(false);
    expect(a.readExports.list).not.toHaveBeenCalled();
    c.setVisible(true);
    const signal = a.readExports.list.mock.calls[0]?.[2];
    c.setVisible(false);
    expect(signal?.aborted).toBe(true);
    resolve(success({ items: [fixture()], next: null }));
    await Promise.resolve();
    expect(c.getSnapshot().history).toBeNull();
    c.setVisible(true);
    await vi.waitFor(() => expect(c.getSnapshot().stage).toBe("ready"));
    expect(a.readExports.list).toHaveBeenCalledTimes(2);
    c.deactivate();
    await c.refresh();
    expect(a.readExports.list).toHaveBeenCalledTimes(2);
  });
});
