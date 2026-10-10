import { describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type {
  ApprovalDelegation,
  ApprovalDelegationId,
} from "../../domain/entities/approval-delegation";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import {
  ApprovalDelegationEditorController,
  type ApprovalDelegationFields,
} from "./approval-delegation-editor-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "90000000-abcd-4000-8000-000000000001" as ApprovalDelegationId;
const actor = "20000000-abcd-4000-8000-000000000001" as AccountId;
const delegate = "20000000-abcd-4000-8000-000000000002" as AccountId;
const access = { companyId, permissions: ["approvals.read", "approvals.manage"] };
const delegation = (version = 2): ApprovalDelegation => ({
  id,
  companyId,
  kind: "LEAVE",
  fromAccount: actor,
  toAccount: delegate,
  validFrom: "2026-10-01T00:00:00.123456Z",
  validUntil: "2026-10-10T00:00:00.123456Z",
  active: false,
  version,
});
const fields = (): ApprovalDelegationFields => ({ ...delegation(), reason: "Review coverage" });
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
    .fn<ApprovalsUseCases["loadDelegationForEdit"]["execute"]>()
    .mockResolvedValue(success(delegation()));
  const save = vi
    .fn<ApprovalsUseCases["saveDelegation"]["execute"]>()
    .mockResolvedValue(success({ id, version: creating ? 0 : 3 }));
  let sequence = 0;
  const next = vi.fn(() => `30000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller = new ApprovalDelegationEditorController(
    { loadDelegationForEdit: { execute: load }, saveDelegation: { execute: save } },
    creating ? { companyId, permissions: ["approvals.read"] } : access,
    actor,
    creating,
    id,
    next,
  );
  controller.activate();
  return { controller, load, save, next };
}
describe("approval delegation editor ownership", () => {
  it("pins the original delegator and version while retaining a detached command and excluding duplicate submits", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    const held = deferred<Result<MutationReceipt>>();
    f.save.mockReturnValueOnce(held.promise);
    const proposed = {
      ...fields(),
      id: "ignored",
      expectedVersion: 0,
      fromAccount: delegate,
      kind: "EXPENSE" as const,
    };
    const pending = f.controller.save(proposed);
    await f.controller.save(fields());
    await f.controller.refresh();
    expect(f.save).toHaveBeenCalledOnce();
    expect(f.load).toHaveBeenCalledOnce();
    expect(f.next).toHaveBeenCalledOnce();
    const captured = f.save.mock.lastCall?.[3];
    expect(captured).toMatchObject({
      id,
      expectedVersion: 2,
      fromAccount: actor,
      kind: "EXPENSE",
      validFrom: delegation().validFrom,
    });
    expect(Object.isFrozen(captured)).toBe(true);
    proposed.reason = "Later caller mutation";
    expect(captured?.reason).toBe("Review coverage");
    held.resolve(success({ id, version: 3 }));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      receipt: { id, version: 3 },
      operationId: null,
    });
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
      expect(call[2]).toBe(f.save.mock.calls[0]?.[2]);
      expect(call[3]).toBe(f.save.mock.calls[0]?.[3]);
    }
    expect(f.next).toHaveBeenCalledOnce();
    expect(f.load).toHaveBeenCalledOnce();
    f.controller.deactivate();
  });

  it("requires a fresh delegation after a definite conflict and permits correction after validation", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.save.mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(fields());
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.save(fields());
    await f.controller.retrySave();
    expect(f.save).toHaveBeenCalledOnce();
    f.load.mockResolvedValueOnce(success(delegation(3)));
    await f.controller.refresh();
    f.save.mockResolvedValueOnce(failed("invalid_delegation"));
    await f.controller.save(fields());
    expect(f.save.mock.lastCall?.[3].expectedVersion).toBe(3);
    expect(f.controller.getSnapshot().stage).toBe("editing");
    f.save.mockResolvedValueOnce(success({ id, version: 4 }));
    await f.controller.save({ ...fields(), reason: "Corrected change" });
    expect(f.next).toHaveBeenCalledTimes(3);
    expect(f.controller.getSnapshot().stage).toBe("saved");
    f.controller.deactivate();
  });

  it("allows own delegation creation without acquiring an existing record", async () => {
    const f = fixture(true);
    expect(f.controller.getSnapshot().stage).toBe("editing");
    await f.controller.save({ ...fields(), kind: "EXPENSE" });
    expect(f.load).not.toHaveBeenCalled();
    expect(f.save.mock.lastCall?.[3]).toMatchObject({
      id,
      expectedVersion: null,
      kind: "EXPENSE",
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
      const signal = f.save.mock.lastCall?.[4];
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      if (outcome === "success") held.resolve(success({ id, version: 3 }));
      else held.reject(new Error("Late failure"));
      await pending;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "loading",
        delegation: null,
        receipt: null,
        operationId: null,
      });
    },
  );

  it("discards superseded reads and removes the editable definition on access denial", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    const held = deferred<Result<ApprovalDelegation>>();
    f.load.mockReturnValueOnce(held.promise).mockResolvedValueOnce(failed("access_denied"));
    const previous = f.controller.refresh();
    const signal = f.load.mock.lastCall?.[3];
    await f.controller.refresh();
    expect(signal?.aborted).toBe(true);
    held.resolve(success(delegation(3)));
    await previous;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      delegation: null,
      failure: { code: "access_denied" },
    });
    await f.controller.save(fields());
    expect(f.save).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
});
