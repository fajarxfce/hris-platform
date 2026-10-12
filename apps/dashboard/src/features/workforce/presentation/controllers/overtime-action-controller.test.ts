import { afterEach, describe, expect, it, vi } from "vitest";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toOvertimeDetails } from "../../data/mappers/overtime-details-mapper";
import {
  company,
  identifier,
  overtimeDetailsDtoFixture,
  overtimeId,
} from "../../di/overtime-fixture";
import type { OvertimeRequestId } from "../../domain/entities/overtime-request";
import type {
  OvertimeAction,
  OvertimeRequestDetails,
} from "../../domain/entities/overtime-request-details";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { OvertimeActionController } from "./overtime-action-controller";

function deferred<T>() {
  let resolve!: (value: T) => void, reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
const controllers: OvertimeActionController[] = [];
afterEach(() => {
  for (const controller of controllers.splice(0)) controller.deactivate();
});
function fixture(action: OvertimeAction = "APPROVE", active = true) {
  const record = structuredClone(
    toOvertimeDetails(
      overtimeDetailsDtoFixture(
        action === "SUBMIT_ACTUAL" || action === "WITHDRAW" ? "PLANNED" : "PENDING",
      ),
      company,
      overtimeId as OvertimeRequestId,
      null,
    ),
  );
  const review = vi
    .fn<WorkforceUseCases["reviewOvertimeAction"]["execute"]>()
    .mockResolvedValue(success(record));
  const receipt = { id: record.request.id, version: record.request.version + 1 };
  const actual = vi
    .fn<WorkforceUseCases["submitOvertimeActual"]["execute"]>()
    .mockResolvedValue(success(receipt));
  const decide = vi
    .fn<WorkforceUseCases["decideOvertimeRequest"]["execute"]>()
    .mockResolvedValue(success(receipt));
  const withdraw = vi
    .fn<WorkforceUseCases["withdrawOvertimeRequest"]["execute"]>()
    .mockResolvedValue(success(receipt));
  let sequence = 1000;
  const next = vi.fn(() => identifier(++sequence));
  const controller = new OvertimeActionController(
    {
      reviewOvertimeAction: { execute: review },
      submitOvertimeActual: { execute: actual },
      decideOvertimeRequest: { execute: decide },
      withdrawOvertimeRequest: { execute: withdraw },
    },
    { companyId: company, permissions: ["overtime.manage", "overtime.approve"] },
    record.request.id,
    action,
    next,
  );
  controllers.push(controller);
  if (active) controller.activate();
  return { controller, record, review, actual, decide, withdraw, next, receipt };
}
const ready = (controller: OvertimeActionController) =>
  vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("reviewing"));

