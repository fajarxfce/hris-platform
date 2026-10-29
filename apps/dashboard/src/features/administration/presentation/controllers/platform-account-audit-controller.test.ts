import { expect, it, vi } from "vitest";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { PlatformAccountAccessAudit } from "../../domain/entities/platform-account-access-audit";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { PlatformAccountAuditController } from "./platform-account-audit-controller";

const access = {
  accountId: "account-a" as AccountId,
  permissions: ["audit.read", "identity.manage"],
};
const event = {
  id: "event-a",
  actorId: access.accountId,
  resourceId: "target-a",
  resourceType: "account",
  action: "identity.account_access_saved",
  correlationId: "correlation",
  recordedAt: "2026-10-01T00:00:00Z",
};
const detail: PlatformAccountAccessAudit = {
  id: event.id,
  accountId: event.resourceId,
  active: true,
  permissions: ["audit.read"],
  reason: "Saved reason",
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

it("clears private details before retry and rejects superseded late successes and failures", async () => {
  for (const rejected of [false, true]) {
    const stale = pending<Result<PlatformAccountAccessAudit>>();
    const execute = vi
      .fn<AdministrationUseCases["getPlatformAccountAudit"]["execute"]>()
      .mockReturnValueOnce(stale.promise)
      .mockResolvedValueOnce(success(detail))
      .mockResolvedValueOnce(failed("access_denied"));
    const owner = new PlatformAccountAuditController({ execute }, access, event);
    owner.activate();
    owner.activate();
    expect(execute).toHaveBeenCalledTimes(1);
    await owner.refresh();
    expect(execute.mock.calls[0]?.[2].aborted).toBe(true);
    if (rejected) stale.reject(new Error("PRIVATE LATE"));
    else stale.resolve(success({ ...detail, reason: "PRIVATE LATE" }));
    await Promise.resolve();
    expect(owner.getSnapshot().review).toBe(detail);
    const retry = owner.refresh();
    expect(owner.getSnapshot().review).toBeNull();
    await retry;
    expect(owner.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      failure: { code: "access_denied" },
    });
    owner.deactivate();
  }
});

it("disposal aborts and clears the event; remount never revives its old result", async () => {
  const stale = pending<Result<PlatformAccountAccessAudit>>();
  const execute = vi
    .fn<AdministrationUseCases["getPlatformAccountAudit"]["execute"]>()
    .mockReturnValueOnce(stale.promise)
    .mockResolvedValueOnce(success(detail));
  const owner = new PlatformAccountAuditController({ execute }, access, event);
  owner.activate();
  owner.deactivate();
  expect(execute.mock.calls[0]?.[2].aborted).toBe(true);
  expect(owner.getSnapshot()).toMatchObject({ stage: "idle", review: null });
  owner.activate();
  await vi.waitFor(() => expect(owner.getSnapshot().review).toBe(detail));
  stale.resolve(failed("session_revoked"));
  await stale.promise;
  expect(owner.getSnapshot().review).toBe(detail);
  owner.deactivate();
  await owner.refresh();
  expect(execute).toHaveBeenCalledTimes(2);
});

it("unexpected errors are safe, retries are explicit and removed listeners stop receiving state", async () => {
  const execute = vi
    .fn<AdministrationUseCases["getPlatformAccountAudit"]["execute"]>()
    .mockRejectedValueOnce(new Error("PRIVATE ADAPTER"))
    .mockResolvedValueOnce(success(detail));
  const owner = new PlatformAccountAuditController({ execute }, access, event);
  const listener = vi.fn();
  const remove = owner.subscribe(listener);
  owner.activate();
  await vi.waitFor(() => expect(owner.getSnapshot().failure?.code).toBe("unexpected_error"));
  expect(JSON.stringify(owner.getSnapshot())).not.toContain("PRIVATE");
  expect(execute).toHaveBeenCalledTimes(1);
  await owner.refresh();
  expect(owner.getSnapshot().review).toBe(detail);
  remove();
  listener.mockClear();
  owner.deactivate();
  expect(listener).not.toHaveBeenCalled();
});
