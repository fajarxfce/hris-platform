import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type {
  LifecycleTemplate,
  LifecycleTemplateId,
} from "../../domain/entities/lifecycle-template";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import {
  LifecycleTemplateEditorController,
  type LifecycleTemplateFields,
} from "./lifecycle-template-editor-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "90000000-abcd-4000-8000-000000000001" as LifecycleTemplateId;
const access = { companyId, permissions: ["people.lifecycle.read", "people.lifecycle.manage"] };
const template = (version = 2): LifecycleTemplate => ({
  id,
  companyId,
  code: "ONBOARD",
  name: "Onboarding",
  kind: "ONBOARDING",
  active: true,
  version,
  tasks: [{ key: "equipment", title: "Equipment", required: true, dueDays: 0 }],
});
const fields = (): LifecycleTemplateFields => ({
  ...template(),
  name: "Revised checklist",
  reason: "Update tasks",
});
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
function fixture(creating = false) {
  const load = vi
    .fn<LifecycleUseCases["loadTemplate"]["execute"]>()
    .mockResolvedValue(success(template()));
  const save = vi
    .fn<LifecycleUseCases["saveTemplate"]["execute"]>()
    .mockResolvedValue(success({ id, version: creating ? 0 : 3 }));
  let sequence = 0;
  const next = vi.fn(() => `30000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller = new LifecycleTemplateEditorController(
    { loadTemplate: { execute: load }, saveTemplate: { execute: save } },
    creating ? { companyId, permissions: ["people.lifecycle.manage"] } : access,
    creating,
    id,
    next,
  );
  controller.activate();
  return { controller, load, save, next };
}
describe("lifecycle template editor ownership", () => {
  it("pins the loaded identity and version, copies nested task values and excludes double submission", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    const held = deferred<Result<MutationReceipt>>();
    f.save.mockReturnValueOnce(held.promise);
    const proposed = {
      ...fields(),
      id: "ignored",
      expectedVersion: 0,
      kind: "OFFBOARDING" as const,
      code: "IGNORED",
      tasks: [{ key: "security", title: "Revoke credentials", required: true, dueDays: -1 }],
    };
    const pending = f.controller.save(proposed);
    await f.controller.save(fields());
    await f.controller.refresh();
    expect(f.save).toHaveBeenCalledOnce();
    expect(f.load).toHaveBeenCalledOnce();
    expect(f.next).toHaveBeenCalledOnce();
    const captured = f.save.mock.lastCall?.[2];
    expect(captured).toMatchObject({ id, expectedVersion: 2, kind: "ONBOARDING", code: "ONBOARD" });
    for (const value of [captured, captured?.tasks, captured?.tasks[0]])
      expect(Object.isFrozen(value)).toBe(true);
    if (proposed.tasks[0]) proposed.tasks[0].title = "Caller mutation";
    expect(captured?.tasks[0]?.title).toBe("Revoke credentials");
    held.resolve(success({ id, version: 3 }));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      receipt: { id, version: 3 },
      operationId: null,
    });
    expect(f.load).toHaveBeenCalledOnce();
    f.controller.deactivate();
  });

  it("retains an uncertain command through MFA and a later conflict until its receipt is recovered", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.save
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(fields());
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.save({ ...fields(), reason: "Different change" });
    await f.controller.refresh();
    expect(f.save).toHaveBeenCalledOnce();
    await f.controller.retrySave();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "mfa_required" },
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

  it("requires a fresh template after a definite conflict and permits correction after validation", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.save.mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(fields());
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.save(fields());
    await f.controller.retrySave();
    expect(f.save).toHaveBeenCalledOnce();
    f.load.mockResolvedValueOnce(success(template(3)));
    await f.controller.refresh();
    f.save.mockResolvedValueOnce(failed("invalid_lifecycle_template"));
    await f.controller.save(fields());
    expect(f.save.mock.lastCall?.[2].expectedVersion).toBe(3);
    expect(f.controller.getSnapshot().stage).toBe("editing");
    f.save.mockResolvedValueOnce(success({ id, version: 4 }));
    await f.controller.save({ ...fields(), reason: "Corrected change" });
    expect(f.next).toHaveBeenCalledTimes(3);
    expect(f.controller.getSnapshot().stage).toBe("saved");
    f.controller.deactivate();
  });

  it("allows manage-only creation without acquiring an existing template", async () => {
    const f = fixture(true);
    expect(f.controller.getSnapshot().stage).toBe("editing");
    await f.controller.save({ ...fields(), kind: "OFFBOARDING", code: "OFFBOARD" });
    expect(f.load).not.toHaveBeenCalled();
    expect(f.save.mock.lastCall?.[2]).toMatchObject({
      id,
      expectedVersion: null,
      kind: "OFFBOARDING",
      code: "OFFBOARD",
    });
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "saved", receipt: { version: 0 } });
    f.controller.deactivate();
  });

  it.each(["success", "failure"] as const)(
    "disposal aborts a pending save and ignores late %s",
    async (outcome) => {
      const f = fixture();
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
      const held = deferred<Result<MutationReceipt>>();
      f.save.mockReturnValueOnce(held.promise);
      const pending = f.controller.save(fields());
      const signal = f.save.mock.lastCall?.[3];
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      if (outcome === "success") held.resolve(success({ id, version: 3 }));
      else held.reject(new Error("Late failure"));
      await pending;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "loading",
        template: null,
        receipt: null,
        operationId: null,
      });
    },
  );

  it("discards superseded reads and removes the editable definition on access denial", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    const held = deferred<Result<LifecycleTemplate>>();
    f.load.mockReturnValueOnce(held.promise).mockResolvedValueOnce(failed("access_denied"));
    const previous = f.controller.refresh();
    const signal = f.load.mock.lastCall?.[2];
    await f.controller.refresh();
    expect(signal?.aborted).toBe(true);
    held.resolve(success(template(3)));
    await previous;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      template: null,
      failure: { code: "access_denied" },
    });
    await f.controller.save(fields());
    expect(f.save).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
});
