import { describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { LifecycleCaseId } from "../../domain/entities/lifecycle-case";
import type { LifecycleTaskContext } from "../../domain/entities/lifecycle-task-context";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { LifecycleTaskEditorController } from "./lifecycle-task-editor-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const account = "20000000-0000-4000-8000-000000000001" as AccountId;
const id = "a0000000-abcd-4000-8000-000000000001" as LifecycleCaseId;
const context: LifecycleTaskContext = {
  companyId,
  caseId: id,
  caseVersion: 7,
  caseStatus: "OPEN",
  kind: "ONBOARDING",
  employee: {
    id: "b0000000-0000-4000-8000-000000000001" as EmployeeId,
    employeeNumber: "E01",
    name: "Employee",
  },
  task: {
    key: "equipment",
    title: "Review equipment",
    required: true,
    dueDate: "2026-10-01",
    assigneeId: account,
    status: "PENDING",
    completedBy: null,
    completedAt: null,
  },
};
const fields = { status: "DONE" as const, reason: "Equipment received" };
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
function fixture(permissions = ["people.lifecycle.perform"], observed = context) {
  const change = vi
    .fn<LifecycleUseCases["changeTask"]["execute"]>()
    .mockResolvedValue(success({ id, version: 8 }));
  let sequence = 0;
  const next = vi.fn(() => `30000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller = new LifecycleTaskEditorController(
    { execute: change },
    { companyId, permissions },
    account,
    observed,
    next,
  );
  controller.activate();
  return { controller, change, next };
}

describe("lifecycle task editor ownership", () => {
  it("pins the task and observed case version and admits only one immutable in-flight command", async () => {
    const f = fixture();
    const held = deferred<Result<MutationReceipt>>();
    f.change.mockReturnValueOnce(held.promise);
    const proposed = { ...fields, caseId: "FORGED", taskKey: "FORGED", expectedVersion: 0 };
    const pending = f.controller.save(proposed);
    await f.controller.save(fields);
    await f.controller.retry();
    expect(f.change).toHaveBeenCalledOnce();
    expect(f.next).toHaveBeenCalledOnce();
    const input = f.change.mock.lastCall?.[2];
    expect(input).toEqual({ ...fields, caseId: id, taskKey: "equipment", expectedVersion: 7 });
    expect(Object.isFrozen(input)).toBe(true);
    proposed.reason = "Caller mutation";
    expect(input?.reason).toBe(fields.reason);
    held.resolve(success({ id, version: 8 }));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      receipt: { id, version: 8 },
      operationId: null,
    });
    await f.controller.save(fields);
    expect(f.change).toHaveBeenCalledOnce();
    f.controller.deactivate();
  });

  it("retains the original operation through a lost response, MFA, and a conflicting retry", async () => {
    const f = fixture();
    f.change
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(fields);
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.save({ ...fields, reason: "New payload" });
    expect(f.change).toHaveBeenCalledOnce();
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "mfa_required" },
    });
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "stale_version" },
    });
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    for (const call of f.change.mock.calls) {
      expect(call[1]).toBe(f.change.mock.calls[0]?.[1]);
      expect(call[2]).toBe(f.change.mock.calls[0]?.[2]);
    }
    expect(f.next).toHaveBeenCalledOnce();
    f.controller.deactivate();
  });

  it("permits correcting a rejected command but requires a new observed context after a conflict", async () => {
    const f = fixture();
    f.change
      .mockResolvedValueOnce(failed("invalid_lifecycle_change"))
      .mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(fields);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "editing", operationId: null });
    await f.controller.save({ ...fields, reason: "Corrected reason" });
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "conflict", operationId: null });
    await f.controller.save(fields);
    await f.controller.retry();
    expect(f.change).toHaveBeenCalledTimes(2);
    expect(f.next).toHaveBeenCalledTimes(2);
    expect(f.change.mock.calls[0]?.[1]).not.toBe(f.change.mock.calls[1]?.[1]);
    f.controller.deactivate();
    const fresh = fixture(undefined, { ...context, caseVersion: 8 });
    fresh.change.mockResolvedValueOnce(success({ id, version: 9 }));
    await fresh.controller.save(fields);
    expect(fresh.change.mock.lastCall?.[2].expectedVersion).toBe(8);
    expect(fresh.controller.getSnapshot().stage).toBe("saved");
    fresh.controller.deactivate();
  });

  it("does not submit a read-only task or an unsupported transition", async () => {
    for (const f of [
      fixture([]),
      fixture(["people.lifecycle.read"]),
      fixture(undefined, { ...context, task: { ...context.task, assigneeId: null } }),
    ]) {
      await f.controller.save(fields);
      expect(f.change).not.toHaveBeenCalled();
      expect(f.next).not.toHaveBeenCalled();
      f.controller.deactivate();
    }
    const f = fixture(["people.lifecycle.manage"]);
    await f.controller.save({ ...fields, status: "WAIVED" });
    expect(f.controller.getSnapshot().failure?.code).toBe("invalid_lifecycle_change");
    expect(f.change).not.toHaveBeenCalled();
    f.controller.deactivate();
  });

  it.each(["receipt", "failure", "exception"] as const)(
    "disposal aborts a pending write and discards its late %s",
    async (outcome) => {
      const f = fixture();
      const held = deferred<Result<MutationReceipt>>();
      f.change.mockReturnValueOnce(held.promise);
      const listener = vi.fn();
      const unsubscribe = f.controller.subscribe(listener);
      const pending = f.controller.save(fields);
      const signal = f.change.mock.lastCall?.[3];
      unsubscribe();
      f.controller.deactivate();
      const calls = listener.mock.calls.length;
      expect(signal?.aborted).toBe(true);
      if (outcome === "receipt") held.resolve(success({ id, version: 8 }));
      else if (outcome === "failure") held.resolve(failed("access_denied"));
      else held.reject(new Error("Late failure"));
      await pending;
      expect(listener).toHaveBeenCalledTimes(calls);
      expect(f.controller.getSnapshot()).toEqual({
        stage: "editing",
        receipt: null,
        operationId: null,
        failure: null,
      });
      await f.controller.retry();
      await f.controller.save(fields);
      expect(f.change).toHaveBeenCalledOnce();
    },
  );

  it("does not let a disposed command overwrite a reactivated owner's result", async () => {
    const f = fixture();
    const held = deferred<Result<MutationReceipt>>();
    f.change.mockReturnValueOnce(held.promise);
    const old = f.controller.save(fields);
    f.controller.deactivate();
    f.controller.activate();
    await f.controller.save(fields);
    held.reject(new Error("Late failure"));
    await old;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "saved", receipt: { version: 8 } });
    f.controller.deactivate();
  });
});
