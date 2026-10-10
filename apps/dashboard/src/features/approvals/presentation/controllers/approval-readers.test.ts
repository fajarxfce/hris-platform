import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../../core/data/http/http-client";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { createApprovalsFeature } from "../../di/approvals-feature";
import { ApprovalInboxController } from "./approval-inbox-controller";
import { ApprovalRequestController } from "./approval-request-controller";

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "30000000-0000-4000-8000-000000000001";
const access = { companyId: company, permissions: ["approvals.read"] };
const row = {
  id,
  kind: "LEAVE",
  resourceId: "40000000-0000-4000-8000-000000000001",
  authorId: "20000000-0000-4000-8000-000000000001",
  requesterId: null,
  templateId: "50000000-0000-4000-8000-000000000001",
  templateRevision: 0,
  stages: [{ assignees: [] }],
  currentStep: 0,
  status: "BLOCKED",
  version: 0,
  submittedAt: "2026-10-01T09:30:00Z",
  excludedAccountIds: [],
};

for (const kind of ["inbox", "request"] as const)
  describe(`${kind} lifecycle`, () => {
    const reply = kind === "inbox" ? { items: [row], nextCursor: null } : row;
    const create = (request: HttpClient["request"]) => {
      const feature = createApprovalsFeature({ request });
      return kind === "inbox"
        ? new ApprovalInboxController(feature.loadInbox, access, null)
        : new ApprovalRequestController(feature.loadRequest, access, id);
    };
    it("rejects late results and failures after replacement or disposal", async () => {
      const first = deferred<unknown>();
      const last = deferred<unknown>();
      const request = vi
        .fn<HttpClient["request"]>()
        .mockReturnValueOnce(first.promise)
        .mockResolvedValueOnce(reply)
        .mockReturnValueOnce(last.promise);
      const controller = create(request);
      controller.activate();
      controller.activate();
      expect(request).toHaveBeenCalledTimes(1);
      await controller.refresh();
      expect(request.mock.calls[0]?.[1].aborted).toBe(true);
      first.resolve({ unrelated: true });
      await first.promise;
      await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
      const refresh = controller.refresh();
      expect(controller.getSnapshot().stage).toBe("loading");
      expect(JSON.stringify(controller.getSnapshot())).not.toContain(id);
      controller.deactivate();
      expect(request.mock.lastCall?.[1].aborted).toBe(true);
      last.reject(new Error("Late technical failure"));
      await refresh;
      expect(controller.getSnapshot()).toMatchObject({ stage: "loading", failure: null });
      expect(JSON.stringify(controller.getSnapshot())).not.toContain(id);
      await controller.refresh();
      expect(request).toHaveBeenCalledTimes(3);
    });
    it("removes loaded data while refreshing and keeps denied results cleared", async () => {
      const request = vi
        .fn<HttpClient["request"]>()
        .mockResolvedValueOnce(reply)
        .mockResolvedValueOnce({ invalid: true });
      const controller = create(request);
      const listener = vi.fn();
      const unsubscribe = controller.subscribe(listener);
      controller.activate();
      await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
      const refresh = controller.refresh();
      expect(JSON.stringify(controller.getSnapshot())).not.toContain(id);
      await refresh;
      expect(controller.getSnapshot()).toMatchObject({
        stage: "unavailable",
        failure: { code: "invalid_response" },
      });
      expect(JSON.stringify(controller.getSnapshot())).not.toContain(id);
      unsubscribe();
      const calls = listener.mock.calls.length;
      controller.deactivate();
      expect(listener).toHaveBeenCalledTimes(calls);
    });
    it("can remount after React lifecycle cleanup without accepting the old request", async () => {
      const old = deferred<unknown>();
      const request = vi
        .fn<HttpClient["request"]>()
        .mockReturnValueOnce(old.promise)
        .mockResolvedValue(reply);
      const controller = create(request);
      controller.activate();
      controller.deactivate();
      controller.activate();
      old.resolve({ invalid: true });
      await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
      expect(request).toHaveBeenCalledTimes(2);
      controller.deactivate();
    });
  });
