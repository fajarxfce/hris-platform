import { describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { LifecycleCaseId } from "../../domain/entities/lifecycle-case";
import type { LifecycleTaskContext } from "../../domain/entities/lifecycle-task-context";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { LifecycleTaskAssignmentController } from "./lifecycle-task-assignment-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "a0000000-0000-4000-8000-000000000001" as LifecycleCaseId;
const account = "20000000-0000-4000-8000-000000000001" as AccountId;
const target = "20000000-0000-4000-8000-000000000002";
const context: LifecycleTaskContext = {
  companyId,
  caseId: id,
  caseVersion: 2,
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
const fields = { assigneeId: target, reason: "Assign review" };
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
function fixture(permissions = ["people.lifecycle.manage"], observed = context) {
  const assign = vi
    .fn<LifecycleUseCases["assignTask"]["execute"]>()
    .mockResolvedValue(success({ id, version: 3 }));
  let sequence = 0;
  const next = vi.fn(() => `30000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller = new LifecycleTaskAssignmentController(
    { execute: assign },
    { companyId, permissions },
    observed,
    next,
  );
  controller.activate();
  return { controller, assign, next };
}

describe("lifecycle assignment ownership", () => {
  it("pins one task/version and immutable target while excluding double submission", async () => {
    const f = fixture();
    const held = deferred<Result<MutationReceipt>>();
    f.assign.mockReturnValueOnce(held.promise);
    const proposed = { ...fields, caseId: "FORGED", taskKey: "FORGED", expectedVersion: 0 };
    const pending = f.controller.save(proposed);
    proposed.assigneeId = "Caller mutation";
    await f.controller.save(fields);
    await f.controller.retry();
    expect(f.assign).toHaveBeenCalledOnce();
    expect(f.next).toHaveBeenCalledOnce();
    const input = f.assign.mock.lastCall?.[2];
    expect(input).toEqual({ ...fields, caseId: id, taskKey: "equipment", expectedVersion: 2 });
    expect(Object.isFrozen(input)).toBe(true);
    held.resolve(success({ id, version: 3 }));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      receipt: { id, version: 3 },
      operationId: null,
    });
    f.controller.deactivate();
  });
  it("retains an uncertain assignment through MFA and candidate rejection until its receipt is recovered", async () => {
    const f = fixture();
    f.assign
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("lifecycle_assignee_unavailable"));
    await f.controller.save(fields);
    await f.controller.save({ assigneeId: null, reason: "Different command" });
    expect(f.assign).toHaveBeenCalledOnce();
    await f.controller.retry();
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "lifecycle_assignee_unavailable" },
    });
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    for (const call of f.assign.mock.calls) {
      expect(call[1]).toBe(f.assign.mock.calls[0]?.[1]);
      expect(call[2]).toBe(f.assign.mock.calls[0]?.[2]);
    }
    expect(f.next).toHaveBeenCalledOnce();
    f.controller.deactivate();
  });
  it("allows a different target after a definite candidate rejection but blocks edits on stale versions", async () => {
    const f = fixture();
    f.assign
      .mockResolvedValueOnce(failed("lifecycle_assignee_unavailable"))
      .mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(fields);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "editing", operationId: null });
    await f.controller.save({ assigneeId: null, reason: "Remove assignment" });
    expect(f.assign.mock.lastCall?.[2].assigneeId).toBeNull();
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.save(fields);
    await f.controller.retry();
    expect(f.assign).toHaveBeenCalledTimes(2);
    expect(f.next).toHaveBeenCalledTimes(2);
    f.controller.deactivate();
  });
  it("does not assign without management or outside the observed open pending task", async () => {
    for (const f of [
      fixture([]),
      fixture(["people.lifecycle.perform"]),
      fixture(undefined, { ...context, caseStatus: "COMPLETED" }),
      fixture(undefined, { ...context, caseStatus: "CANCELLED" }),
      fixture(undefined, { ...context, caseVersion: Number.MAX_SAFE_INTEGER }),
      fixture(undefined, { ...context, task: { ...context.task, status: "DONE" } }),
      fixture(undefined, {
        ...context,
        companyId: "10000000-0000-4000-8000-000000000002" as CompanyId,
      }),
    ]) {
      expect(f.controller.assignable).toBe(false);
      await f.controller.save(fields);
      expect(f.assign).not.toHaveBeenCalled();
      f.controller.deactivate();
    }
  });
  it.each(["success", "failure"] as const)(
    "aborts on disposal and ignores a late %s",
    async (outcome) => {
      const f = fixture();
      const held = deferred<Result<MutationReceipt>>();
      f.assign.mockReturnValueOnce(held.promise);
      const pending = f.controller.save(fields);
      const signal = f.assign.mock.lastCall?.[3];
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      if (outcome === "success") held.resolve(success({ id, version: 3 }));
      else held.reject(new Error("Late failure"));
      await pending;
      expect(f.controller.getSnapshot()).toEqual({
        stage: "editing",
        failure: null,
        receipt: null,
        operationId: null,
      });
      await f.controller.retry();
      await f.controller.save(fields);
      expect(f.assign).toHaveBeenCalledOnce();
    },
  );
});
