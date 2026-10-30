import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { ApprovalHistory } from "../../domain/entities/approval-history";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { ApprovalHistoryController } from "./approval-history-controller";

const access = { companyId: "10000000-0000-4000-8000-000000000001" as CompanyId, permissions: [] };
const id = "30000000-0000-4000-8000-000000000001";
// These tests exercise ownership of a use-case result; transport validation is covered separately.
const history = (version: number, nextCursor: string | null) =>
  ({
    request: { id, version },
    decisions: [],
    reassignments: { items: [], nextCursor },
  }) as unknown as ApprovalHistory;
const deferred = <T>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
};

describe("approval history ownership", () => {
  it("uses one page, pins the observed version and requires refresh after conflict", async () => {
    const execute = vi
      .fn<ApprovalsUseCases["loadHistory"]["execute"]>()
      .mockResolvedValueOnce(success(history(52, "50")))
      .mockResolvedValueOnce(success(history(52, null)))
      .mockResolvedValueOnce(failed("approval_changed"))
      .mockResolvedValueOnce(success(history(53, "50")));
    const controller = new ApprovalHistoryController({ execute }, access, id);
    await controller.next();
    controller.activate();
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    await controller.next();
    expect(execute.mock.calls[1]?.slice(0, 4)).toEqual([access, id, 50, 52]);
    expect(controller.getSnapshot()).toMatchObject({
      after: 50,
      history: { request: { version: 52 } },
    });
    await controller.next();
    expect(execute).toHaveBeenCalledTimes(2);
    await controller.first();
    expect(execute.mock.calls[2]?.slice(0, 4)).toEqual([access, id, null, 52]);
    expect(controller.getSnapshot()).toMatchObject({
      history: null,
      stage: "unavailable",
      failure: { code: "approval_changed" },
    });
    await controller.first();
    await controller.next();
    expect(execute).toHaveBeenCalledTimes(3);
    await controller.refresh();
    expect(execute.mock.calls[3]?.slice(0, 4)).toEqual([access, id, null, null]);
    expect(controller.getSnapshot()).toMatchObject({
      after: null,
      history: { request: { version: 53 } },
    });
    controller.deactivate();
  });
  it("cancels hidden/replaced reads, clears retained reasons and fences late results on remount", async () => {
    const old = deferred<Awaited<ReturnType<ApprovalsUseCases["loadHistory"]["execute"]>>>();
    const next = deferred<Awaited<ReturnType<ApprovalsUseCases["loadHistory"]["execute"]>>>();
    const execute = vi
      .fn<ApprovalsUseCases["loadHistory"]["execute"]>()
      .mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce(success(history(52, "50")))
      .mockReturnValueOnce(next.promise)
      .mockResolvedValueOnce(failed("approval_not_found"));
    const controller = new ApprovalHistoryController({ execute }, access, id);
    const listener = vi.fn(),
      unsubscribe = controller.subscribe(listener);
    controller.activate();
    controller.deactivate();
    controller.activate();
    expect(execute.mock.calls[0]?.[4].aborted).toBe(true);
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    old.resolve(success(history(1, null)));
    await old.promise;
    expect(controller.getSnapshot().history?.request.version).toBe(52);
    const pending = controller.next();
    expect(controller.getSnapshot().history).toBeNull();
    await controller.refresh();
    expect(execute.mock.calls[2]?.[4].aborted).toBe(true);
    next.resolve(success(history(52, null)));
    await pending;
    expect(controller.getSnapshot()).toMatchObject({
      history: null,
      failure: { code: "approval_not_found" },
    });
    unsubscribe();
    const calls = listener.mock.calls.length;
    controller.deactivate();
    await controller.refresh();
    expect(listener).toHaveBeenCalledTimes(calls);
    expect(execute).toHaveBeenCalledTimes(4);
    expect(controller.getSnapshot()).toMatchObject({ history: null, failure: null });
  });
});
