import { describe, expect, it, vi } from "vitest";
import { leaveRecord } from "../../../../../tests/fixtures/leave";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toLeaveRequestDetails } from "../../data/mappers/leave-request-details-mapper";
import type { LeaveRequestIntent } from "../../domain/entities/leave-request-action";
import type { LeaveRequestDetails } from "../../domain/entities/leave-request-details";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveActionController } from "./leave-action-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["leave.approve", "leave.manage"] };
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
function fixture(intent: LeaveRequestIntent = "approve") {
  const request = structuredClone(toLeaveRequestDetails(leaveRecord(), companyId, null));
  const review = vi
    .fn<LeaveUseCases["reviewAction"]["execute"]>()
    .mockResolvedValue(success(request));
  const decide = vi
    .fn<LeaveUseCases["decide"]["execute"]>()
    .mockResolvedValue(success({ id: request.id, version: 1 }));
  const withdraw = vi
    .fn<LeaveUseCases["withdraw"]["execute"]>()
    .mockResolvedValue(success({ id: request.id, version: 1 }));
  const cancel = vi
    .fn<LeaveUseCases["requestCancellation"]["execute"]>()
    .mockResolvedValue(success({ id: request.id, version: 1 }));
  let sequence = 0;
  const next = vi.fn(() => `90000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller = new LeaveActionController(
    {
      reviewAction: { execute: review },
      decide: { execute: decide },
      withdraw: { execute: withdraw },
      requestCancellation: { execute: cancel },
    },
    access,
    request.id,
    intent,
    next,
  );
  controller.activate();
  return { controller, request, review, decide, withdraw, cancel, next };
}
describe("leave action ownership", () => {
  it("captures only immutable command scope and prevents duplicate submission or refresh", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    const held = deferred<Result<MutationReceipt>>();
    f.decide.mockReturnValueOnce(held.promise);
    const work = f.controller.confirm("Reviewed original schedule");
    (f.request.availableActions as string[]).splice(0);
    await f.controller.confirm("Replacement");
    await f.controller.refresh();
    expect(f.decide).toHaveBeenCalledOnce();
    expect(f.review).toHaveBeenCalledOnce();
    expect(f.decide.mock.lastCall?.[2]).toEqual({
      id: f.request.id,
      companyId,
      version: 0,
      status: "PENDING",
      availableActions: ["DECIDE"],
    });
    expect(f.decide.mock.lastCall?.slice(3, 5)).toEqual(["APPROVE", "Reviewed original schedule"]);
    expect(Object.isFrozen(f.decide.mock.lastCall?.[2])).toBe(true);
    expect(Object.isFrozen(f.decide.mock.lastCall?.[2].availableActions)).toBe(true);
    held.resolve(success({ id: f.request.id, version: 1 }));
    await work;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      review: null,
      receipt: { version: 1 },
    });
    f.controller.deactivate();
  });
  it.each(["approve", "reject", "withdraw", "cancel"] as const)(
    "dispatches %s to its dedicated use case",
    async (intent) => {
      const f = fixture(intent);
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
      await f.controller.confirm("Reviewed");
      expect(f.decide).toHaveBeenCalledTimes(intent === "approve" || intent === "reject" ? 1 : 0);
      expect(f.withdraw).toHaveBeenCalledTimes(intent === "withdraw" ? 1 : 0);
      expect(f.cancel).toHaveBeenCalledTimes(intent === "cancel" ? 1 : 0);
      if (intent === "reject") expect(f.decide.mock.lastCall?.[3]).toBe("REJECT");
      f.controller.deactivate();
    },
  );
  it("retains the same operation and review through lost responses, renewed MFA and later conflicts", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    f.decide
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("leave_not_pending"));
    await f.controller.confirm("Original reason");
    await f.controller.retry();
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "leave_not_pending" },
    });
    await f.controller.refresh();
    await f.controller.confirm("Different intent");
    expect(f.review).toHaveBeenCalledOnce();
    expect(f.decide).toHaveBeenCalledTimes(3);
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledOnce();
    for (const call of f.decide.mock.calls) {
      expect(call[1]).toBe(f.decide.mock.calls[0]?.[1]);
      expect(call[2]).toBe(f.decide.mock.calls[0]?.[2]);
      expect(call[4]).toBe("Original reason");
    }
    f.controller.deactivate();
  });
  it("requires a new review after a definite conflict and allows correcting rejected input", async () => {
    const f = fixture("reject");
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    f.decide.mockResolvedValueOnce(failed("invalid_leave_decision"));
    await f.controller.confirm("");
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
    f.decide.mockResolvedValueOnce(failed("stale_version"));
    await f.controller.confirm("Cannot approve");
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.confirm("No new review");
    expect(f.decide).toHaveBeenCalledTimes(2);
    f.review.mockResolvedValueOnce(success({ ...f.request, version: 1 }));
    await f.controller.refresh();
    await f.controller.confirm("Reviewed changed request");
    expect(f.decide.mock.lastCall?.[2].version).toBe(1);
    expect(f.next).toHaveBeenCalledTimes(3);
    f.controller.deactivate();
  });
  it("clears private review after access loss without discarding an uncertain receipt", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    f.decide
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("leave_request_not_found"));
    await f.controller.confirm("Original reason");
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", review: null });
    await f.controller.refresh();
    expect(f.review).toHaveBeenCalledOnce();
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledOnce();
    f.controller.deactivate();
  });
  it.each(["success", "failure"] as const)(
    "disposal cancels pending work and ignores late %s",
    async (outcome) => {
      const f = fixture();
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
      const held = deferred<Result<MutationReceipt>>();
      f.decide.mockReturnValueOnce(held.promise);
      const work = f.controller.confirm("Reviewed");
      const signal = f.decide.mock.lastCall?.[5];
      const listener = vi.fn();
      const detach = f.controller.subscribe(listener);
      detach();
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      if (outcome === "success") held.resolve(success({ id: f.request.id, version: 1 }));
      else held.reject(new Error("Late failure"));
      await work;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "loading",
        review: null,
        receipt: null,
        failure: null,
      });
      expect(listener).not.toHaveBeenCalled();
    },
  );
  it("rejects superseded review results and never replaces cancellation intent after acknowledgement", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    const held = deferred<Result<LeaveRequestDetails>>();
    f.review
      .mockReturnValueOnce(held.promise)
      .mockResolvedValueOnce(success({ ...f.request, status: "CANCELLATION_PENDING" }));
    const old = f.controller.refresh();
    const signal = f.review.mock.lastCall?.[3];
    await f.controller.refresh();
    held.resolve(success(f.request));
    await old;
    expect(signal?.aborted).toBe(true);
    expect(f.controller.getSnapshot().phase).toBe("cancellation");
    await f.controller.confirm("Reviewed cancellation");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      phase: "cancellation",
      review: null,
    });
    f.controller.deactivate();
  });
});
