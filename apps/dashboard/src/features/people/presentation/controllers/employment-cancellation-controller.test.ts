import { afterEach, describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { EmployeeId } from "../../domain/entities/employee";
import type { EmploymentRevisionDetails } from "../../domain/entities/employment-revision-details";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmploymentCancellationController } from "./employment-cancellation-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const employeeId = "40000000-0000-4000-8000-000000000001" as EmployeeId;
const details: EmploymentRevisionDetails = {
  employeeId,
  version: 9,
  companyDate: "2026-10-01",
  canCancel: true,
  revision: {
    revision: 2,
    reason: "Scheduled change",
    recordedAt: "2026-09-01T00:00:00Z",
    cancellation: null,
    terms: {
      effectiveFrom: "2027-01-01",
      startDate: "2026-01-01",
      endDate: null,
      status: "ACTIVE",
      contract: "PERMANENT",
    },
  },
};
const owners: EmploymentCancellationController[] = [];
afterEach(() => {
  for (const owner of owners.splice(0)) owner.deactivate();
});
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
function fixture(permissions = ["people.read", "people.manage"]) {
  const loadEmploymentRevision = {
    execute: vi
      .fn<PeopleUseCases["loadEmploymentRevision"]["execute"]>()
      .mockResolvedValue(success(details)),
  };
  const cancelEmploymentRevision = {
    execute: vi
      .fn<PeopleUseCases["cancelEmploymentRevision"]["execute"]>()
      .mockResolvedValue(success({ id: employeeId, version: 10 })),
  };
  let issued = 0;
  const next = vi.fn(() => `60000000-0000-4000-8000-${String(++issued).padStart(12, "0")}`);
  const controller = new EmploymentCancellationController(
    { loadEmploymentRevision, cancelEmploymentRevision },
    { companyId, permissions },
    employeeId,
    "2",
    next,
  );
  owners.push(controller);
  controller.activate();
  return { controller, loadEmploymentRevision, cancelEmploymentRevision, next };
}

describe("employment cancellation ownership", () => {
  it("submits the loaded version and revision once and keeps acknowledgement separate from history reads", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    const held = pending<Result<MutationReceipt>>();
    f.cancelEmploymentRevision.execute.mockReturnValueOnce(held.promise);
    const saving = f.controller.cancelRevision("Cancel scheduled change");
    await f.controller.cancelRevision("Double submission");
    await f.controller.refresh();
    expect(f.cancelEmploymentRevision.execute).toHaveBeenCalledOnce();
    const input = f.cancelEmploymentRevision.execute.mock.lastCall?.[2];
    expect(input).toEqual({
      employeeId,
      expectedVersion: 9,
      revision: 2,
      reason: "Cancel scheduled change",
    });
    expect(Object.isFrozen(input)).toBe(true);
    held.resolve(success({ id: employeeId, version: 10 }));
    await saving;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "cancelled",
      receipt: { id: employeeId, version: 10 },
      operationId: null,
    });
    expect(f.loadEmploymentRevision.execute).toHaveBeenCalledOnce();
  });

  it("preserves an uncertain cancellation across MFA and already-cancelled responses until its original receipt is recovered", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    f.cancelEmploymentRevision.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("revision_already_cancelled"));
    await f.controller.cancelRevision("Cancel scheduled change");
    const first = f.cancelEmploymentRevision.execute.mock.calls[0];
    await f.controller.refresh();
    await f.controller.cancelRevision("Replacement reason");
    for (let attempt = 0; attempt < 2; attempt += 1) {
      await f.controller.retry();
      expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
      expect(f.cancelEmploymentRevision.execute.mock.lastCall?.[1]).toBe(first?.[1]);
      expect(f.cancelEmploymentRevision.execute.mock.lastCall?.[2]).toBe(first?.[2]);
    }
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("cancelled");
    expect(f.next).toHaveBeenCalledOnce();
    expect(f.loadEmploymentRevision.execute).toHaveBeenCalledOnce();
  });

  it("requires reloading after a definite conflict and accepts current server availability", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    f.cancelEmploymentRevision.execute.mockResolvedValueOnce(failed("stale_version"));
    await f.controller.cancelRevision("Cancel scheduled change");
    await f.controller.cancelRevision("Stale retry");
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    expect(f.cancelEmploymentRevision.execute).toHaveBeenCalledOnce();
    f.loadEmploymentRevision.execute.mockResolvedValueOnce(
      success({ ...details, version: 10, canCancel: false }),
    );
    await f.controller.refresh();
    await f.controller.cancelRevision("Already effective");
    expect(f.cancelEmploymentRevision.execute).toHaveBeenCalledOnce();
    expect(f.controller.getSnapshot().details?.canCancel).toBe(false);
  });

  it("does not load or cancel without management and read access", async () => {
    for (const permissions of [
      ["people.read"],
      ["people.manage"],
      ["people.team.read", "people.manage"],
    ]) {
      const f = fixture(permissions);
      await f.controller.cancelRevision("Unauthorized");
      expect(f.controller.getSnapshot().stage).toBe("unavailable");
      expect(f.loadEmploymentRevision.execute).not.toHaveBeenCalled();
      expect(f.cancelEmploymentRevision.execute).not.toHaveBeenCalled();
    }
  });

  it.each([false, true])(
    "ignores a late cancellation after disposal and reactivation (failure: %s)",
    async (rejects) => {
      const f = fixture();
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
      const held = pending<Result<MutationReceipt>>();
      f.cancelEmploymentRevision.execute.mockReturnValueOnce(held.promise);
      const saving = f.controller.cancelRevision("Cancel scheduled change");
      const signal = f.cancelEmploymentRevision.execute.mock.lastCall?.[3];
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      f.controller.activate();
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
      if (rejects) held.reject(new Error("PRIVATE LATE FAILURE"));
      else held.resolve(success({ id: employeeId, version: 10 }));
      await saving;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "reviewing",
        receipt: null,
        operationId: null,
        failure: null,
      });
    },
  );

  it("cancels superseded revision reads and rejects their late versions", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    const held = pending<Result<EmploymentRevisionDetails>>();
    f.loadEmploymentRevision.execute.mockReturnValueOnce(held.promise);
    const old = f.controller.refresh();
    const signal = f.loadEmploymentRevision.execute.mock.lastCall?.[3];
    f.loadEmploymentRevision.execute.mockResolvedValueOnce(success({ ...details, version: 10 }));
    await f.controller.refresh();
    expect(signal?.aborted).toBe(true);
    held.resolve(success(details));
    await old;
    expect(f.controller.getSnapshot().details?.version).toBe(10);
  });
});
