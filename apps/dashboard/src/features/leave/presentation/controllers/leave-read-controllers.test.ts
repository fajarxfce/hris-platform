import { expect, it, vi } from "vitest";
import { leaveRecord, leaveSummary } from "../../../../../tests/fixtures/leave";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toLeaveRequestDetails } from "../../data/mappers/leave-request-details-mapper";
import { toLeaveRequestPage } from "../../data/mappers/leave-request-mapper";
import type { LeaveRequestPage } from "../../domain/entities/leave-request";
import type { LeaveRequestDetails } from "../../domain/entities/leave-request-details";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveRequestController } from "./leave-request-controller";
import { LeaveRequestsController } from "./leave-requests-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["leave.read"] };
const query = { employeeId: null, status: null, after: null };
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

it("detail refresh aborts obsolete work and cannot restore stale data after a new access failure", async () => {
  const detail = toLeaveRequestDetails(leaveRecord(), companyId, null);
  const load = vi.fn<LeaveUseCases["loadRequest"]["execute"]>().mockResolvedValue(success(detail));
  const controller = new LeaveRequestController({ execute: load }, access, detail.id, "4");
  controller.activate();
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
  expect(load).toHaveBeenCalledTimes(1);
  expect(load.mock.lastCall?.slice(0, 3)).toEqual([access, detail.id, "4"]);
  const old = deferred<Result<LeaveRequestDetails>>();
  load.mockReturnValueOnce(old.promise);
  const first = controller.refresh();
  const signal = load.mock.lastCall?.[3];
  expect(controller.getSnapshot().request).toBeNull();
  load.mockResolvedValueOnce(failed("leave_request_not_found"));
  await controller.refresh();
  expect(signal?.aborted).toBe(true);
  old.resolve(success(detail));
  await first;
  expect(controller.getSnapshot()).toMatchObject({
    stage: "unavailable",
    request: null,
    failure: { code: "leave_request_not_found" },
  });
  controller.deactivate();
});

it.each(["result", "failure"] as const)(
  "detail disposal rejects a late %s and releases subscriptions",
  async (kind) => {
    const detail = toLeaveRequestDetails(leaveRecord(), companyId, null);
    const held = deferred<Result<LeaveRequestDetails>>();
    const load = vi.fn<LeaveUseCases["loadRequest"]["execute"]>().mockReturnValue(held.promise);
    const controller = new LeaveRequestController({ execute: load }, access, detail.id, null);
    const listener = vi.fn();
    const unsubscribe = controller.subscribe(listener);
    controller.activate();
    const work = controller.refresh();
    const signal = load.mock.lastCall?.[3];
    unsubscribe();
    const before = listener.mock.calls.length;
    controller.deactivate();
    if (kind === "result") held.resolve(success(detail));
    else held.reject(new Error("Late technical failure"));
    await work;
    expect(signal?.aborted).toBe(true);
    expect(listener).toHaveBeenCalledTimes(before);
    expect(controller.getSnapshot()).toEqual({ stage: "loading", request: null, failure: null });
    await controller.refresh();
    expect(load).toHaveBeenCalledTimes(2);
  },
);

it("directory refresh cancels obsolete pages and clears prior data on access loss", async () => {
  const page = toLeaveRequestPage(
    { items: [leaveSummary(leaveRecord())], nextCursor: null },
    companyId,
    query,
  );
  const load = vi.fn<LeaveUseCases["loadRequests"]["execute"]>().mockResolvedValue(success(page));
  const controller = new LeaveRequestsController({ execute: load }, access, query);
  controller.activate();
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().page).toBe(page));
  expect(load).toHaveBeenCalledTimes(1);
  const old = deferred<Result<LeaveRequestPage>>();
  load.mockReturnValueOnce(old.promise);
  const first = controller.refresh();
  const signal = load.mock.lastCall?.[2];
  load.mockResolvedValueOnce(failed("access_denied"));
  await controller.refresh();
  old.reject(new Error("Obsolete error"));
  await first;
  expect(signal?.aborted).toBe(true);
  expect(controller.getSnapshot()).toMatchObject({
    stage: "unavailable",
    page: null,
    failure: { code: "access_denied" },
  });
  controller.deactivate();
});

it("directory disposal removes its snapshot and ignores a late page", async () => {
  const page = toLeaveRequestPage(
    { items: [leaveSummary(leaveRecord())], nextCursor: null },
    companyId,
    query,
  );
  const load = vi.fn<LeaveUseCases["loadRequests"]["execute"]>().mockResolvedValue(success(page));
  const controller = new LeaveRequestsController({ execute: load }, access, query);
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().page).toBe(page));
  const held = deferred<Result<LeaveRequestPage>>();
  load.mockReturnValueOnce(held.promise);
  const reading = controller.refresh();
  const signal = load.mock.lastCall?.[2];
  controller.deactivate();
  held.resolve(success(page));
  await reading;
  expect(signal?.aborted).toBe(true);
  expect(controller.getSnapshot()).toEqual({ stage: "loading", page: null, failure: null });
});