describe("overtime action ownership", () => {
  it("requires an active reviewed route and handles duplicate activation", async () => {
    const f = fixture("APPROVE", false);
    await f.controller.approve("");
    await f.controller.refresh();
    expect(f.review).not.toHaveBeenCalled();
    f.controller.activate();
    f.controller.activate();
    await ready(f.controller);
    await f.controller.reject("Another route");
    await f.controller.withdraw("Another route");
    expect(f.review).toHaveBeenCalledOnce();
    expect(f.next).not.toHaveBeenCalled();
    await f.controller.approve("");
    expect(f.decide.mock.lastCall?.[3]).toBe("APPROVE");
  });

  it("freezes actual time and minimal request facts before delivery and prevents duplicate actions", async () => {
    const f = fixture("SUBMIT_ACTUAL");
    await ready(f.controller);
    const held = deferred<Result<MutationReceipt>>();
    f.actual
      .mockReturnValueOnce(held.promise)
      .mockResolvedValueOnce(failed("connection_unavailable"));
    const input = {
      startsAt: "2026-10-12T07:15:00Z",
      endsAt: "2026-10-12T09:45:00Z",
      breakMinutes: 15,
    };
    const work = f.controller.submitActual(input, "Original time");
    input.breakMinutes = 100;
    (f.record.actions as string[]).splice(0);
    Object.assign(f.record.request.requested, { workedMinutes: 1 });
    await f.controller.submitActual(input, "Replacement");
    await f.controller.retry();
    await f.controller.refresh();
    expect(f.actual).toHaveBeenCalledOnce();
    expect(f.review).toHaveBeenCalledOnce();
    expect(f.actual.mock.lastCall?.[2]).toMatchObject({
      requested: { workedMinutes: 180 },
      actions: ["SUBMIT_ACTUAL", "WITHDRAW"],
    });
    expect(f.actual.mock.lastCall?.[2]).not.toHaveProperty("history");
    expect(Object.isFrozen(f.actual.mock.lastCall?.[2].requested)).toBe(true);
    expect(Object.isFrozen(f.actual.mock.lastCall?.[3])).toBe(true);
    held.resolve(failed("connection_unavailable"));
    await work;
    await f.controller.retry();
    expect(f.actual.mock.calls[1]?.slice(0, 5)).toEqual(f.actual.mock.calls[0]?.slice(0, 5));
    expect(f.actual.mock.lastCall?.[3].breakMinutes).toBe(15);
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      review: null,
      operationId: null,
      receipt: f.receipt,
    });
    expect(f.next).toHaveBeenCalledOnce();
  });

  it("keeps the original command through MFA rejection and later conflicts without reopening private data", async () => {
    const f = fixture();
    await ready(f.controller);
    f.decide
      .mockResolvedValueOnce(failed("request_timeout"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_version"));
    await f.controller.approve("Original review");
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      review: null,
      failure: { code: "mfa_required" },
    });
    const held = deferred<Result<MutationReceipt>>();
    f.decide.mockReturnValueOnce(held.promise);
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      review: null,
      failure: { code: "stale_version" },
    });
    const recovery = f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "submitting",
      review: null,
      failure: null,
    });
    await f.controller.refresh();
    await f.controller.approve("Replacement");
    await f.controller.retry();
    expect(f.review).toHaveBeenCalledOnce();
    expect(f.decide).toHaveBeenCalledTimes(4);
    held.resolve(success(f.receipt));
    await recovery;
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledOnce();
    for (const call of f.decide.mock.calls)
      expect(call.slice(0, 5)).toEqual(f.decide.mock.calls[0]?.slice(0, 5));
  });

  it("requires an explicit fresh review after initial version conflict and never auto-resubmits", async () => {
    const f = fixture("REJECT");
    await ready(f.controller);
    f.decide
      .mockResolvedValueOnce(failed("invalid_overtime_decision"))
      .mockResolvedValueOnce(failed("stale_version"));
    await f.controller.reject("");
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
    await f.controller.reject("Reviewed");
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.reject("No new review");
    await f.controller.retry();
    expect(f.decide).toHaveBeenCalledTimes(2);
    f.review.mockResolvedValueOnce(
      success({ ...f.record, request: { ...f.record.request, version: 2 } }),
    );
    await f.controller.refresh();
    expect(f.decide).toHaveBeenCalledTimes(2);
    await f.controller.reject("Reviewed changed request");
    expect(f.decide.mock.lastCall?.[2].version).toBe(2);
    expect(f.decide.mock.lastCall?.[3]).toBe("REJECT");
    expect(f.next).toHaveBeenCalledTimes(3);
  });

  it.each(["mfa_required", "overtime_request_not_found"])(
    "keeps private data gated after a definite %s and failed read retry",
    async (code) => {
      const f = fixture("WITHDRAW");
      await ready(f.controller);
      f.withdraw.mockResolvedValueOnce(failed(code));
      await f.controller.withdraw("Not needed");
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unavailable",
        review: null,
        operationId: null,
      });
      const held = deferred<Result<OvertimeRequestDetails>>();
      f.review.mockReturnValueOnce(held.promise);
      const pending = f.controller.refresh();
      expect(f.controller.getSnapshot()).toMatchObject({ stage: "loading", review: null });
      held.resolve(failed("connection_unavailable"));
      await pending;
      expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", review: null });
      await f.controller.withdraw("No current review");
      expect(f.withdraw).toHaveBeenCalledOnce();
      await f.controller.refresh();
      expect(f.controller.getSnapshot().review).toBe(f.record);
      expect(f.withdraw).toHaveBeenCalledOnce();
    },
  );

  it("contains unexpected failures without exposing diagnostics or losing an uncertain command", async () => {
    const f = fixture("WITHDRAW");
    await ready(f.controller);
    f.withdraw.mockRejectedValueOnce(new Error("private technical detail"));
    await f.controller.withdraw("Cancelled work");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "unexpected_error", fields: {}, parameters: {} },
    });
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("private technical detail");
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.withdraw.mock.calls[1]?.slice(0, 4)).toEqual(f.withdraw.mock.calls[0]?.slice(0, 4));
  });

  it.each(["success", "failure"] as const)(
    "aborts disposed commands and ignores late %s after a new activation",
    async (outcome) => {
      const f = fixture();
      await ready(f.controller);
      const held = deferred<Result<MutationReceipt>>();
      f.decide.mockReturnValueOnce(held.promise);
      const work = f.controller.approve("Reviewed");
      const signal = f.decide.mock.lastCall?.[5];
      const listener = vi.fn(),
        detach = f.controller.subscribe(listener);
      detach();
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      f.controller.activate();
      await ready(f.controller);
      if (outcome === "success") held.resolve(success(f.receipt));
      else held.reject(new Error("late failure"));
      await work;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "reviewing",
        receipt: null,
        failure: null,
        operationId: null,
      });
      expect(listener).not.toHaveBeenCalled();
      await f.controller.retry();
      expect(f.decide).toHaveBeenCalledOnce();
    },
  );

  it.each(["success", "failure"] as const)(
    "ignores superseded review %s and clears old details during acquisition",
    async (outcome) => {
      const f = fixture();
      await ready(f.controller);
      const held = deferred<Result<OvertimeRequestDetails>>();
      f.review
        .mockReturnValueOnce(held.promise)
        .mockResolvedValueOnce(failed("overtime_action_unavailable"));
      const old = f.controller.refresh(),
        signal = f.review.mock.lastCall?.[3];
      expect(f.controller.getSnapshot().review).toBeNull();
      await f.controller.refresh();
      expect(signal?.aborted).toBe(true);
      if (outcome === "success") held.resolve(success(f.record));
      else held.reject(new Error("old failure"));
      await old;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unavailable",
        review: null,
        failure: { code: "overtime_action_unavailable" },
      });
      await f.controller.approve("");
      expect(f.next).not.toHaveBeenCalled();
    },
  );
});
