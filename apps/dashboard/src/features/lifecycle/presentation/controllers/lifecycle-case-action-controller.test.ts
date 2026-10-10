import { describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { LifecycleCase, LifecycleCaseId } from "../../domain/entities/lifecycle-case";
import type { LifecycleCaseAction } from "../../domain/entities/lifecycle-case-change";
import type { LifecycleTemplateId } from "../../domain/entities/lifecycle-template";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { LifecycleCaseActionController } from "./lifecycle-case-action-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const account = "20000000-0000-4000-8000-000000000001" as AccountId;
const id = "a0000000-0000-4000-8000-000000000001" as LifecycleCaseId;
const access = { companyId, permissions: ["people.lifecycle.read", "people.lifecycle.manage"] };
const details: LifecycleCase = {
  id,
  companyId,
  employee: {
    id: "40000000-0000-4000-8000-000000000001" as EmployeeId,
    employeeNumber: "E-001",
    name: "Employee",
  },
  kind: "ONBOARDING",
  status: "OPEN",
  version: 7,
  targetDate: "2026-10-01",
  templateId: "90000000-0000-4000-8000-000000000001" as LifecycleTemplateId,
  templateVersion: 0,
  templateName: "Onboarding",
  createdBy: account,
  createdAt: "2026-10-01T00:00:00Z",
  tasks: [
    {
      key: "equipment",
      title: "Equipment",
      required: true,
      status: "DONE",
      dueDate: "2026-10-01",
      assigneeId: account,
      completedBy: account,
      completedAt: "2026-10-01T01:00:00Z",
    },
  ],
};
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (value: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
function fixture(action: LifecycleCaseAction = "cancel", record = details) {
  const execute = vi
    .fn<LifecycleUseCases["cancelCase"]["execute"]>()
    .mockResolvedValue(success({ id, version: 8 }));
  let sequence = 0;
  const next = vi.fn(() => `30000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller = new LifecycleCaseActionController({ execute }, access, record, action, next);
  controller.activate();
  return { controller, execute, next };
}
describe("lifecycle case action ownership", () => {
  it.each(["cancel", "completeOnboarding"] as const)(
    "pins one %s command and excludes duplicate submissions",
    async (action) => {
      const f = fixture(action);
      const held = deferred<Result<MutationReceipt>>();
      f.execute.mockReturnValueOnce(held.promise);
      const pending = f.controller.save("Checklist reviewed");
      await f.controller.save("Other reason");
      await f.controller.retry();
      expect(f.execute).toHaveBeenCalledOnce();
      expect(f.execute.mock.lastCall?.[2]).toEqual({
        caseId: id,
        expectedVersion: 7,
        reason: "Checklist reviewed",
      });
      expect(Object.isFrozen(f.execute.mock.lastCall?.[2])).toBe(true);
      held.resolve(success({ id, version: 8 }));
      await pending;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "saved",
        receipt: { id, version: 8 },
        operationId: null,
      });
      await f.controller.save("Another action");
      expect(f.execute).toHaveBeenCalledOnce();
      f.controller.deactivate();
    },
  );
  it("keeps an uncertain action pinned through MFA and already-closed responses", async () => {
    const f = fixture();
    f.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("lifecycle_case_not_open"));
    await f.controller.save("Checklist cancelled");
    const captured = f.execute.mock.lastCall;
    for (const code of ["mfa_required", "lifecycle_case_not_open"]) {
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", failure: { code } });
      await f.controller.save("Changed reason");
    }
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    for (const call of f.execute.mock.calls) {
      expect(call[1]).toBe(captured?.[1]);
      expect(call[2]).toBe(captured?.[2]);
    }
    expect(f.next).toHaveBeenCalledOnce();
    f.controller.deactivate();
  });
  it.each([
    "stale_version",
    "required_lifecycle_tasks_pending",
    "lifecycle_tasks_unresolved",
    "lifecycle_case_not_open",
  ])("requires a new review after a definite %s response", async (code) => {
    const f = fixture("completeOnboarding");
    f.execute.mockResolvedValueOnce(failed(code));
    await f.controller.save("Checklist reviewed");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "conflict",
      failure: { code },
      operationId: null,
    });
    await f.controller.save("Changed reason");
    await f.controller.retry();
    expect(f.execute).toHaveBeenCalledOnce();
    f.controller.deactivate();
  });
  it("allows correction after definite validation but retains a thrown uncertain result", async () => {
    const f = fixture();
    f.execute
      .mockResolvedValueOnce(failed("invalid_lifecycle_change"))
      .mockRejectedValueOnce(new Error("PRIVATE"));
    await f.controller.save("");
    expect(f.controller.getSnapshot().stage).toBe("editing");
    await f.controller.save("Corrected reason");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
    expect(f.execute.mock.calls[1]?.[1]).not.toBe(f.execute.mock.calls[0]?.[1]);
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.execute.mock.lastCall?.[1]).toBe(f.execute.mock.calls[1]?.[1]);
    f.controller.deactivate();
  });
  it("does not submit an ineligible completion or an already closed cancellation", async () => {
    for (const [action, record] of [
      ["completeOnboarding", { ...details, kind: "OFFBOARDING" }],
      [
        "completeOnboarding",
        {
          ...details,
          tasks: details.tasks.map((task) => ({ ...task, status: "PENDING" as const })),
        },
      ],
      ["cancel", { ...details, status: "COMPLETED" }],
    ] as const) {
      const f = fixture(action, record);
      await f.controller.save("Disallowed action");
      expect(f.controller.allowed).toBe(false);
      expect(f.execute).not.toHaveBeenCalled();
      f.controller.deactivate();
    }
  });
  it.each(["success", "failure"] as const)(
    "disposal cancels an action and ignores its late %s",
    async (outcome) => {
      const f = fixture();
      const held = deferred<Result<MutationReceipt>>();
      f.execute.mockReturnValueOnce(held.promise);
      const observer = vi.fn();
      const remove = f.controller.subscribe(observer);
      const pending = f.controller.save("Checklist reviewed");
      const signal = f.execute.mock.lastCall?.[3];
      remove();
      observer.mockClear();
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      if (outcome === "success") held.resolve(success({ id, version: 8 }));
      else held.reject(new Error("Late response"));
      await pending;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "editing",
        failure: null,
        receipt: null,
        operationId: null,
      });
      expect(observer).not.toHaveBeenCalled();
    },
  );
});
