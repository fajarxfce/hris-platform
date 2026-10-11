import { describe, expect, it, vi } from "vitest";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  attendanceAccess,
  attendanceDate,
  attendanceFixture,
  employeeId,
  eventId,
  identifier,
} from "../../di/attendance-fixture";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { AttendanceEditorController } from "./attendance-editor-controller";

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function fixture() {
  const actions = {
    loadEmployeeAttendance: {
      execute: vi
        .fn<WorkforceUseCases["loadEmployeeAttendance"]["execute"]>()
        .mockResolvedValue(success(attendanceFixture())),
    },
    reviewAttendance: {
      execute: vi
        .fn<WorkforceUseCases["reviewAttendance"]["execute"]>()
        .mockResolvedValue(success({ id: eventId, version: 1 })),
    },
    correctAttendance: {
      execute: vi
        .fn<WorkforceUseCases["correctAttendance"]["execute"]>()
        .mockResolvedValue(success({ id: identifier(909), version: 0 })),
    },
  };
  return actions;
}
describe("attendance editor ownership", () => {
  it("retains one immutable review through duplicate clicks, response loss, MFA and conflicts", async () => {
    const actions = fixture(),
      pending = deferred<Result<MutationReceipt>>();
    actions.reviewAttendance.execute.mockReturnValueOnce(pending.promise);
    const controller = new AttendanceEditorController(
      actions,
      attendanceAccess,
      employeeId,
      attendanceDate,
      { kind: "review", entryId: eventId },
      () => identifier(99),
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    const fields = { decision: "ACCEPT" as const, reason: "Observed onsite" },
      sending = controller.review(fields);
    fields.reason = "Changed caller";
    await controller.review(fields);
    expect(actions.reviewAttendance.execute).toHaveBeenCalledTimes(1);
    const original = actions.reviewAttendance.execute.mock.calls[0];
    expect(original?.[2].reason).toBe("Observed onsite");
    pending.resolve(failed("connection_unavailable"));
    await sending;
    for (const code of ["mfa_required", "stale_version"]) {
      actions.reviewAttendance.execute.mockResolvedValueOnce(failed(code));
      await controller.retry();
      expect(controller.getSnapshot().stage).toBe("unconfirmed");
    }
    await controller.refresh();
    expect(actions.loadEmployeeAttendance.execute).toHaveBeenCalledTimes(1);
    await controller.retry();
    expect(controller.getSnapshot().stage).toBe("saved");
    for (const call of actions.reviewAttendance.execute.mock.calls) {
      expect(call[1]).toBe(original?.[1]);
      expect(call[2]).toBe(original?.[2]);
    }
    controller.deactivate();
  });
  it("discards late results and releases the pending request on departure", async () => {
    const actions = fixture(),
      pending = deferred<Result<MutationReceipt>>();
    actions.reviewAttendance.execute.mockReturnValueOnce(pending.promise);
    const controller = new AttendanceEditorController(
      actions,
      attendanceAccess,
      employeeId,
      attendanceDate,
      { kind: "review", entryId: eventId },
      () => identifier(99),
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    const sending = controller.review({ decision: "REJECT", reason: "Duplicate evidence" });
    const signal = actions.reviewAttendance.execute.mock.calls[0]?.[3];
    controller.deactivate();
    expect(signal?.aborted).toBe(true);
    pending.resolve(success({ id: eventId, version: 1 }));
    await sending;
    expect(controller.getSnapshot().day).toBeNull();
    expect(controller.getSnapshot().receipt).toBeNull();
  });
  it("uses a correction revision only after pending evidence has been resolved", async () => {
    const actions = fixture();
    const controller = new AttendanceEditorController(
      actions,
      attendanceAccess,
      employeeId,
      attendanceDate,
      { kind: "correction" },
      () => identifier(99),
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("conflict"));
    await controller.correct({ clockIn: null, clockOut: null, breakMinutes: 0, reason: "Absence" });
    expect(actions.correctAttendance.execute).not.toHaveBeenCalled();
    const review = attendanceFixture(),
      day = review.days[0];
    if (!day) throw new Error("Missing fixture");
    actions.loadEmployeeAttendance.execute.mockResolvedValue(
      success({ ...review, days: [{ ...day, entries: [], pendingCount: 0 }] }),
    );
    await controller.refresh();
    expect(controller.getSnapshot().stage).toBe("editing");
    actions.correctAttendance.execute.mockResolvedValueOnce(failed("stale_version"));
    await controller.correct({ clockIn: null, clockOut: null, breakMinutes: 0, reason: "Absence" });
    expect(controller.getSnapshot().stage).toBe("conflict");
    expect(actions.correctAttendance.execute.mock.calls[0]?.[2].expectedVersion).toBeNull();
    controller.deactivate();
  });
  it("a replacement read cannot restore stale evidence", async () => {
    const actions = fixture(),
      pending =
        deferred<Awaited<ReturnType<WorkforceUseCases["loadEmployeeAttendance"]["execute"]>>>();
    actions.loadEmployeeAttendance.execute.mockReturnValueOnce(pending.promise);
    const controller = new AttendanceEditorController(
      actions,
      attendanceAccess,
      employeeId,
      attendanceDate,
      { kind: "review", entryId: eventId },
      () => identifier(99),
    );
    controller.activate();
    const original = actions.loadEmployeeAttendance.execute.mock.calls[0]?.[3];
    await controller.refresh();
    expect(original?.aborted).toBe(true);
    pending.resolve(failed("unexpected_error"));
    await Promise.resolve();
    expect(controller.getSnapshot().stage).toBe("editing");
    controller.deactivate();
  });
});
