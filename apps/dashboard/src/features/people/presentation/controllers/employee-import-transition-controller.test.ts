import { describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { EmployeeImportId } from "../../domain/entities/employee-import";
import type { EmployeeImportAction } from "../../domain/entities/employee-import-change";
import type { EmployeeImportSummary } from "../../domain/entities/employee-import-summary";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { initialEmployeeImportTransitionState } from "../models/employee-import-transition-state";
import { EmployeeImportTransitionController } from "./employee-import-transition-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "30000000-0000-4000-8000-000000000001" as EmployeeImportId;
const access = {
  companyId,
  permissions: ["people.import", "people.manage", "people.profile.read", "people.profile.manage"],
};
const review: EmployeeImportSummary = {
  batch: {
    id,
    companyId,
    fileName: "intake.csv",
    sourceHash: "a".repeat(64),
    rowCount: 3,
    status: "REVIEW",
    jobId: id,
    version: 1,
    createdBy: "20000000-0000-4000-8000-000000000001" as AccountId,
    createdAt: "2026-10-01T00:00:00Z",
    reason: "Intake",
  },
  counts: { PENDING: 0, READY: 2, INVALID: 1, APPLIED: 0, REJECTED: 0 },
  jobStatus: "SUCCEEDED",
  cancellationRequested: false,
  availableActions: ["apply", "cancel"],
};
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
function fixture(action: EmployeeImportAction = "apply", summary = review) {
  const load = vi
    .fn<PeopleUseCases["loadEmployeeImport"]["execute"]>()
    .mockResolvedValue(success(summary));
  const apply = vi
    .fn<PeopleUseCases["applyEmployeeImport"]["execute"]>()
    .mockResolvedValue(success({ id, version: 2 }));
  const resume = vi
    .fn<PeopleUseCases["resumeEmployeeImport"]["execute"]>()
    .mockResolvedValue(success({ id, version: 2 }));
  const cancel = vi
    .fn<PeopleUseCases["cancelEmployeeImport"]["execute"]>()
    .mockResolvedValue(success({ id, version: 2 }));
  let sequence = 0;
  const next = vi.fn(() => `40000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller = new EmployeeImportTransitionController(
    {
      loadEmployeeImport: { execute: load },
      applyEmployeeImport: { execute: apply },
      resumeEmployeeImport: { execute: resume },
      cancelEmployeeImport: { execute: cancel },
    },
    access,
    id,
    action,
    next,
  );
  return { controller, load, apply, resume, cancel, next };
}
async function ready(f: ReturnType<typeof fixture>) {
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
}
describe("employee import transition ownership", () => {
  it("requires partial confirmation, pins one apply payload and clears the review after acknowledgement", async () => {
    const f = fixture();
    await ready(f);
    await f.controller.apply("Reviewed", false);
    expect(f.controller.getSnapshot().failure?.code).toBe("employee_import_has_invalid_rows");
    expect(f.apply).not.toHaveBeenCalled();
    expect(f.next).not.toHaveBeenCalled();
    const held = deferred<Result<MutationReceipt>>();
    f.apply.mockReturnValueOnce(held.promise);
    const pending = f.controller.apply("Reviewed", true);
    await f.controller.apply("Other", false);
    await f.controller.cancel("Other");
    await f.controller.retry();
    await f.controller.refresh();
    expect(f.apply).toHaveBeenCalledOnce();
    expect(f.load).toHaveBeenCalledOnce();
    expect(f.cancel).not.toHaveBeenCalled();
    expect(f.apply.mock.lastCall?.[2]).toEqual({
      importId: id,
      expectedVersion: 1,
      reason: "Reviewed",
      allowPartial: true,
    });
    expect(Object.isFrozen(f.apply.mock.lastCall?.[2])).toBe(true);
    held.resolve(success({ id, version: 2 }));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      review: null,
      receipt: { id, version: 2 },
      operationId: null,
    });
    expect(f.load).toHaveBeenCalledOnce();
    f.controller.deactivate();
  });
  it("retains an uncertain apply through MFA and terminal replies without changing its confirmation", async () => {
    const f = fixture();
    await ready(f);
    f.apply
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_version"))
      .mockResolvedValueOnce(failed("employee_import_is_terminal"));
    await f.controller.apply("Reviewed", true);
    for (const code of ["mfa_required", "stale_version", "employee_import_is_terminal"]) {
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", failure: { code } });
      await f.controller.apply("Changed", false);
      await f.controller.refresh();
    }
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledOnce();
    expect(f.load).toHaveBeenCalledOnce();
    for (const call of f.apply.mock.calls) {
      expect(call[1]).toBe(f.apply.mock.calls[0]?.[1]);
      expect(call[2]).toBe(f.apply.mock.calls[0]?.[2]);
    }
    f.controller.deactivate();
  });
  it("a first stale rejection requires a new review, version and operation", async () => {
    const f = fixture();
    await ready(f);
    f.apply.mockResolvedValueOnce(failed("stale_version"));
    await f.controller.apply("Reviewed", true);
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.apply("Changed", true);
    expect(f.apply).toHaveBeenCalledOnce();
    f.load.mockResolvedValueOnce(success({ ...review, batch: { ...review.batch, version: 2 } }));
    await f.controller.refresh();
    await f.controller.apply("Reviewed again", true);
    expect(f.apply.mock.calls[1]?.[2].expectedVersion).toBe(2);
    expect(f.next).toHaveBeenCalledTimes(2);
    f.controller.deactivate();
  });
  it.each(["cancel", "resume"] as const)(
    "%s owns only its allowed action and recovers the original receipt",
    async (action) => {
      const f = fixture(action, {
        ...review,
        availableActions: [action],
        jobStatus: action === "resume" ? "FAILED" : "RUNNING",
      });
      await ready(f);
      f[action].mockResolvedValueOnce(failed("connection_unavailable"));
      await f.controller[action]("Reviewed");
      await f.controller[action]("Changed");
      await f.controller.retry();
      expect(f[action]).toHaveBeenCalledTimes(2);
      expect(f[action].mock.calls[1]?.[2]).toBe(f[action].mock.calls[0]?.[2]);
      expect(f.apply).not.toHaveBeenCalled();
      expect(f.controller.getSnapshot().stage).toBe("saved");
      f.controller.deactivate();
    },
  );
  it.each(["apply", "resume", "cancel"] as const)(
    "%s cannot bypass available actions from the review",
    async (action) => {
      const f = fixture(action, { ...review, availableActions: [] });
      await ready(f);
      if (action === "apply") await f.controller.apply("Reviewed", true);
      else await f.controller[action]("Reviewed");
      expect(f.controller.getSnapshot().failure?.code).toBe("employee_import_action_unavailable");
      expect(f.next).not.toHaveBeenCalled();
      f.controller.deactivate();
    },
  );
  it.each(["success", "failure"])(
    "disposal aborts pending mutations and rejects late %s",
    async (outcome) => {
      const f = fixture();
      await ready(f);
      const held = deferred<Result<MutationReceipt>>();
      f.apply.mockReturnValueOnce(held.promise);
      const pending = f.controller.apply("Reviewed", true);
      const signal = f.apply.mock.lastCall?.[3];
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      if (outcome === "success") held.resolve(success({ id, version: 2 }));
      else held.reject(new Error("Late failure"));
      await pending;
      expect(f.controller.getSnapshot()).toBe(initialEmployeeImportTransitionState);
      await f.controller.retry();
      expect(f.apply).toHaveBeenCalledOnce();
    },
  );
  it("superseded reads cannot restore stale private summaries or leak listeners", async () => {
    const f = fixture();
    const old = deferred<Result<EmployeeImportSummary>>();
    f.load.mockReturnValueOnce(old.promise);
    const listener = vi.fn();
    const unsubscribe = f.controller.subscribe(listener);
    f.controller.activate();
    f.controller.activate();
    const firstSignal = f.load.mock.calls[0]?.[2];
    await f.controller.refresh();
    expect(firstSignal?.aborted).toBe(true);
    old.reject(new Error("obsolete read"));
    await Promise.resolve();
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "reviewing", failure: null });
    unsubscribe();
    const calls = listener.mock.calls.length;
    f.controller.deactivate();
    expect(listener).toHaveBeenCalledTimes(calls);
  });
});
