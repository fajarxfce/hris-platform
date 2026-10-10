import { expect, it, vi } from "vitest";
import { leaveEmployeeId } from "../../../../../tests/fixtures/leave";
import {
  balanceEntryId,
  balanceLedgerPage,
  balanceTypeId,
} from "../../../../../tests/fixtures/leave-balances";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toLeaveLedger } from "../../data/mappers/leave-ledger-mapper";
import type { LeaveAdjustmentCatalog } from "../../domain/entities/leave-adjustment-catalog";
import type { LeaveLedger } from "../../domain/entities/leave-ledger";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveAdjustmentCatalogController } from "./leave-adjustment-catalog-controller";
import { LeaveBalanceAdjustmentController } from "./leave-balance-adjustment-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["leave.read", "leave.manage"] };
const review = toLeaveLedger(
  balanceLedgerPage(),
  companyId,
  leaveEmployeeId(),
  balanceTypeId(),
  2026,
  null,
);
const receipt = { id: balanceEntryId(100), version: 0 };
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
function fixture() {
  const load = vi
    .fn<LeaveUseCases["reviewBalanceAdjustment"]["execute"]>()
    .mockResolvedValue(success(review));
  const save = vi
    .fn<LeaveUseCases["adjustBalance"]["execute"]>()
    .mockResolvedValue(success(receipt));
  let sequence = 0;
  const identifier = vi.fn(() => `b7000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller = new LeaveBalanceAdjustmentController(
    {
      reviewBalanceAdjustment: { execute: load },
      adjustBalance: { execute: save },
    },
    access,
    leaveEmployeeId(),
    balanceTypeId(),
    "2026",
    identifier,
  );
  return { controller, load, save, identifier };
}

it("uses the reviewed target/version and blocks duplicate saves or refreshes while awaiting acknowledgement", async () => {
  const own = fixture();
  await own.controller.save("1", "Ignored");
  expect(own.save).not.toHaveBeenCalled();
  own.controller.activate();
  own.controller.activate();
  await vi.waitFor(() => expect(own.controller.getSnapshot().stage).toBe("editing"));
  expect(own.load.mock.lastCall?.slice(0, 4)).toEqual([
    access,
    leaveEmployeeId(),
    balanceTypeId(),
    "2026",
  ]);
  const held = deferred<Result<MutationReceipt>>();
  own.save.mockReturnValueOnce(held.promise);
  const saving = own.controller.save("-0.5", "Reviewed correction");
  await own.controller.save("20", "Must not replace command");
  await own.controller.retry();
  await own.controller.refresh();
  expect(own.save).toHaveBeenCalledTimes(1);
  expect(own.load).toHaveBeenCalledTimes(1);
  expect(own.save.mock.lastCall?.[2]).toEqual({
    employeeId: leaveEmployeeId(),
    typeId: balanceTypeId(),
    year: 2026,
    days: "-0.5",
    reason: "Reviewed correction",
    expectedVersion: 14,
  });
  expect(Object.isFrozen(own.save.mock.lastCall?.[2])).toBe(true);
  held.resolve(success(receipt));
  await saving;
  expect(own.controller.getSnapshot()).toMatchObject({
    stage: "saved",
    review: null,
    operationId: null,
    receipt,
  });
  own.controller.deactivate();
});

it("retains exactly one uncertain command through MFA, a later conflict, and the original receipt", async () => {
  const own = fixture();
  own.controller.activate();
  await vi.waitFor(() => expect(own.controller.getSnapshot().stage).toBe("editing"));
  own.save.mockRejectedValueOnce(new TypeError("Response lost"));
  await own.controller.save("2.5", "Opening balance");
  const original = own.save.mock.lastCall;
  for (const code of ["mfa_required", "stale_balance_version", "access_denied"]) {
    own.save.mockResolvedValueOnce(failed(code));
    await own.controller.retry();
    expect(own.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      review: null,
      operationId: original?.[1],
    });
    await own.controller.refresh();
    await own.controller.save("100", "Cannot replace unresolved command");
  }
  await own.controller.retry();
  expect(own.controller.getSnapshot()).toMatchObject({ stage: "saved", receipt });
  expect(own.identifier).toHaveBeenCalledTimes(1);
  expect(own.load).toHaveBeenCalledTimes(1);
  for (const call of own.save.mock.calls) {
    expect(call.slice(0, 3)).toEqual(original?.slice(0, 3));
    expect(call[2]).toBe(original?.[2]);
  }
  own.controller.deactivate();
});

it.each(["stale_balance_version", "leave_year_closed", "leave_type_unavailable"])(
  "a definite %s requires an explicit new review before another command",
  async (code) => {
    const own = fixture();
    own.controller.activate();
    await vi.waitFor(() => expect(own.controller.getSnapshot().stage).toBe("editing"));
    own.save.mockResolvedValueOnce(failed(code));
    await own.controller.save("1", "Correction");
    expect(own.controller.getSnapshot().stage).toBe("conflict");
    await own.controller.retry();
    await own.controller.save("1", "Correction");
    expect(own.save).toHaveBeenCalledTimes(1);
    own.load.mockResolvedValueOnce(
      success({ ...review, balance: { ...review.balance, version: 15 } }),
    );
    await own.controller.refresh();
    await own.controller.save("1", "Correction");
    expect(own.save.mock.lastCall?.[2].expectedVersion).toBe(15);
    expect(own.save.mock.calls[0]?.[1]).not.toBe(own.save.mock.calls[1]?.[1]);
    own.controller.deactivate();
  },
);

it.each(["invalid_leave_adjustment", "insufficient_leave_balance"])(
  "a definite %s allows correction without retaining a failed operation",
  async (code) => {
    const own = fixture();
    own.controller.activate();
    await vi.waitFor(() => expect(own.controller.getSnapshot().stage).toBe("editing"));
    own.save.mockResolvedValueOnce(failed(code));
    await own.controller.save("-300", "Correction");
    expect(own.controller.getSnapshot()).toMatchObject({ stage: "editing", operationId: null });
    await own.controller.save("-0.5", "Corrected amount");
    expect(own.identifier).toHaveBeenCalledTimes(2);
    expect(own.save.mock.lastCall?.[2].days).toBe("-0.5");
    own.controller.deactivate();
  },
);

it.each(["mfa_required", "employee_not_found", "self_adjustment_denied", "company_access_denied"])(
  "a definite %s hides the private review and never retries automatically",
  async (code) => {
    const own = fixture();
    own.controller.activate();
    await vi.waitFor(() => expect(own.controller.getSnapshot().stage).toBe("editing"));
    own.save.mockResolvedValueOnce(failed(code));
    await own.controller.save("1", "Correction");
    expect(own.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      operationId: null,
    });
    own.controller.activate();
    await own.controller.retry();
    expect(own.load).toHaveBeenCalledTimes(1);
    expect(own.save).toHaveBeenCalledTimes(1);
    own.controller.deactivate();
  },
);

it.each(["value", "error"])(
  "disposal cancels a pending write and ignores its late %s",
  async (kind) => {
    const own = fixture();
    own.controller.activate();
    await vi.waitFor(() => expect(own.controller.getSnapshot().stage).toBe("editing"));
    const held = deferred<Result<MutationReceipt>>();
    own.save.mockReturnValueOnce(held.promise);
    const listener = vi.fn();
    const unsubscribe = own.controller.subscribe(listener);
    const saving = own.controller.save("1", "Correction");
    const signal = own.save.mock.lastCall?.[3];
    unsubscribe();
    const count = listener.mock.calls.length;
    own.controller.deactivate();
    if (kind === "value") held.resolve(success(receipt));
    else held.reject(new Error("Late private failure"));
    await saving;
    expect(signal?.aborted).toBe(true);
    expect(listener).toHaveBeenCalledTimes(count);
    expect(own.controller.getSnapshot()).toMatchObject({
      stage: "loading",
      review: null,
      receipt: null,
      failure: null,
    });
    await own.controller.retry();
    expect(own.save).toHaveBeenCalledTimes(1);
  },
);

it("a cancelled old review cannot replace a newer denial or cause a save", async () => {
  const own = fixture();
  const held = deferred<Result<LeaveLedger>>();
  own.load.mockReturnValueOnce(held.promise);
  own.controller.activate();
  const signal = own.load.mock.lastCall?.[4];
  own.load.mockResolvedValueOnce(failed("leave_adjustment_unavailable"));
  await own.controller.refresh();
  held.resolve(success(review));
  await Promise.resolve();
  expect(signal?.aborted).toBe(true);
  expect(own.controller.getSnapshot()).toMatchObject({ stage: "unavailable", review: null });
  await own.controller.save("1", "Correction");
  expect(own.save).not.toHaveBeenCalled();
  own.controller.deactivate();
});

it.each(["value", "error"])(
  "catalog scope disposal rejects a late %s and clears prior private context",
  async (kind) => {
    const catalog: LeaveAdjustmentCatalog = {
      companyId,
      employee: review.employee,
      year: 2026,
      policies: { items: [], nextCursor: null },
    };
    const load = vi
      .fn<LeaveUseCases["loadAdjustmentCatalog"]["execute"]>()
      .mockResolvedValue(success(catalog));
    const controller = new LeaveAdjustmentCatalogController(
      { execute: load },
      access,
      leaveEmployeeId(),
      { year: "2026", after: null },
    );
    controller.activate();
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    const held = deferred<Result<LeaveAdjustmentCatalog>>();
    load.mockReturnValueOnce(held.promise);
    const reading = controller.refresh();
    const signal = load.mock.lastCall?.[3];
    expect(controller.getSnapshot().catalog).toBeNull();
    controller.deactivate();
    if (kind === "value") held.resolve(success(catalog));
    else held.reject(new Error("Private late failure"));
    await reading;
    expect(signal?.aborted).toBe(true);
    expect(controller.getSnapshot()).toMatchObject({
      stage: "loading",
      catalog: null,
      failure: null,
    });
  },
);
