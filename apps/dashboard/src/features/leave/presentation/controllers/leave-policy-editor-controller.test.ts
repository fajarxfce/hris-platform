import { expect, it, vi } from "vitest";
import {
  leavePolicyRecord,
  leavePolicyReviewPage,
} from "../../../../../tests/fixtures/leave-policies";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toLeavePolicyReview } from "../../data/mappers/leave-policy-definition-mapper";
import type { LeavePolicyId } from "../../domain/entities/leave-policy-definition";
import type { LeavePolicyReview } from "../../domain/entities/leave-policy-review";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import {
  LeavePolicyEditorController,
  type LeavePolicyFields,
} from "./leave-policy-editor-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["leave.manage"] };
const raw = leavePolicyRecord(0, 1, 23);
const policy = toLeavePolicyReview(
  leavePolicyReviewPage(raw),
  companyId,
  raw.current.id as LeavePolicyId,
  null,
);
const fields: LeavePolicyFields = {
  ...policy.current,
  code: "TAMPERED",
  reason: "Reviewed allowance",
  active: true,
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
function fixture(creating = false, permissions = access.permissions) {
  const load = vi.fn<LeaveUseCases["loadPolicy"]["execute"]>().mockResolvedValue(success(policy));
  const save = vi
    .fn<LeaveUseCases["savePolicy"]["execute"]>()
    .mockResolvedValue(success({ id: policy.current.id, version: creating ? 0 : 24 }));
  let sequence = 0;
  const identifier = vi.fn(() => `b7000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller = new LeavePolicyEditorController(
    { loadPolicy: { execute: load }, savePolicy: { execute: save } },
    { ...access, permissions },
    creating,
    policy.current.id,
    identifier,
  );
  return { controller, load, save, identifier };
}

it("creation needs no prior resource read and denies nonadministrators", async () => {
  const own = fixture(true);
  own.controller.activate();
  own.controller.activate();
  expect(own.controller.getSnapshot().stage).toBe("editing");
  await own.controller.save(fields);
  expect(own.load).not.toHaveBeenCalled();
  expect(own.save.mock.lastCall?.[2]).toMatchObject({
    id: policy.current.id,
    expectedVersion: null,
    code: fields.code,
  });
  own.controller.deactivate();
  const denied = fixture(true, ["leave.read"]);
  denied.controller.activate();
  await denied.controller.save(fields);
  expect(denied.controller.getSnapshot().failure?.code).toBe("access_denied");
  expect(denied.save).not.toHaveBeenCalled();
  expect(denied.identifier).not.toHaveBeenCalled();
  denied.controller.deactivate();
});

it("editing observes the latest head, preserves its immutable code and detaches one pending command", async () => {
  const own = fixture();
  own.controller.activate();
  await vi.waitFor(() => expect(own.controller.getSnapshot().stage).toBe("editing"));
  expect(own.load.mock.lastCall?.slice(0, 3)).toEqual([access, policy.current.id, null]);
  const held = deferred<Result<MutationReceipt>>();
  own.save.mockReturnValueOnce(held.promise);
  const contracts: ("PERMANENT" | "FIXED_TERM")[] = ["PERMANENT"];
  const accrual = { frequency: "MONTHLY" as const, daysPerPeriod: "1.5", carryLimitDays: "3.5" };
  const saving = own.controller.save({ ...fields, allowedContracts: contracts, accrual });
  contracts.splice(0);
  accrual.daysPerPeriod = "30";
  await own.controller.save(fields);
  await own.controller.refresh();
  expect(own.save).toHaveBeenCalledTimes(1);
  expect(own.load).toHaveBeenCalledTimes(1);
  expect(own.save.mock.lastCall?.[2]).toMatchObject({
    expectedVersion: 23,
    code: "LEAVE001",
    allowedContracts: ["PERMANENT"],
    accrual: { daysPerPeriod: "1.5" },
  });
  held.resolve(success({ id: policy.current.id, version: 24 }));
  await saving;
  expect(own.controller.getSnapshot()).toMatchObject({
    stage: "saved",
    policy: null,
    operationId: null,
  });
  own.controller.deactivate();
});

it("an unconfirmed save retains its operation through MFA and later conflict without another read", async () => {
  const own = fixture();
  own.controller.activate();
  await vi.waitFor(() => expect(own.controller.getSnapshot().stage).toBe("editing"));
  own.save.mockRejectedValueOnce(new TypeError("Response lost"));
  await own.controller.save(fields);
  const original = own.save.mock.lastCall;
  expect(own.controller.getSnapshot().stage).toBe("unconfirmed");
  await own.controller.refresh();
  await own.controller.save({ ...fields, reason: "Must not replace pending command" });
  own.save.mockResolvedValueOnce(failed("mfa_required"));
  await own.controller.retrySave();
  expect(own.controller.getSnapshot().stage).toBe("unconfirmed");
  own.save.mockResolvedValueOnce(failed("stale_version"));
  await own.controller.retrySave();
  expect(own.controller.getSnapshot().stage).toBe("unconfirmed");
  await own.controller.retrySave();
  expect(own.controller.getSnapshot()).toMatchObject({ stage: "saved", receipt: { version: 24 } });
  expect(own.identifier).toHaveBeenCalledTimes(1);
  expect(own.load).toHaveBeenCalledTimes(1);
  for (const call of own.save.mock.calls) {
    expect(call.slice(0, 3)).toEqual(original?.slice(0, 3));
    expect(call[2]).toBe(original?.[2]);
  }
  own.controller.deactivate();
});

it("a definite version conflict requires an explicit current review and a new operation", async () => {
  const own = fixture();
  own.controller.activate();
  await vi.waitFor(() => expect(own.controller.getSnapshot().stage).toBe("editing"));
  own.save.mockResolvedValueOnce(failed("stale_version"));
  await own.controller.save(fields);
  expect(own.controller.getSnapshot().stage).toBe("conflict");
  await own.controller.retrySave();
  expect(own.save).toHaveBeenCalledTimes(1);
  own.load.mockResolvedValueOnce(
    success({ ...policy, current: { ...policy.current, version: 24 } }),
  );
  await own.controller.refresh();
  await own.controller.save(fields);
  expect(own.save.mock.lastCall?.[2].expectedVersion).toBe(24);
  expect(own.save.mock.calls[0]?.[1]).not.toBe(own.save.mock.calls[1]?.[1]);
  own.controller.deactivate();
});

it("a known validation rejection releases the command for correction", async () => {
  const own = fixture(true);
  own.controller.activate();
  own.save.mockResolvedValueOnce(failed("invalid_leave_accrual_policy"));
  await own.controller.save(fields);
  expect(own.controller.getSnapshot()).toMatchObject({ stage: "editing", operationId: null });
  await own.controller.save({ ...fields, accrual: null });
  expect(own.identifier).toHaveBeenCalledTimes(2);
  expect(own.save.mock.lastCall?.[2].accrual).toBeNull();
  own.controller.deactivate();
});

it.each(["success", "failure"] as const)(
  "disposal cancels a pending save and ignores late %s",
  async (kind) => {
    const own = fixture(true);
    const held = deferred<Result<MutationReceipt>>();
    own.save.mockReturnValueOnce(held.promise);
    const listener = vi.fn();
    const unsubscribe = own.controller.subscribe(listener);
    own.controller.activate();
    const saving = own.controller.save(fields);
    const signal = own.save.mock.lastCall?.[3];
    unsubscribe();
    const count = listener.mock.calls.length;
    own.controller.deactivate();
    if (kind === "success") held.resolve(success({ id: policy.current.id, version: 0 }));
    else held.reject(new Error("Late failure"));
    await saving;
    expect(signal?.aborted).toBe(true);
    expect(listener).toHaveBeenCalledTimes(count);
    expect(own.controller.getSnapshot()).toMatchObject({
      stage: "loading",
      policy: null,
      receipt: null,
      failure: null,
    });
    await own.controller.retrySave();
    expect(own.save).toHaveBeenCalledTimes(1);
  },
);

it("a cancelled old read cannot replace a newer access failure", async () => {
  const own = fixture();
  const held = deferred<Result<LeavePolicyReview>>();
  own.load.mockReturnValueOnce(held.promise);
  own.controller.activate();
  const signal = own.load.mock.lastCall?.[3];
  own.load.mockResolvedValueOnce(failed("access_denied"));
  await own.controller.refresh();
  held.resolve(success(policy));
  await Promise.resolve();
  expect(signal?.aborted).toBe(true);
  expect(own.controller.getSnapshot()).toMatchObject({
    stage: "unavailable",
    policy: null,
    failure: { code: "access_denied" },
  });
  own.controller.deactivate();
});
