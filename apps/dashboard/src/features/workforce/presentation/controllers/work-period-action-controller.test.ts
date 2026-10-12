import { afterEach, describe, expect, it, vi } from "vitest";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toWorkPeriodOverview } from "../../data/mappers/work-period-overview-mapper";
import {
  company,
  identifier,
  periodAccess,
  periodMonth,
  workPeriodOverviewDtoFixture,
  workPeriodOverviewFixture,
} from "../../di/work-period-fixture";
import type { WorkPeriodIntent } from "../../domain/entities/work-period-action-context";
import type { WorkPeriodOverview } from "../../domain/entities/work-period-overview";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { WorkPeriodActionController } from "./work-period-action-controller";

function deferred<T>() {
  let resolve!: (value: Result<T>) => void;
  const promise = new Promise<Result<T>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
const controllers: WorkPeriodActionController[] = [];
afterEach(() => {
  for (const controller of controllers.splice(0)) controller.deactivate();
});
function fixture(intent: WorkPeriodIntent = "close", permissions = periodAccess.permissions) {
  const raw = workPeriodOverviewDtoFixture(intent === "close" ? "OPEN" : "PROCESSING");
  if (intent === "recover" && raw.attempt) {
    raw.attempt.status = "FAILED";
    raw.attempt.finishedAt = "2026-10-12T01:00:10Z";
    raw.availableActions = ["recover", "view_job"];
  }
  const value = toWorkPeriodOverview(raw, company, periodMonth);
  const load = vi
    .fn<WorkforceUseCases["loadWorkPeriodOverview"]["execute"]>()
    .mockResolvedValue(success(value));
  const receipt = {
    id: intent === "close" ? identifier(900) : (value.period?.id ?? ""),
    version: (value.period?.version ?? 0) + 1,
  };
  const close = vi
    .fn<WorkforceUseCases["closeWorkPeriod"]["execute"]>()
    .mockResolvedValue(success(receipt));
  const recover = vi
    .fn<WorkforceUseCases["recoverWorkPeriod"]["execute"]>()
    .mockResolvedValue(success(receipt));
  let sequence = 1000;
  const next = vi.fn(() => identifier(++sequence));
  const controller = new WorkPeriodActionController(
    {
      loadWorkPeriodOverview: { execute: load },
      closeWorkPeriod: { execute: close },
      recoverWorkPeriod: { execute: recover },
    },
    { ...periodAccess, permissions },
    periodMonth,
    intent,
    next,
  );
  controllers.push(controller);
  return {
    controller,
    load,
    close,
    recover,
    next,
    value,
    receipt,
    action: intent === "close" ? close : recover,
  };
}
const ready = (controller: WorkPeriodActionController) =>
  vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("reviewing"));

