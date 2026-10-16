import { afterEach, describe, expect, it, vi } from "vitest";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  adjustmentAccess,
  adjustmentChange,
  adjustmentDate,
  adjustmentId,
  adjustmentOperation,
  adjustmentReviewFixture,
  employeeId,
} from "../../di/attendance-adjustment-fixture";
import type { AttendanceAdjustmentReview } from "../../domain/entities/attendance-adjustment-review";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { AttendanceAdjustmentController } from "./attendance-adjustment-controller";

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function actions() {
  return {
    loadAttendanceAdjustmentReview: {
      execute: vi
        .fn<WorkforceUseCases["loadAttendanceAdjustmentReview"]["execute"]>()
        .mockResolvedValue(success(adjustmentReviewFixture())),
    },
    adjustClosedAttendance: {
      execute: vi
        .fn<WorkforceUseCases["adjustClosedAttendance"]["execute"]>()
        .mockResolvedValue(success({ id: adjustmentId, version: 1 })),
    },
  };
}
const active: AttendanceAdjustmentController[] = [];
afterEach(() => {
  for (const controller of active.splice(0)) controller.deactivate();
});
function fixture(api = actions(), visible = true) {
  const next = vi.fn(() => adjustmentOperation);
  const controller = new AttendanceAdjustmentController(
    api,
    adjustmentAccess,
    employeeId,
    adjustmentDate,
    next,
  );
  active.push(controller);
  controller.setVisible(visible);
  controller.activate();
  return { controller, api, next };
}
async function ready(controller: AttendanceAdjustmentController) {
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("reviewing"));
}

describe("closed attendance command ownership", () => {
  it("stops a hidden read and rejects its late failure after readmission", async () => {
    const api = actions(),
      old = deferred<Result<AttendanceAdjustmentReview>>();
    api.loadAttendanceAdjustmentReview.execute.mockReturnValueOnce(old.promise);
    const { controller } = fixture(api, false);
    expect(api.loadAttendanceAdjustmentReview.execute).not.toHaveBeenCalled();
    controller.setVisible(true);
    const signal = api.loadAttendanceAdjustmentReview.execute.mock.calls[0]?.[3];
    controller.setVisible(false);
    expect(signal?.aborted).toBe(true);
    controller.setVisible(true);
    await ready(controller);
    old.resolve(failed("mfa_required"));
    await old.promise;
    expect(controller.getSnapshot()).toMatchObject({ stage: "reviewing", failure: null });
    expect(api.loadAttendanceAdjustmentReview.execute).toHaveBeenCalledTimes(2);
  });
  it("requires decisions and admits only one original submission", async () => {
    const api = actions(),
      held = deferred<Result<MutationReceipt>>();
    api.adjustClosedAttendance.execute.mockReturnValueOnce(held.promise);
    const { controller } = fixture(api);
    await ready(controller);
    await controller.save({ ...adjustmentChange(), decisions: [] });
    expect(api.adjustClosedAttendance.execute).not.toHaveBeenCalled();
    const change = {
      ...adjustmentChange(),
      decisions: adjustmentChange().decisions.map((item) => ({ ...item })),
    };
    const saving = controller.save(change);
    change.decisions.splice(0);
    await controller.save(adjustmentChange());
    await controller.retry();
    expect(api.adjustClosedAttendance.execute).toHaveBeenCalledTimes(1);
    expect(api.adjustClosedAttendance.execute.mock.calls[0]?.[2].decisions).toHaveLength(1);
    held.resolve(success({ id: adjustmentId, version: 1 }));
    await saving;
    expect(controller.getSnapshot()).toMatchObject({
      stage: "saved",
      receipt: { id: adjustmentId, version: 1 },
    });
  });
  it("keeps the exact command through response loss, revoked reads and explicit retry", async () => {
    const api = actions();
    api.adjustClosedAttendance.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("attendance_adjustment_changed"));
    const { controller, next } = fixture(api);
    await ready(controller);
    await controller.save(adjustmentChange());
    const original = api.adjustClosedAttendance.execute.mock.calls[0];
    await controller.refresh();
    await controller.save({ ...adjustmentChange(), reason: "Changed" });
    for (let index = 0; index < 2; index++) await controller.retry();
    expect(controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", review: null });
    await controller.retry();
    expect(controller.getSnapshot().stage).toBe("saved");
    expect(next).toHaveBeenCalledTimes(1);
    expect(api.loadAttendanceAdjustmentReview.execute).toHaveBeenCalledTimes(1);
    for (const call of api.adjustClosedAttendance.execute.mock.calls)
      expect(call.slice(0, 3)).toEqual(original?.slice(0, 3));
  });
  it("requires deliberate source reload after a definite conflict", async () => {
    const api = actions();
    api.adjustClosedAttendance.execute.mockResolvedValueOnce(
      failed("attendance_adjustment_changed"),
    );
    const { controller, next } = fixture(api);
    await ready(controller);
    await controller.save(adjustmentChange());
    await controller.save(adjustmentChange());
    await controller.retry();
    expect(controller.getSnapshot()).toMatchObject({ stage: "conflict", operationId: null });
    expect(api.adjustClosedAttendance.execute).toHaveBeenCalledTimes(1);
    await controller.refresh();
    await controller.save(adjustmentChange());
    expect(next).toHaveBeenCalledTimes(2);
  });
  it.each(["correct", "verify"])(
    "does not allow a read-only %s hint to authorize the command",
    async (kind) => {
      const api = actions();
      api.loadAttendanceAdjustmentReview.execute.mockResolvedValue(
        success({
          ...adjustmentReviewFixture(),
          ...(kind === "correct" ? { canCorrect: false } : { canReviewEvidence: false }),
        }),
      );
      const { controller } = fixture(api);
      await ready(controller);
      await controller.save(adjustmentChange());
      expect(api.adjustClosedAttendance.execute).not.toHaveBeenCalled();
    },
  );
  it("disposal cancels pending work and cannot publish a late receipt into another page", async () => {
    const api = actions(),
      held = deferred<Result<MutationReceipt>>();
    api.adjustClosedAttendance.execute.mockReturnValueOnce(held.promise);
    const { controller } = fixture(api);
    await ready(controller);
    const listener = vi.fn(),
      unsubscribe = controller.subscribe(listener);
    const saving = controller.save(adjustmentChange());
    const signal = api.adjustClosedAttendance.execute.mock.calls[0]?.[3];
    unsubscribe();
    controller.deactivate();
    listener.mockClear();
    held.resolve(success({ id: adjustmentId, version: 1 }));
    await saving;
    expect(signal?.aborted).toBe(true);
    expect(controller.getSnapshot()).toMatchObject({ receipt: null, review: null });
    expect(listener).not.toHaveBeenCalled();
  });
});
