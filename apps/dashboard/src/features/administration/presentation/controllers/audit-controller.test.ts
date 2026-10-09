import { describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { AuditPage } from "../../domain/entities/audit-page";
import { defaultAuditSearch } from "../../domain/entities/audit-search";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { AuditController } from "./audit-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["audit.read"] };
const event = {
  id: "40000000-0000-4000-8000-000000000001",
  companyId,
  actorId: "20000000-0000-4000-8000-000000000001" as AccountId,
  resourceType: "company",
  resourceId: companyId,
  action: "company.created",
  correlationId: "50000000-0000-4000-8000-000000000001",
  recordedAt: "2026-09-25T00:00:00Z",
};
const page: AuditPage = {
  companyId,
  from: "2026-09-01T00:00:00Z",
  until: "2026-10-01T00:00:00Z",
  evaluatedAt: "2026-10-01T00:00:00Z",
  items: [event],
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

describe("audit page ownership", () => {
  it("aborts superseded reads and ignores late success and failure without another request", async () => {
    for (const fail of [false, true]) {
      const old = pending<Result<AuditPage>>();
      const execute = vi
        .fn<AdministrationUseCases["searchAudit"]["execute"]>()
        .mockReturnValueOnce(old.promise)
        .mockResolvedValue(success(page));
      const controller = new AuditController({ execute }, access, defaultAuditSearch);
      controller.activate();
      controller.activate();
      expect(execute).toHaveBeenCalledTimes(1);
      const stale = execute.mock.calls[0]?.[2];
      await controller.refresh();
      expect(stale?.aborted).toBe(true);
      if (fail) old.reject(new Error("PRIVATE LATE FAILURE"));
      else old.resolve(success({ ...page, items: [] }));
      await Promise.resolve();
      expect(controller.getSnapshot()).toEqual({
        stage: "ready",
        page,
        selected: null,
        failure: null,
      });
      expect(execute).toHaveBeenCalledTimes(2);
      controller.deactivate();
    }
  });

  it("only opens loaded events and removes the page and open details before refresh or access failure", async () => {
    const next = pending<Result<AuditPage>>();
    const execute = vi
      .fn<AdministrationUseCases["searchAudit"]["execute"]>()
      .mockResolvedValueOnce(success(page))
      .mockReturnValueOnce(next.promise);
    const controller = new AuditController({ execute }, access, defaultAuditSearch);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    controller.openEvent("unknown");
    expect(controller.getSnapshot().selected).toBeNull();
    controller.openEvent(event.id);
    expect(controller.getSnapshot().selected).toBe(event);
    controller.closeEvent();
    expect(controller.getSnapshot().selected).toBeNull();
    controller.openEvent(event.id);
    const read = controller.refresh();
    expect(controller.getSnapshot()).toEqual({
      stage: "loading",
      page: null,
      selected: null,
      failure: null,
    });
    controller.openEvent(event.id);
    next.resolve(failed("company_access_denied"));
    await read;
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      page: null,
      selected: null,
      failure: { code: "company_access_denied" },
    });
    controller.deactivate();
  });

  it("copies search inputs, cancels on disposal and supports strict-mode remount without accepting old work", async () => {
    const old = pending<Result<AuditPage>>();
    const execute = vi
      .fn<AdministrationUseCases["searchAudit"]["execute"]>()
      .mockReturnValueOnce(old.promise)
      .mockResolvedValue(success(page));
    const query = { ...defaultAuditSearch };
    const controller = new AuditController({ execute }, access, query);
    query.action = "changed.after.construction";
    controller.activate();
    expect(execute.mock.calls[0]?.[1].action).toBeNull();
    const signal = execute.mock.calls[0]?.[2];
    controller.deactivate();
    expect(signal?.aborted).toBe(true);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().page).toBe(page));
    old.resolve(failed("session_revoked"));
    await old.promise;
    expect(controller.getSnapshot().page).toBe(page);
    controller.deactivate();
    await controller.refresh();
    controller.openEvent(event.id);
    expect(controller.getSnapshot()).toEqual({
      stage: "idle",
      page: null,
      selected: null,
      failure: null,
    });
    expect(execute).toHaveBeenCalledTimes(2);
  });

  it("contains unexpected adapter errors, retries only on request and stops notifying removed subscribers", async () => {
    const execute = vi
      .fn<AdministrationUseCases["searchAudit"]["execute"]>()
      .mockRejectedValueOnce(new Error("PRIVATE ADAPTER FAILURE"))
      .mockResolvedValue(success(page));
    const controller = new AuditController({ execute }, access, defaultAuditSearch);
    const listener = vi.fn();
    const unsubscribe = controller.subscribe(listener);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().failure?.code).toBe("unexpected_error"));
    expect(execute).toHaveBeenCalledTimes(1);
    expect(JSON.stringify(controller.getSnapshot())).not.toContain("PRIVATE");
    await controller.refresh();
    expect(controller.getSnapshot().page).toBe(page);
    unsubscribe();
    listener.mockClear();
    controller.deactivate();
    expect(listener).not.toHaveBeenCalled();
  });
});
