import { describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { ApprovalId, ApprovalRequest } from "../../domain/entities/approval-request";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { ApprovalReassignmentController } from "./approval-reassignment-controller";

const companyId = "10000000-abcd-4000-8000-000000000001" as CompanyId;
const id = "30000000-abcd-4000-8000-000000000001" as ApprovalId;
const actor = "20000000-abcd-4000-8000-000000000001" as AccountId;
const selected = "20000000-abcd-4000-8000-000000000002" as AccountId;
const access = { companyId, permissions: ["approvals.manage"] };
const snapshot = (version = 2): ApprovalRequest => ({
  id,
  companyId,
  version,
  kind: "LEAVE",
  resourceId: "40000000-abcd-4000-8000-000000000001",
  authorId: actor,
  requesterId: null,
  excludedAccountIds: [actor],
  templateId: "50000000-abcd-4000-8000-000000000001",
  templateRevision: 0,
  stages: [{ assignees: [] }],
  currentStep: 0,
  status: "BLOCKED",
  submittedAt: "2026-10-01T00:00:00Z",
});
const selection = () => ({ assignees: [selected], reason: "Reviewer unavailable" });
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
function fixture() {
  const request = snapshot();
  const review = vi
    .fn<ApprovalsUseCases["reviewReassignment"]["execute"]>()
    .mockResolvedValue(success(request));
  const reassign = vi
    .fn<ApprovalsUseCases["reassign"]["execute"]>()
    .mockResolvedValue(success({ id, version: 3 }));
  let sequence = 0;
  const next = vi.fn(() => `90000000-abcd-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller = new ApprovalReassignmentController(
    { reviewReassignment: { execute: review }, reassign: { execute: reassign } },
    access,
    id,
    next,
  );
  controller.activate();
  return { controller, request, review, reassign, next };
}
describe("approval reassignment owner", () => {
  it("detaches the reviewed request and command, and suppresses duplicate submission or reload while pending", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    const held = deferred<Result<MutationReceipt>>();
    f.reassign.mockReturnValueOnce(held.promise);
    const input = selection();
    const work = f.controller.save(input);
    input.assignees.splice(0);
    input.reason = "Caller mutation";
    (f.request.excludedAccountIds as AccountId[]).splice(0);
    await f.controller.save(selection());
    await f.controller.refresh();
    expect(f.reassign).toHaveBeenCalledOnce();
    expect(f.review).toHaveBeenCalledOnce();
    const call = f.reassign.mock.lastCall;
    expect(call?.[2]).toMatchObject({ id, version: 2, excludedAccountIds: [actor] });
    expect(call?.[3]).toEqual(selection());
    for (const value of [
      call?.[2],
      call?.[2].stages,
      call?.[2].stages[0],
      call?.[2].excludedAccountIds,
      call?.[3],
      call?.[3].assignees,
    ])
      expect(Object.isFrozen(value)).toBe(true);
    held.resolve(success({ id, version: 3 }));
    await work;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      request: null,
      receipt: { version: 3 },
    });
    f.controller.deactivate();
  });
  it("retains one command through a lost response, assurance rejection and advanced stage", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.reassign
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("approval_changed"));
    await f.controller.save(selection());
    await f.controller.retry();
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "approval_changed" },
    });
    await f.controller.refresh();
    await f.controller.save({ ...selection(), reason: "Different intent" });
    expect(f.review).toHaveBeenCalledOnce();
    expect(f.reassign).toHaveBeenCalledTimes(3);
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledOnce();
    for (const call of f.reassign.mock.calls)
      for (const index of [1, 2, 3] as const)
        expect(call[index]).toBe(f.reassign.mock.calls[0]?.[index]);
    f.controller.deactivate();
  });
  it("requires a fresh review after a definite conflict and permits correcting a rejected selection", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.reassign.mockResolvedValueOnce(failed("approval_changed"));
    await f.controller.save(selection());
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.save(selection());
    await f.controller.retry();
    expect(f.reassign).toHaveBeenCalledOnce();
    f.review.mockResolvedValueOnce(success(snapshot(3)));
    await f.controller.refresh();
    f.reassign.mockResolvedValueOnce(failed("approver_unavailable"));
    await f.controller.save(selection());
    expect(f.controller.getSnapshot().stage).toBe("editing");
    expect(f.reassign.mock.lastCall?.[2].version).toBe(3);
    f.reassign.mockResolvedValueOnce(success({ id, version: 4 }));
    await f.controller.save(selection());
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledTimes(3);
    f.controller.deactivate();
  });
  it.each(["success", "failure"] as const)(
    "disposal cancels a pending write and ignores a late %s",
    async (outcome) => {
      const f = fixture();
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
      const held = deferred<Result<MutationReceipt>>();
      f.reassign.mockReturnValueOnce(held.promise);
      const work = f.controller.save(selection());
      const signal = f.reassign.mock.lastCall?.[4];
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      if (outcome === "success") held.resolve(success({ id, version: 3 }));
      else held.reject(new Error("Late rejection"));
      await work;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "loading",
        request: null,
        receipt: null,
        failure: null,
      });
    },
  );
  it("drops superseded reviews and prevents editing after access is revoked", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    const held = deferred<Result<ApprovalRequest>>();
    f.review.mockReturnValueOnce(held.promise).mockResolvedValueOnce(failed("access_denied"));
    const work = f.controller.refresh();
    const old = f.review.mock.lastCall?.[2];
    await f.controller.refresh();
    expect(old?.aborted).toBe(true);
    held.resolve(success(snapshot(3)));
    await work;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      request: null,
      failure: { code: "access_denied" },
    });
    await f.controller.save(selection());
    expect(f.reassign).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
});