describe("work period commands", () => {
  it("does not acquire a review without closing access or an active owner", async () => {
    const f = fixture("close", ["workforce.read"]);
    await f.controller.refresh();
    await f.controller.confirm("Unused");
    expect(f.load).not.toHaveBeenCalled();
    f.controller.activate();
    f.controller.activate();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      failure: { code: "access_denied" },
    });
    expect(f.load).not.toHaveBeenCalled();
    expect(f.next).not.toHaveBeenCalled();
  });

  it.each(["close", "recover"] as const)(
    "holds one immutable %s command across a lost response and later rejections",
    async (intent) => {
      const f = fixture(intent),
        held = deferred<MutationReceipt>();
      f.action
        .mockReturnValueOnce(held.promise)
        .mockResolvedValueOnce(failed("mfa_required"))
        .mockResolvedValueOnce(failed("stale_version"));
      f.controller.activate();
      f.controller.activate();
      await ready(f.controller);
      const submit = f.controller.confirm("Original reason");
      expect(f.controller.getSnapshot().stage).toBe("submitting");
      await f.controller.confirm("Replacement");
      await f.controller.retry();
      await f.controller.refresh();
      expect(f.action).toHaveBeenCalledOnce();
      const context = f.action.mock.lastCall?.[2];
      expect(Object.isFrozen(context)).toBe(true);
      expect(context).not.toHaveProperty("attempt");
      held.resolve(failed("connection_unavailable"));
      await submit;
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        overview: null,
        context: null,
        failure: { code: "mfa_required" },
      });
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        overview: null,
        failure: { code: "stale_version" },
      });
      const receipt = deferred<MutationReceipt>();
      f.action.mockReturnValueOnce(receipt.promise);
      const retry = f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({ stage: "submitting", overview: null });
      receipt.resolve(success(f.receipt));
      await retry;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "saved",
        overview: null,
        context: null,
        operationId: null,
        receipt: f.receipt,
      });
      expect(f.next).toHaveBeenCalledOnce();
      expect(f.load).toHaveBeenCalledOnce();
      for (const call of f.action.mock.calls)
        expect(call.slice(0, 4)).toEqual(f.action.mock.calls[0]?.slice(0, 4));
      expect(intent === "close" ? f.recover : f.close).not.toHaveBeenCalled();
    },
  );

  it("requires a fresh explicit review after a definite conflict and never resubmits automatically", async () => {
    const f = fixture();
    f.action.mockResolvedValueOnce(failed("stale_version"));
    f.controller.activate();
    await ready(f.controller);
    await f.controller.confirm("Initial reason");
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.confirm("Replacement");
    await f.controller.retry();
    expect(f.action).toHaveBeenCalledOnce();
    f.load.mockResolvedValueOnce(success(workPeriodOverviewFixture("REVIEW_REQUIRED")));
    await f.controller.refresh();
    expect(f.controller.getSnapshot().context?.version).toBe(2);
    await f.controller.confirm("Reviewed again");
    expect(f.action.mock.calls[1]?.[1]).not.toBe(f.action.mock.calls[0]?.[1]);
    expect(f.action.mock.calls[1]?.[2].version).toBe(2);
  });

  it("does not authorize a form when the reviewed action is unavailable", async () => {
    const f = fixture();
    f.load.mockResolvedValueOnce(success(workPeriodOverviewFixture("CLOSED")));
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("unavailable"));
    await f.controller.confirm("Attempt");
    expect(f.action).not.toHaveBeenCalled();
    expect(f.controller.getSnapshot().overview).toBeNull();
  });

  it("keeps denied review data hidden during a failed replacement read", async () => {
    const f = fixture(),
      held = deferred<WorkPeriodOverview>();
    f.action.mockResolvedValueOnce(failed("access_denied"));
    f.controller.activate();
    await ready(f.controller);
    await f.controller.confirm("Original reason");
    expect(f.controller.getSnapshot().overview).toBeNull();
    f.load.mockReturnValueOnce(held.promise);
    const retry = f.controller.refresh();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "loading",
      overview: null,
      context: null,
    });
    held.resolve(failed("connection_unavailable"));
    await retry;
    expect(f.controller.getSnapshot().overview).toBeNull();
  });

  it("cancels replaced acquisition and disposed writes without publishing late acknowledgements", async () => {
    const f = fixture(),
      old = deferred<WorkPeriodOverview>();
    f.load.mockReturnValueOnce(old.promise);
    f.controller.activate();
    const signal = f.load.mock.lastCall?.[2];
    await f.controller.refresh();
    expect(signal?.aborted).toBe(true);
    old.resolve(failed("mfa_required"));
    await ready(f.controller);
    const held = deferred<MutationReceipt>();
    f.action.mockReturnValueOnce(held.promise);
    const submit = f.controller.confirm("Disposed request"),
      writeSignal = f.action.mock.lastCall?.[4];
    f.controller.deactivate();
    expect(writeSignal?.aborted).toBe(true);
    held.resolve(success(f.receipt));
    await submit;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "loading",
      receipt: null,
      failure: null,
      overview: null,
    });
    await f.controller.retry();
    expect(f.action).toHaveBeenCalledOnce();
  });

  it("treats a thrown write as unconfirmed and preserves its original command", async () => {
    const f = fixture();
    f.action.mockRejectedValueOnce(new Error("PRIVATE VALUE"));
    f.controller.activate();
    await ready(f.controller);
    await f.controller.confirm("Original reason");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
    await f.controller.retry();
    expect(f.action.mock.calls[1]?.slice(0, 4)).toEqual(f.action.mock.calls[0]?.slice(0, 4));
  });
});
