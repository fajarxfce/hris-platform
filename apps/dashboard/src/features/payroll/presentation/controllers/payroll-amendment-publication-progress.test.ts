import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  amendmentPublicationDetails,
  amendmentPublicationId,
} from "../../../../../tests/fixtures/payroll-amendment-publications";
import { amendmentId } from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollAmendmentPublicationDetails } from "../../data/mappers/payroll-amendment-publication-mapper";
import type { PayrollAmendmentPublicationDetails } from "../../domain/entities/payroll-amendment-publication-details";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollAmendmentPublicationController } from "./payroll-amendment-publication-controller";

const access: CompanyAccess = {
  companyId: calculationCompany as CompanyId,
  permissions: ["payroll.amend.publish"],
};
function gate<T>() {
  let resolve!: (value: T) => void, reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
async function flush() {
  await Promise.resolve();
  await Promise.resolve();
}
function fixture() {
  const details = (status: Parameters<typeof amendmentPublicationDetails>[0] = "PENDING") =>
      toPayrollAmendmentPublicationDetails(
        amendmentPublicationDetails(status),
        access.companyId,
        amendmentId(),
      ),
    load = {
      execute: vi
        .fn<PayrollUseCases["loadAmendmentPublication"]["execute"]>()
        .mockResolvedValue(success(details())),
    },
    controller = new PayrollAmendmentPublicationController(
      load,
      access,
      amendmentId(),
      amendmentPublicationId(),
    );
  return { details, load, controller };
}
beforeEach(() => vi.useFakeTimers());
afterEach(() => {
  vi.clearAllTimers();
  vi.useRealTimers();
});
describe("visible amendment publication progress", () => {
  it("performs at most 30 delayed updates and requires manual refresh to reset the budget", async () => {
    const f = fixture();
    f.controller.activate();
    f.controller.activate();
    await flush();
    await vi.advanceTimersByTimeAsync(30 * 4000);
    expect(f.load.execute).toHaveBeenCalledTimes(31);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "ready", updatesPaused: true });
    expect(vi.getTimerCount()).toBe(0);
    for (let i = 0; i < 3; i++) {
      f.controller.setVisible(false);
      f.controller.setVisible(true);
    }
    await vi.advanceTimersByTimeAsync(120000);
    expect(f.load.execute).toHaveBeenCalledTimes(31);
    await f.controller.refresh();
    expect(f.load.execute).toHaveBeenCalledTimes(32);
    expect(f.controller.getSnapshot().updatesPaused).toBe(false);
    expect(vi.getTimerCount()).toBe(1);
    f.controller.deactivate();
    expect(vi.getTimerCount()).toBe(0);
  });
  it("does not overlap pending reads or clear retained details during observation", async () => {
    const f = fixture(),
      waiting = gate<Result<PayrollAmendmentPublicationDetails>>();
    f.controller.activate();
    await flush();
    f.load.execute.mockReturnValueOnce(waiting.promise);
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "updating", details: f.details() });
    await vi.advanceTimersByTimeAsync(120000);
    expect(f.load.execute).toHaveBeenCalledTimes(2);
    expect(vi.getTimerCount()).toBe(0);
    waiting.resolve(success(f.details("PUBLISHED")));
    await flush();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "ready",
      details: f.details("PUBLISHED"),
    });
    expect(vi.getTimerCount()).toBe(0);
    f.controller.deactivate();
  });
  it.each(["PUBLISHED", "STOPPED", "EXHAUSTED"] as const)(
    "stops observation for %s and retains its distinct outcome",
    async (status) => {
      const f = fixture();
      f.load.execute.mockResolvedValueOnce(success(f.details(status)));
      f.controller.activate();
      await flush();
      await vi.advanceTimersByTimeAsync(180000);
      expect(f.load.execute).toHaveBeenCalledTimes(1);
      expect(vi.getTimerCount()).toBe(0);
      expect(f.controller.getSnapshot().details).toEqual(f.details(status));
      f.controller.deactivate();
    },
  );
  it("pauses hidden work, ignores its late failure and resumes within the same budget", async () => {
    const f = fixture(),
      waiting = gate<Result<PayrollAmendmentPublicationDetails>>();
    f.controller.activate();
    await flush();
    f.load.execute.mockReturnValueOnce(waiting.promise);
    await vi.advanceTimersByTimeAsync(4000);
    f.controller.setVisible(false);
    expect(f.load.execute.mock.calls[1]?.[3].aborted).toBe(true);
    expect(f.controller.getSnapshot().stage).toBe("ready");
    waiting.reject(new Error("PRIVATE late failure"));
    await flush();
    await vi.advanceTimersByTimeAsync(120000);
    expect(f.load.execute).toHaveBeenCalledTimes(2);
    f.controller.setVisible(true);
    await vi.advanceTimersByTimeAsync(29 * 4000);
    expect(f.load.execute).toHaveBeenCalledTimes(31);
    expect(f.controller.getSnapshot().updatesPaused).toBe(true);
    expect(f.controller.getSnapshot().failure).toBeNull();
    f.controller.deactivate();
  });
  it("does not acquire hidden initial content and aborts an initial read on departure", async () => {
    const f = fixture(),
      waiting = gate<Result<PayrollAmendmentPublicationDetails>>();
    f.controller.setVisible(false);
    f.controller.activate();
    await flush();
    expect(f.load.execute).not.toHaveBeenCalled();
    f.load.execute.mockReturnValueOnce(waiting.promise);
    f.controller.setVisible(true);
    f.controller.deactivate();
    expect(f.load.execute.mock.calls[0]?.[3].aborted).toBe(true);
    waiting.resolve(success(f.details()));
    await flush();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "loading",
      details: null,
      failure: null,
    });
    expect(vi.getTimerCount()).toBe(0);
  });
  it.each(["mfa_required", "access_denied", "payroll_amendment_publication_not_found"])(
    "clears private content on %s and never retries automatically",
    async (code) => {
      const f = fixture();
      f.controller.activate();
      await flush();
      f.load.execute.mockResolvedValueOnce(failed(code));
      await vi.advanceTimersByTimeAsync(4000);
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unavailable",
        details: null,
        failure: { code },
      });
      expect(vi.getTimerCount()).toBe(0);
      f.controller.setVisible(false);
      f.controller.setVisible(true);
      await vi.advanceTimersByTimeAsync(120000);
      expect(f.load.execute).toHaveBeenCalledTimes(2);
      f.controller.deactivate();
    },
  );
  it("keeps the last acknowledged progress on a transient error and requires explicit retry", async () => {
    const f = fixture();
    f.controller.activate();
    await flush();
    f.load.execute.mockRejectedValueOnce(new Error("PRIVATE transport failure"));
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      details: f.details(),
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
    expect(vi.getTimerCount()).toBe(0);
    await f.controller.refresh();
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "ready", failure: null });
    f.controller.deactivate();
  });
  it("replaces an obsolete request and owns subscription cleanup without live-set recursion", async () => {
    const f = fixture(),
      waiting = gate<Result<PayrollAmendmentPublicationDetails>>();
    f.load.execute.mockReturnValueOnce(waiting.promise);
    f.controller.activate();
    let calls = 0,
      unsubscribe = () => {};
    const listener = () => {
      calls += 1;
      unsubscribe();
      unsubscribe = f.controller.subscribe(listener);
    };
    unsubscribe = f.controller.subscribe(listener);
    await f.controller.refresh();
    expect(calls).toBe(2);
    expect(f.load.execute.mock.calls[0]?.[3].aborted).toBe(true);
    waiting.resolve(failed("access_denied"));
    await flush();
    expect(f.controller.getSnapshot().stage).toBe("ready");
    unsubscribe();
    const previous = calls;
    f.controller.deactivate();
    expect(calls).toBe(previous);
    expect(vi.getTimerCount()).toBe(0);
  });
});
