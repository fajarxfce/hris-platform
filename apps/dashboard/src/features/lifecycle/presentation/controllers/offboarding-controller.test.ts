import { describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { LifecycleCaseId } from "../../domain/entities/lifecycle-case";
import type { LifecycleTemplateId } from "../../domain/entities/lifecycle-template";
import type { OffboardingReview } from "../../domain/entities/offboarding-review";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { initialOffboardingState } from "../models/offboarding-state";
import { OffboardingController } from "./offboarding-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const account = "20000000-0000-4000-8000-000000000001" as AccountId;
const id = "a0000000-0000-4000-8000-000000000001" as LifecycleCaseId;
const access = {
  companyId,
  permissions: [
    "people.lifecycle.read",
    "people.lifecycle.manage",
    "people.manage",
    "people.offboard",
  ],
};
const review: OffboardingReview = {
  employmentVersion: 7,
  today: "2026-10-01",
  case: {
    id,
    companyId,
    employee: {
      id: "40000000-0000-4000-8000-000000000001" as EmployeeId,
      employeeNumber: "E-001",
      name: "Employee",
    },
    kind: "OFFBOARDING",
    status: "OPEN",
    version: 3,
    targetDate: "2026-09-30",
    templateId: "90000000-0000-4000-8000-000000000001" as LifecycleTemplateId,
    templateVersion: 0,
    templateName: "Departure",
    createdBy: account,
    createdAt: "2026-09-29T00:00:00Z",
    tasks: [
      {
        key: "equipment",
        title: "Equipment",
        required: true,
        status: "DONE",
        dueDate: "2026-09-30",
        assigneeId: account,
        completedBy: account,
        completedAt: "2026-09-30T01:00:00Z",
      },
    ],
  },
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
function fixture(value = review) {
  const load = vi
    .fn<LifecycleUseCases["loadOffboardingReview"]["execute"]>()
    .mockResolvedValue(success(value));
  const complete = vi
    .fn<LifecycleUseCases["completeOffboarding"]["execute"]>()
    .mockResolvedValue(success({ id, version: value.case.version + 1 }));
  let sequence = 0;
  const next = vi.fn(() => `30000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller = new OffboardingController(
    { loadOffboardingReview: { execute: load }, completeOffboarding: { execute: complete } },
    access,
    id,
    next,
  );
  return { controller, load, complete, next };
}
async function ready(f: ReturnType<typeof fixture>) {
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
}

describe("offboarding request and command ownership", () => {
  it("pins both observed versions and admits one completion without fetching after its receipt", async () => {
    const f = fixture();
    await ready(f);
    const held = deferred<Result<MutationReceipt>>();
    f.complete.mockReturnValueOnce(held.promise);
    const pending = f.controller.complete("Departure reviewed");
    await f.controller.complete("Changed reason");
    await f.controller.retry();
    await f.controller.refresh();
    expect(f.complete).toHaveBeenCalledOnce();
    expect(f.load).toHaveBeenCalledOnce();
    expect(f.complete.mock.lastCall?.[2]).toEqual({
      caseId: id,
      expectedVersion: 3,
      employmentVersion: 7,
      reason: "Departure reviewed",
    });
    expect(Object.isFrozen(f.complete.mock.lastCall?.[2])).toBe(true);
    held.resolve(success({ id, version: 4 }));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "completed",
      receipt: { id, version: 4 },
      operationId: null,
    });
    await f.controller.complete("Another completion");
    expect(f.complete).toHaveBeenCalledOnce();
    expect(f.load).toHaveBeenCalledOnce();
    f.controller.deactivate();
  });
  it("keeps an uncertain command through MFA, employment changes and already-closed replies", async () => {
    const f = fixture();
    await ready(f);
    f.complete
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_employment_version"))
      .mockResolvedValueOnce(failed("lifecycle_case_not_open"));
    await f.controller.complete("Departure reviewed");
    const captured = f.complete.mock.lastCall;
    for (const code of ["mfa_required", "stale_employment_version", "lifecycle_case_not_open"]) {
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", failure: { code } });
      await f.controller.complete("Different payload");
      await f.controller.refresh();
    }
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("completed");
    expect(f.load).toHaveBeenCalledOnce();
    expect(f.next).toHaveBeenCalledOnce();
    for (const call of f.complete.mock.calls) {
      expect(call[1]).toBe(captured?.[1]);
      expect(call[2]).toBe(captured?.[2]);
    }
    f.controller.deactivate();
  });
  it.each([
    "stale_version",
    "stale_employment_version",
    "scheduled_employment_changes_pending",
    "reporting_reassignment_required",
  ])("a definite %s rejection requires a fresh review before another command", async (code) => {
    const f = fixture();
    await ready(f);
    f.complete.mockResolvedValueOnce(failed(code));
    await f.controller.complete("Departure reviewed");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "conflict",
      operationId: null,
      failure: { code },
    });
    await f.controller.complete("Changed reason");
    expect(f.complete).toHaveBeenCalledOnce();
    f.load.mockResolvedValueOnce(
      success({ ...review, employmentVersion: 8, case: { ...review.case, version: 4 } }),
    );
    await f.controller.refresh();
    await f.controller.complete("Departure checked again");
    expect(f.complete.mock.lastCall?.[2]).toMatchObject({
      expectedVersion: 4,
      employmentVersion: 8,
    });
    expect(f.complete.mock.calls[1]?.[1]).not.toBe(f.complete.mock.calls[0]?.[1]);
    f.controller.deactivate();
  });
  it.each(["checklist", "date"])("a review blocked by %s cannot submit", async (kind) => {
    const blocked =
      kind === "date"
        ? { ...review, today: review.case.targetDate }
        : {
            ...review,
            case: {
              ...review.case,
              tasks: review.case.tasks.map((task) => ({ ...task, status: "PENDING" as const })),
            },
          };
    const f = fixture(blocked);
    await ready(f);
    await f.controller.complete("Departure reviewed");
    expect(f.controller.getSnapshot().failure?.code).toBe(
      kind === "date" ? "offboarding_date_not_reached" : "required_lifecycle_tasks_pending",
    );
    expect(f.complete).not.toHaveBeenCalled();
    expect(f.next).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
  it("replaces pending reads and discards late errors instead of clearing a newer review", async () => {
    const f = fixture();
    const held = deferred<Result<OffboardingReview>>();
    f.load.mockReturnValueOnce(held.promise);
    f.controller.activate();
    const first = f.load.mock.lastCall?.[2];
    await f.controller.refresh();
    expect(first?.aborted).toBe(true);
    held.reject(new Error("Late failure"));
    await held.promise.catch(() => undefined);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "reviewing", review, failure: null });
    f.load.mockResolvedValueOnce(failed("offboarding_access_required"));
    const denied = f.controller.refresh();
    expect(f.controller.getSnapshot().review).toBeNull();
    await denied;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      failure: { code: "offboarding_access_required" },
    });
    f.controller.deactivate();
  });
  it.each(["read", "completion"])(
    "disposal aborts a pending %s and ignores a late failure after reactivation",
    async (kind) => {
      const f = fixture();
      const heldRead = deferred<Result<OffboardingReview>>();
      const heldWrite = deferred<Result<MutationReceipt>>();
      let pending: Promise<void> | null = null;
      let signal: AbortSignal | undefined;
      if (kind === "read") {
        f.load.mockReturnValueOnce(heldRead.promise);
        f.controller.activate();
        signal = f.load.mock.lastCall?.[2];
      } else {
        await ready(f);
        f.complete.mockReturnValueOnce(heldWrite.promise);
        pending = f.controller.complete("Departure reviewed");
        signal = f.complete.mock.lastCall?.[3];
      }
      const listener = vi.fn();
      const unsubscribe = f.controller.subscribe(listener);
      unsubscribe();
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      expect(f.controller.getSnapshot()).toEqual(initialOffboardingState);
      await ready(f);
      if (kind === "read") {
        heldRead.reject(new Error("Late read"));
        await heldRead.promise.catch(() => undefined);
      } else {
        heldWrite.reject(new Error("Late completion"));
        await pending;
      }
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "reviewing",
        failure: null,
        receipt: null,
      });
      expect(listener).not.toHaveBeenCalled();
      f.controller.deactivate();
    },
  );
  it("discards a late success after departure and preserves unexpected outcomes for explicit retry", async () => {
    const f = fixture();
    await ready(f);
    f.complete
      .mockResolvedValueOnce(failed("invalid_offboarding"))
      .mockRejectedValueOnce(new Error("Unexpected operation failure"));
    await f.controller.complete("");
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
    await f.controller.complete("Departure reviewed");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "unexpected_error" },
    });
    const held = deferred<Result<MutationReceipt>>();
    f.complete.mockReturnValueOnce(held.promise);
    const pending = f.controller.retry();
    f.controller.deactivate();
    held.resolve(success({ id, version: 4 }));
    await pending;
    expect(f.controller.getSnapshot()).toEqual(initialOffboardingState);
  });
});
