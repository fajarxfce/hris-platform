import { describe, expect, it, vi } from "vitest";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { access, changeFixture, identifier, shiftFixture } from "../../di/workforce-fixture";
import type { ShiftDefinition } from "../../domain/entities/shift-definition";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { ShiftController } from "./shift-controller";
import { ShiftDirectoryController } from "./shift-directory-controller";
import { ShiftEditorController } from "./shift-editor-controller";

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((accept) => {
    resolve = accept;
  });
  return { promise, resolve };
}
function actions() {
  return {
    loadShift: {
      execute: vi
        .fn<WorkforceUseCases["loadShift"]["execute"]>()
        .mockResolvedValue(success(shiftFixture())),
    },
    loadShifts: {
      execute: vi
        .fn<WorkforceUseCases["loadShifts"]["execute"]>()
        .mockResolvedValue(success({ items: [shiftFixture()], nextCursor: null })),
    },
    saveShift: {
      execute: vi
        .fn<WorkforceUseCases["saveShift"]["execute"]>()
        .mockResolvedValue(success({ id: identifier(), version: 0 })),
    },
  };
}

describe("shift request owners", () => {
  it("cancels an obsolete directory request and drops its late error after a newer page", async () => {
    const api = actions();
    const old = deferred<Awaited<ReturnType<WorkforceUseCases["loadShifts"]["execute"]>>>();
    api.loadShifts.execute.mockReturnValueOnce(old.promise);
    const controller = new ShiftDirectoryController(api.loadShifts, access, {
      query: "",
      after: null,
    });
    const listener = vi.fn();
    const unsubscribe = controller.subscribe(listener);
    controller.activate();
    const firstSignal = api.loadShifts.execute.mock.calls[0]?.[2];
    await controller.refresh();
    expect(firstSignal?.aborted).toBe(true);
    old.resolve(failed("mfa_required"));
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    expect(controller.getSnapshot().failure).toBeNull();
    unsubscribe();
    const count = listener.mock.calls.length;
    controller.deactivate();
    await controller.refresh();
    expect(listener).toHaveBeenCalledTimes(count);
    expect(api.loadShifts.execute).toHaveBeenCalledTimes(2);
    expect(controller.getSnapshot().page).toBeNull();
  });

  it("does not restore details after disposal and recovers read errors only on explicit refresh", async () => {
    const api = actions();
    const pending = deferred<Result<ShiftDefinition>>();
    api.loadShift.execute.mockReturnValueOnce(pending.promise);
    const controller = new ShiftController(api.loadShift, access, identifier());
    controller.activate();
    controller.deactivate();
    expect(api.loadShift.execute.mock.calls[0]?.[2].aborted).toBe(true);
    pending.resolve(success(shiftFixture()));
    await Promise.resolve();
    await Promise.resolve();
    expect(controller.getSnapshot().shift).toBeNull();
    api.loadShift.execute.mockRejectedValueOnce(new Error("test failure"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("unavailable"));
    expect(api.loadShift.execute).toHaveBeenCalledTimes(2);
    await controller.refresh();
    expect(controller.getSnapshot().shift?.id).toBe(identifier());
    controller.deactivate();
  });

  it("retains one immutable unknown command through MFA and conflict and never retries automatically", async () => {
    const api = actions();
    const response = deferred<Result<MutationReceipt>>();
    api.saveShift.execute.mockReturnValueOnce(response.promise);
    const controller = new ShiftEditorController(api, access, true, identifier(), () =>
      identifier(99),
    );
    controller.activate();
    const input = structuredClone(changeFixture());
    const pending = controller.save(input);
    await controller.save(input);
    expect(api.saveShift.execute).toHaveBeenCalledTimes(1);
    const submitted = api.saveShift.execute.mock.calls[0]?.[2];
    if (!submitted) throw new Error("Expected the pending shift command");
    expect(Object.isFrozen(submitted.details.fence)).toBe(true);
    response.resolve(failed("connection_unavailable"));
    await pending;
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.refresh();
    await controller.save({ ...input, reason: "Changed" });
    expect(api.saveShift.execute).toHaveBeenCalledTimes(1);
    for (const code of ["mfa_required", "stale_version"]) {
      api.saveShift.execute.mockResolvedValueOnce(failed(code));
      await controller.retrySave();
      expect(controller.getSnapshot().stage).toBe("unconfirmed");
    }
    await controller.retrySave();
    expect(controller.getSnapshot().stage).toBe("saved");
    expect(controller.getSnapshot().receipt).toEqual({ id: identifier(), version: 0 });
    expect(api.saveShift.execute).toHaveBeenCalledTimes(4);
    for (const call of api.saveShift.execute.mock.calls) {
      expect(call[1]).toBe(identifier(99));
      expect(call[2]).toBe(api.saveShift.execute.mock.calls[0]?.[2]);
    }
    await controller.retrySave();
    await controller.save(input);
    expect(api.saveShift.execute).toHaveBeenCalledTimes(4);
    controller.deactivate();
  });

  it("requires reload after a definite stale revision and uses a new ID with the newly observed version", async () => {
    const api = actions();
    let sequence = 20;
    const controller = new ShiftEditorController(api, access, false, identifier(), () =>
      identifier(++sequence),
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    api.saveShift.execute.mockResolvedValueOnce(failed("stale_version"));
    await controller.save(changeFixture());
    expect(controller.getSnapshot().stage).toBe("conflict");
    await controller.save(changeFixture());
    expect(api.saveShift.execute).toHaveBeenCalledTimes(1);
    api.loadShift.execute.mockResolvedValueOnce(success(shiftFixture(1, 3)));
    await controller.refresh();
    api.saveShift.execute.mockResolvedValueOnce(success({ id: identifier(), version: 4 }));
    await controller.save(changeFixture());
    expect(api.saveShift.execute.mock.calls.map((call) => call[2].expectedVersion)).toEqual([0, 3]);
    expect(api.saveShift.execute.mock.calls[0]?.[1]).not.toBe(
      api.saveShift.execute.mock.calls[1]?.[1],
    );
    expect(controller.getSnapshot().stage).toBe("saved");
    controller.deactivate();
  });

  it("blocks unauthorized editing and clears pending save ownership on disposal", async () => {
    const api = actions();
    const denied = new ShiftEditorController(
      api,
      { ...access, permissions: ["workforce.read"] },
      false,
      identifier(),
      () => identifier(99),
    );
    denied.activate();
    await denied.save(changeFixture());
    expect(denied.getSnapshot().stage).toBe("unavailable");
    expect(api.loadShift.execute).not.toHaveBeenCalled();
    expect(api.saveShift.execute).not.toHaveBeenCalled();
    denied.deactivate();
    const late = deferred<Result<MutationReceipt>>();
    api.saveShift.execute.mockReturnValueOnce(late.promise);
    const controller = new ShiftEditorController(api, access, true, identifier(), () =>
      identifier(99),
    );
    controller.activate();
    const pending = controller.save(changeFixture());
    controller.deactivate();
    expect(api.saveShift.execute.mock.calls[0]?.[3].aborted).toBe(true);
    late.resolve(success({ id: identifier(), version: 0 }));
    await pending;
    expect(controller.getSnapshot().receipt).toBeNull();
    expect(controller.getSnapshot().operationId).toBeNull();
  });
});
