import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { ClientPolicyReview } from "../../domain/entities/client-policy-review";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { ClientPolicyController } from "./client-policy-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["settings.manage"] };
const review: ClientPolicyReview = {
  settings: {
    companyId,
    latest: null,
    effective: {
      version: null,
      enabledModules: [],
      minimumBuilds: { android: 0, ios: 0, web: 0 },
      maintenance: null,
      maintenanceActive: false,
      evaluatedAt: "2026-10-10T00:00:00Z",
      validUntil: "2026-10-10T00:01:00Z",
    },
  },
  selected: null,
};
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}

describe("client policy controller ownership", () => {
  it("aborts replaced work and ignores both late results and failures", async () => {
    for (const failure of [false, true]) {
      const old = pending<Result<ClientPolicyReview>>();
      const execute = vi
        .fn<AdministrationUseCases["loadClientPolicy"]["execute"]>()
        .mockReturnValueOnce(old.promise)
        .mockResolvedValue(success(review));
      const controller = new ClientPolicyController({ execute }, access, "2");
      controller.activate();
      controller.activate();
      expect(execute).toHaveBeenCalledTimes(1);
      expect(execute.mock.lastCall?.[1]).toBe("2");
      const obsolete = execute.mock.lastCall?.[2];
      await controller.refresh();
      expect(obsolete?.aborted).toBe(true);
      if (failure) old.reject(new Error("PRIVATE OLD FAILURE"));
      else old.resolve(failed("company_access_denied"));
      await Promise.resolve();
      expect(controller.getSnapshot()).toEqual({ stage: "ready", review, failure: null });
      expect(execute).toHaveBeenCalledTimes(2);
      controller.deactivate();
    }
  });

  it("clears configuration before refresh and after access loss", async () => {
    const next = pending<Result<ClientPolicyReview>>();
    const execute = vi
      .fn<AdministrationUseCases["loadClientPolicy"]["execute"]>()
      .mockResolvedValueOnce(success(review))
      .mockReturnValueOnce(next.promise);
    const controller = new ClientPolicyController({ execute }, access, null);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    const refreshed = controller.refresh();
    expect(controller.getSnapshot()).toEqual({ stage: "loading", review: null, failure: null });
    next.resolve(failed("company_access_denied"));
    await refreshed;
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      failure: { code: "company_access_denied" },
    });
    controller.deactivate();
  });

  it("cancels on disposal and permits remount without restoring a previous lifetime", async () => {
    const old = pending<Result<ClientPolicyReview>>();
    const execute = vi
      .fn<AdministrationUseCases["loadClientPolicy"]["execute"]>()
      .mockReturnValueOnce(old.promise)
      .mockResolvedValue(success(review));
    const controller = new ClientPolicyController({ execute }, access, null);
    controller.activate();
    const signal = execute.mock.lastCall?.[2];
    controller.deactivate();
    expect(signal?.aborted).toBe(true);
    expect(controller.getSnapshot()).toEqual({ stage: "idle", review: null, failure: null });
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().review).toBe(review));
    old.resolve(failed("session_revoked"));
    await old.promise;
    expect(controller.getSnapshot().review).toBe(review);
    controller.deactivate();
    await controller.refresh();
    expect(execute).toHaveBeenCalledTimes(2);
  });

  it("contains adapter errors without automatic retries and releases removed listeners", async () => {
    const execute = vi
      .fn<AdministrationUseCases["loadClientPolicy"]["execute"]>()
      .mockRejectedValueOnce(new Error("PRIVATE DETAIL"))
      .mockResolvedValue(success(review));
    const controller = new ClientPolicyController({ execute }, access, null);
    const listener = vi.fn();
    const unsubscribe = controller.subscribe(listener);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().failure?.code).toBe("unexpected_error"));
    expect(JSON.stringify(controller.getSnapshot())).not.toContain("PRIVATE");
    expect(execute).toHaveBeenCalledTimes(1);
    await controller.refresh();
    expect(controller.getSnapshot().review).toBe(review);
    unsubscribe();
    listener.mockClear();
    controller.deactivate();
    expect(listener).not.toHaveBeenCalled();
  });
});
