import { describe, expect, it, vi } from "vitest";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  type IntegrationConfiguration,
  integrationKinds,
} from "../../domain/entities/integration-configuration";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { IntegrationConfigurationController } from "./integration-configuration-controller";

const access = {
  accountId: "20000000-0000-4000-8000-000000000001" as AccountId,
  permissions: ["identity.manage"],
};
const review: IntegrationConfiguration = {
  runtime: "API",
  observedAt: "2026-10-28T16:00:00Z",
  integrations: integrationKinds.map((kind) => ({ kind, enabled: false })),
};
function pending() {
  let resolve!: (value: Result<IntegrationConfiguration>) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<Result<IntegrationConfiguration>>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
describe("integration review ownership", () => {
  it("replaces old work and ignores late values and thrown failures", async () => {
    for (const thrown of [false, true]) {
      const old = pending();
      const execute = vi
        .fn<AdministrationUseCases["loadIntegrationConfiguration"]["execute"]>()
        .mockReturnValueOnce(old.promise)
        .mockResolvedValue(success(review));
      const controller = new IntegrationConfigurationController({ execute }, access);
      controller.activate();
      controller.activate();
      expect(execute).toHaveBeenCalledTimes(1);
      const obsolete = execute.mock.lastCall?.[1];
      await controller.refresh();
      expect(obsolete?.aborted).toBe(true);
      if (thrown) old.reject(new Error("PRIVATE OBSOLETE FAILURE"));
      else old.resolve(failed("session_revoked"));
      await Promise.resolve();
      expect(controller.getSnapshot()).toEqual({ stage: "ready", review, failure: null });
      controller.deactivate();
    }
  });
  it("clears observations during refresh and on denied acquisition", async () => {
    const next = pending();
    const execute = vi
      .fn<AdministrationUseCases["loadIntegrationConfiguration"]["execute"]>()
      .mockResolvedValueOnce(success(review))
      .mockReturnValueOnce(next.promise);
    const controller = new IntegrationConfigurationController({ execute }, access);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    const refreshing = controller.refresh();
    expect(controller.getSnapshot()).toEqual({ stage: "loading", review: null, failure: null });
    next.resolve(failed("platform_administrator_required"));
    await refreshing;
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      failure: { code: "platform_administrator_required" },
    });
    controller.deactivate();
  });
  it("hiding or disposal stops observation until a fresh activation", async () => {
    const old = pending();
    const execute = vi
      .fn<AdministrationUseCases["loadIntegrationConfiguration"]["execute"]>()
      .mockReturnValueOnce(old.promise)
      .mockResolvedValue(success(review));
    const controller = new IntegrationConfigurationController({ execute }, access);
    const listener = vi.fn();
    const unsubscribe = controller.subscribe(listener);
    controller.activate();
    const obsolete = execute.mock.lastCall?.[1];
    controller.deactivate();
    unsubscribe();
    listener.mockClear();
    old.resolve(success(review));
    await Promise.resolve();
    await controller.refresh();
    expect(obsolete?.aborted).toBe(true);
    expect(controller.getSnapshot()).toEqual({ stage: "idle", review: null, failure: null });
    expect(execute).toHaveBeenCalledTimes(1);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    expect(listener).not.toHaveBeenCalled();
    expect(execute.mock.lastCall?.[0]).toBe(access);
    controller.deactivate();
  });
});
