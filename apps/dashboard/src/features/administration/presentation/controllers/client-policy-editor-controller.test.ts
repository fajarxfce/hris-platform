import { describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { ClientPolicyChange } from "../../domain/entities/client-policy-change";
import type { ClientPolicyReview } from "../../domain/entities/client-policy-review";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { ClientPolicyEditorController } from "./client-policy-editor-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["settings.manage"] };
const review = (version = 1): ClientPolicyReview => ({
  settings: {
    companyId,
    latest: {
      companyId,
      version,
      activateAt: "2026-11-01T00:00:00Z",
      disabledModules: ["PAYROLL"],
      minimumBuilds: { android: 42, ios: 0, web: 0 },
      maintenance: null,
      recordedAt: "2026-10-01T00:00:00Z",
      reason: "Prior policy",
      actorId: "20000000-0000-4000-8000-000000000001" as AccountId,
    },
    effective: {
      version: 0,
      enabledModules: ["PEOPLE"],
      minimumBuilds: { android: 0, ios: 0, web: 0 },
      maintenance: null,
      maintenanceActive: false,
      evaluatedAt: "2026-10-10T00:00:00Z",
      validUntil: "2026-10-10T00:01:00Z",
    },
  },
  selected: null,
});
const fields = (): Omit<ClientPolicyChange, "expectedVersion"> => ({
  activateAt: null,
  disabledModules: ["PAYROLL"],
  minimumBuilds: { android: 43, ios: 19, web: 0 },
  maintenance: { startsAt: "2026-11-01T00:00:00Z", endsAt: "2026-11-01T00:30:00Z" },
  reason: "Update support",
});
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
function fixture() {
  const load = vi
    .fn<AdministrationUseCases["loadClientPolicy"]["execute"]>()
    .mockResolvedValue(success(review()));
  const save = vi
    .fn<AdministrationUseCases["saveClientPolicy"]["execute"]>()
    .mockResolvedValue(success({ id: companyId, version: 2 }));
  let sequence = 0;
  const next = vi.fn(() => `30000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller = new ClientPolicyEditorController(
    { loadClientPolicy: { execute: load }, saveClientPolicy: { execute: save } },
    access,
    next,
  );
  controller.activate();
  return { controller, load, save, next };
}
describe("client policy editor ownership", () => {
  it("pins the configured head and one immutable command while excluding duplicate saves and refreshes", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    expect(f.load.mock.lastCall?.slice(0, 2)).toEqual([access, null]);
    const held = deferred<Result<MutationReceipt>>();
    f.save.mockReturnValueOnce(held.promise);
    const proposed = { ...fields(), expectedVersion: 0 };
    const pending = f.controller.save(proposed);
    await f.controller.save(fields());
    await f.controller.refresh();
    expect(f.save).toHaveBeenCalledOnce();
    expect(f.load).toHaveBeenCalledOnce();
    expect(f.next).toHaveBeenCalledOnce();
    const captured = f.save.mock.lastCall?.[2];
    expect(captured).toMatchObject({ expectedVersion: 1, activateAt: null });
    expect(Object.isFrozen(captured)).toBe(true);
    expect(Object.isFrozen(captured?.disabledModules)).toBe(true);
    expect(Object.isFrozen(captured?.minimumBuilds)).toBe(true);
    expect(Object.isFrozen(captured?.maintenance)).toBe(true);
    proposed.reason = "Changed after click";
    expect(captured?.reason).toBe("Update support");
    held.resolve(success({ id: companyId, version: 2 }));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      receipt: { version: 2 },
      operationId: null,
    });
    expect(f.load).toHaveBeenCalledOnce();
    f.controller.deactivate();
  });

  it("keeps an uncertain payload through MFA and a later stale rejection until the same receipt is acknowledged", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.save
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("recent_authentication_required"))
      .mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(fields());
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.save({ ...fields(), reason: "Another change" });
    await f.controller.refresh();
    expect(f.save).toHaveBeenCalledOnce();
    await f.controller.retrySave();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "recent_authentication_required" },
    });
    await f.controller.retrySave();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "stale_version" },
    });
    await f.controller.retrySave();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    for (const call of f.save.mock.calls) {
      expect(call[1]).toBe(f.save.mock.calls[0]?.[1]);
      expect(call[2]).toBe(f.save.mock.calls[0]?.[2]);
    }
    expect(f.next).toHaveBeenCalledOnce();
    expect(f.load).toHaveBeenCalledOnce();
    f.controller.deactivate();
  });

  it("requires a new observed head after a definite conflict and permits correction after definite validation", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.save.mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(fields());
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.save(fields());
    await f.controller.retrySave();
    expect(f.save).toHaveBeenCalledOnce();
    f.load.mockResolvedValueOnce(success(review(2)));
    await f.controller.refresh();
    f.save.mockResolvedValueOnce(failed("client_policy_activation_expired"));
    await f.controller.save(fields());
    expect(f.save.mock.lastCall?.[2].expectedVersion).toBe(2);
    expect(f.controller.getSnapshot().stage).toBe("editing");
    f.save.mockResolvedValueOnce(success({ id: companyId, version: 3 }));
    await f.controller.save({ ...fields(), reason: "Immediate replacement" });
    expect(f.next).toHaveBeenCalledTimes(3);
    expect(f.controller.getSnapshot().stage).toBe("saved");
    f.controller.deactivate();
  });

  it("retains null for the first revision and blocks saving at the bounded history limit", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.load.mockResolvedValueOnce(
      success({ ...review(), settings: { ...review().settings, latest: null } }),
    );
    await f.controller.refresh();
    f.save.mockResolvedValueOnce(success({ id: companyId, version: 0 }));
    await f.controller.save(fields());
    expect(f.save.mock.lastCall?.[2].expectedVersion).toBeNull();
    f.load.mockResolvedValueOnce(success(review(9999)));
    await f.controller.refresh();
    expect(f.controller.getSnapshot()).toMatchObject({
      failure: { code: "client_policy_revision_limit" },
    });
    await f.controller.save(fields());
    expect(f.save).toHaveBeenCalledOnce();
    f.controller.deactivate();
  });

  it.each(["success", "failure"] as const)(
    "disposal cancels a pending save and ignores late %s",
    async (outcome) => {
      const f = fixture();
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
      const held = deferred<Result<MutationReceipt>>();
      f.save.mockReturnValueOnce(held.promise);
      const pending = f.controller.save(fields());
      const signal = f.save.mock.lastCall?.[3];
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      if (outcome === "success") held.resolve(success({ id: companyId, version: 2 }));
      else held.reject(new Error("Late failure"));
      await pending;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "loading",
        settings: null,
        receipt: null,
        operationId: null,
      });
    },
  );

  it("replaced reads and access denial cannot restore a former configuration", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    const held = deferred<Result<ClientPolicyReview>>();
    f.load.mockReturnValueOnce(held.promise).mockResolvedValueOnce(failed("access_denied"));
    const previous = f.controller.refresh();
    const signal = f.load.mock.lastCall?.[2];
    await f.controller.refresh();
    expect(signal?.aborted).toBe(true);
    held.resolve(success(review(3)));
    await previous;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      settings: null,
      failure: { code: "access_denied" },
    });
    await f.controller.save(fields());
    expect(f.save).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
});
