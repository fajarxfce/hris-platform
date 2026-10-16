import { afterEach, describe, expect, it, vi } from "vitest";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { access, operation } from "../../di/calendar-fixture";
import { bulkReviewFixture, bulkSelection } from "../../di/roster-bulk-fixture";
import type { RosterBulkReview } from "../../domain/entities/roster-bulk-review";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { RosterBulkController } from "./roster-bulk-controller";

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
function actions() {
  return {
    reviewRosterBulk: {
      execute: vi
        .fn<WorkforceUseCases["reviewRosterBulk"]["execute"]>()
        .mockResolvedValue(success(bulkReviewFixture())),
    },
    applyRosterBulk: {
      execute: vi
        .fn<WorkforceUseCases["applyRosterBulk"]["execute"]>()
        .mockResolvedValue(success({ id: operation, version: 0 })),
    },
  };
}
const controllers: RosterBulkController[] = [];
afterEach(() => {
  for (const controller of controllers.splice(0)) controller.deactivate();
});
function create(api = actions(), scope = access, next = vi.fn(() => operation)) {
  const controller = new RosterBulkController(api, scope, next);
  controllers.push(controller);
  controller.activate();
  return { controller, api, next };
}

describe("bulk roster presentation ownership", () => {
  it("admits only current managers and leaves editing without a background preview", async () => {
    const { controller, api } = create(actions(), { ...access, permissions: ["workforce.read"] });
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      failure: { code: "access_denied" },
    });
    await controller.prepare(bulkSelection());
    await controller.save("Coverage");
    await controller.retry();
    expect(api.reviewRosterBulk.execute).not.toHaveBeenCalled();
    expect(api.applyRosterBulk.execute).not.toHaveBeenCalled();
    const active = create();
    expect(active.controller.getSnapshot().stage).toBe("editing");
    expect(active.api.reviewRosterBulk.execute).not.toHaveBeenCalled();
  });

  it("runs one preview at a time and ignores an aborted old failure after reload", async () => {
    const api = actions();
    const old = deferred<Result<RosterBulkReview>>();
    api.reviewRosterBulk.execute.mockReturnValueOnce(old.promise);
    const { controller } = create(api);
    const pending = controller.prepare(bulkSelection());
    await controller.prepare(bulkSelection());
    expect(api.reviewRosterBulk.execute).toHaveBeenCalledTimes(1);
    const signal = api.reviewRosterBulk.execute.mock.calls[0]?.[2];
    controller.refresh();
    expect(signal?.aborted).toBe(true);
    await controller.prepare(bulkSelection());
    expect(controller.getSnapshot().stage).toBe("reviewing");
    old.resolve(failed("mfa_required"));
    await pending;
    expect(controller.getSnapshot()).toMatchObject({ stage: "reviewing", failure: null });
  });

  it("keeps the original operation, review and reason after unknown results and later rejections", async () => {
    const api = actions();
    const response = deferred<Result<MutationReceipt>>();
    api.applyRosterBulk.execute.mockReturnValueOnce(response.promise);
    const { controller, next } = create(api);
    await controller.prepare(bulkSelection());
    const pending = controller.save(" Coverage ");
    await controller.save("Another reason");
    await controller.retry();
    expect(api.applyRosterBulk.execute).toHaveBeenCalledTimes(1);
    const original = api.applyRosterBulk.execute.mock.calls[0];
    expect(original?.[2]).toMatchObject({
      reviewRevision: "b".repeat(64),
      reason: "Coverage",
      selection: bulkSelection(),
    });
    expect(Object.isFrozen(original?.[2])).toBe(true);
    expect(Object.isFrozen(original?.[2].selection.employeeIds)).toBe(true);
    response.resolve(failed("connection_unavailable"));
    await pending;
    controller.refresh();
    controller.revise();
    await controller.prepare(bulkSelection());
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    expect(api.reviewRosterBulk.execute).toHaveBeenCalledTimes(1);
    for (const code of [
      "mfa_required",
      "roster_bulk_changed",
      "work_period_locked",
      "request_rate_limited",
    ]) {
      api.applyRosterBulk.execute.mockResolvedValueOnce(failed(code));
      await controller.retry();
      expect(controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        operationId: operation,
        review: null,
      });
    }
    await controller.retry();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "saved",
      operationId: null,
      receipt: { id: operation, version: 0 },
    });
    expect(next).toHaveBeenCalledTimes(1);
    for (const call of api.applyRosterBulk.execute.mock.calls) {
      expect(call[1]).toBe(original?.[1]);
      expect(call[2]).toBe(original?.[2]);
    }
  });

  it.each([
    "roster_bulk_changed",
    "roster_bulk_unchanged",
    "shift_changed",
    "work_period_locked",
    "employee_unavailable",
  ])("requires another preview after the first definite %s rejection", async (code) => {
    const api = actions();
    api.applyRosterBulk.execute.mockResolvedValueOnce(failed(code));
    const { controller } = create(api);
    await controller.prepare(bulkSelection());
    await controller.save("Coverage");
    expect(controller.getSnapshot()).toMatchObject({ stage: "conflict", operationId: null });
    await controller.retry();
    await controller.save("Not reviewed");
    expect(api.applyRosterBulk.execute).toHaveBeenCalledTimes(1);
    controller.revise();
    api.reviewRosterBulk.execute.mockResolvedValueOnce(
      success({ ...bulkReviewFixture(), revision: "c".repeat(64) }),
    );
    await controller.prepare(bulkSelection());
    await controller.save("Reviewed again");
    expect(api.applyRosterBulk.execute.mock.lastCall?.[2].reviewRevision).toBe("c".repeat(64));
    expect(controller.getSnapshot().stage).toBe("saved");
  });

  it("releases a first definite access denial and keeps sensitive preview hidden", async () => {
    const api = actions();
    api.applyRosterBulk.execute.mockResolvedValueOnce(failed("company_access_denied"));
    const { controller } = create(api);
    await controller.prepare(bulkSelection());
    await controller.save("Coverage");
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      operationId: null,
      review: null,
    });
    await controller.retry();
    expect(api.applyRosterBulk.execute).toHaveBeenCalledTimes(1);
  });

  it("does not submit an unchanged preview or invent an operation during review", async () => {
    const api = actions();
    api.reviewRosterBulk.execute.mockResolvedValueOnce(
      success({ ...bulkReviewFixture(), changeCount: 0 }),
    );
    const { controller, next } = create(api);
    await controller.prepare(bulkSelection());
    await controller.save("Unchanged");
    expect(controller.getSnapshot().stage).toBe("reviewing");
    expect(api.applyRosterBulk.execute).not.toHaveBeenCalled();
    expect(next).not.toHaveBeenCalled();
  });

  it("contains unexpected adapter errors without discarding a possibly committed command", async () => {
    const api = actions();
    api.reviewRosterBulk.execute.mockRejectedValueOnce(new Error("Technical preview failure"));
    api.applyRosterBulk.execute.mockRejectedValueOnce(new Error("Technical command failure"));
    const { controller } = create(api);
    await controller.prepare(bulkSelection());
    expect(controller.getSnapshot()).toMatchObject({
      stage: "editing",
      failure: { code: "unexpected_error" },
    });
    await controller.prepare(bulkSelection());
    await controller.save("Coverage");
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "unexpected_error" },
      operationId: operation,
    });
    await controller.retry();
    expect(controller.getSnapshot().stage).toBe("saved");
  });

  it("contains an identifier failure before any write and permits an explicit save retry", async () => {
    const next = vi
      .fn(() => operation)
      .mockImplementationOnce(() => {
        throw new Error("Unavailable entropy");
      });
    const { controller, api } = create(actions(), access, next);
    await controller.prepare(bulkSelection());
    await controller.save("Coverage");
    expect(controller.getSnapshot()).toMatchObject({
      stage: "reviewing",
      operationId: null,
      failure: { code: "unexpected_error" },
    });
    expect(api.applyRosterBulk.execute).not.toHaveBeenCalled();
    await controller.save("Coverage");
    expect(controller.getSnapshot().stage).toBe("saved");
  });

  it.each(["read", "save"] as const)(
    "cancels pending %s work on departure and rejects its late result in another activation",
    async (stage) => {
      const api = actions();
      const read = deferred<Result<RosterBulkReview>>();
      const save = deferred<Result<MutationReceipt>>();
      if (stage === "read") api.reviewRosterBulk.execute.mockReturnValueOnce(read.promise);
      else api.applyRosterBulk.execute.mockReturnValueOnce(save.promise);
      const { controller } = create(api);
      const listener = vi.fn();
      const unsubscribe = controller.subscribe(listener);
      let pending: Promise<void>;
      if (stage === "read") pending = controller.prepare(bulkSelection());
      else {
        await controller.prepare(bulkSelection());
        pending = controller.save("Coverage");
      }
      const signal =
        stage === "read"
          ? api.reviewRosterBulk.execute.mock.calls[0]?.[2]
          : api.applyRosterBulk.execute.mock.calls[0]?.[3];
      controller.deactivate();
      unsubscribe();
      listener.mockClear();
      expect(signal?.aborted).toBe(true);
      controller.activate();
      if (stage === "read") read.reject(new Error("Late failure"));
      else save.resolve(success({ id: operation, version: 0 }));
      await pending;
      expect(controller.getSnapshot()).toMatchObject({
        stage: "editing",
        failure: null,
        review: null,
        receipt: null,
      });
      expect(listener).not.toHaveBeenCalled();
    },
  );
});
