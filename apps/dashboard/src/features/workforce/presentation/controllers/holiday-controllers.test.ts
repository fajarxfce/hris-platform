import { describe, expect, it, vi } from "vitest";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  holidayAccess as access,
  holidayChangeFixture as changeFixture,
  holidayFixture,
  identifier,
} from "../../di/holiday-fixture";
import type { WorkHoliday } from "../../domain/entities/work-holiday";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { HolidayController } from "./holiday-controller";
import { HolidayDirectoryController } from "./holiday-directory-controller";
import { HolidayEditorController } from "./holiday-editor-controller";

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((accept) => {
    resolve = accept;
  });
  return { promise, resolve };
}
function actions() {
  return {
    loadHoliday: {
      execute: vi
        .fn<WorkforceUseCases["loadHoliday"]["execute"]>()
        .mockResolvedValue(success(holidayFixture())),
    },
    loadHolidays: {
      execute: vi
        .fn<WorkforceUseCases["loadHolidays"]["execute"]>()
        .mockResolvedValue(success([holidayFixture()])),
    },
    saveHoliday: {
      execute: vi
        .fn<WorkforceUseCases["saveHoliday"]["execute"]>()
        .mockResolvedValue(success({ id: identifier(), version: 0 })),
    },
  };
}

describe("holiday request owners", () => {
  it("cancels an obsolete directory request and drops its late error after a newer page", async () => {
    const api = actions();
    const old = deferred<Awaited<ReturnType<WorkforceUseCases["loadHolidays"]["execute"]>>>();
    api.loadHolidays.execute.mockReturnValueOnce(old.promise);
    const controller = new HolidayDirectoryController(api.loadHolidays, access, {
      from: "2026-01-01",
      until: "2026-12-31",
    });
    const listener = vi.fn();
    const unsubscribe = controller.subscribe(listener);
    controller.activate();
    const firstSignal = api.loadHolidays.execute.mock.calls[0]?.[2];
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
    expect(api.loadHolidays.execute).toHaveBeenCalledTimes(2);
    expect(controller.getSnapshot().holidays).toBeNull();
  });

  it("does not restore details after disposal and recovers read errors only on explicit refresh", async () => {
    const api = actions();
    const pending = deferred<Result<WorkHoliday>>();
    api.loadHoliday.execute.mockReturnValueOnce(pending.promise);
    const controller = new HolidayController(api.loadHoliday, access, identifier());
    controller.activate();
    controller.deactivate();
    expect(api.loadHoliday.execute.mock.calls[0]?.[2].aborted).toBe(true);
    pending.resolve(success(holidayFixture()));
    await Promise.resolve();
    await Promise.resolve();
    expect(controller.getSnapshot().holiday).toBeNull();
    api.loadHoliday.execute.mockRejectedValueOnce(new Error("test failure"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("unavailable"));
    expect(api.loadHoliday.execute).toHaveBeenCalledTimes(2);
    await controller.refresh();
    expect(controller.getSnapshot().holiday?.id).toBe(identifier());
    controller.deactivate();
  });

  it("retains one immutable unknown command through MFA and conflict and never retries automatically", async () => {
    const api = actions();
    const response = deferred<Result<MutationReceipt>>();
    api.saveHoliday.execute.mockReturnValueOnce(response.promise);
    const controller = new HolidayEditorController(api, access, true, identifier(), () =>
      identifier(99),
    );
    controller.activate();
    const input = { ...changeFixture() };
    const pending = controller.save(input);
    await controller.save(input);
    expect(api.saveHoliday.execute).toHaveBeenCalledTimes(1);
    const submitted = api.saveHoliday.execute.mock.calls[0]?.[2];
    if (!submitted) throw new Error("Expected the pending holiday command");
    expect(Object.isFrozen(submitted)).toBe(true);
    input.name = "Changed while pending";
    expect(submitted.name).toBe("New year");
    response.resolve(failed("connection_unavailable"));
    await pending;
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.refresh();
    await controller.save({ ...input, reason: "Changed" });
    expect(api.saveHoliday.execute).toHaveBeenCalledTimes(1);
    for (const code of ["mfa_required", "stale_version"]) {
      api.saveHoliday.execute.mockResolvedValueOnce(failed(code));
      await controller.retrySave();
      expect(controller.getSnapshot().stage).toBe("unconfirmed");
    }
    await controller.retrySave();
    expect(controller.getSnapshot().stage).toBe("saved");
    expect(controller.getSnapshot().receipt).toEqual({ id: identifier(), version: 0 });
    expect(api.saveHoliday.execute).toHaveBeenCalledTimes(4);
    for (const call of api.saveHoliday.execute.mock.calls) {
      expect(call[1]).toBe(identifier(99));
      expect(call[2]).toBe(api.saveHoliday.execute.mock.calls[0]?.[2]);
    }
    await controller.retrySave();
    await controller.save(input);
    expect(api.saveHoliday.execute).toHaveBeenCalledTimes(4);
    controller.deactivate();
  });

  it("requires reload after a definite stale revision and uses a new ID with the newly observed version", async () => {
    const api = actions();
    let sequence = 20;
    const controller = new HolidayEditorController(api, access, false, identifier(), () =>
      identifier(++sequence),
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    api.saveHoliday.execute.mockResolvedValueOnce(failed("stale_version"));
    await controller.save(changeFixture());
    expect(controller.getSnapshot().stage).toBe("conflict");
    await controller.save(changeFixture());
    expect(api.saveHoliday.execute).toHaveBeenCalledTimes(1);
    api.loadHoliday.execute.mockResolvedValueOnce(success(holidayFixture(1, 3)));
    await controller.refresh();
    api.saveHoliday.execute.mockResolvedValueOnce(success({ id: identifier(), version: 4 }));
    await controller.save(changeFixture());
    expect(api.saveHoliday.execute.mock.calls.map((call) => call[2].expectedVersion)).toEqual([
      0, 3,
    ]);
    expect(api.saveHoliday.execute.mock.calls[0]?.[1]).not.toBe(
      api.saveHoliday.execute.mock.calls[1]?.[1],
    );
    expect(controller.getSnapshot().stage).toBe("saved");
    controller.deactivate();
  });

  it("blocks unauthorized editing and clears pending save ownership on disposal", async () => {
    const api = actions();
    const denied = new HolidayEditorController(
      api,
      { ...access, permissions: ["company.read"] },
      false,
      identifier(),
      () => identifier(99),
    );
    denied.activate();
    await denied.save(changeFixture());
    expect(denied.getSnapshot().stage).toBe("unavailable");
    expect(api.loadHoliday.execute).not.toHaveBeenCalled();
    expect(api.saveHoliday.execute).not.toHaveBeenCalled();
    denied.deactivate();
    const late = deferred<Result<MutationReceipt>>();
    api.saveHoliday.execute.mockReturnValueOnce(late.promise);
    const controller = new HolidayEditorController(api, access, true, identifier(), () =>
      identifier(99),
    );
    controller.activate();
    const pending = controller.save(changeFixture());
    controller.deactivate();
    expect(api.saveHoliday.execute.mock.calls[0]?.[3].aborted).toBe(true);
    late.resolve(success({ id: identifier(), version: 0 }));
    await pending;
    expect(controller.getSnapshot().receipt).toBeNull();
    expect(controller.getSnapshot().operationId).toBeNull();
  });
});
