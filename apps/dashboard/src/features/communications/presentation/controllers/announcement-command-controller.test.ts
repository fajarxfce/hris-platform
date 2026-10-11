import { describe, expect, it, vi } from "vitest";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toAnnouncementAudiencePreview } from "../../data/mappers/announcement-audience-preview-mapper";
import { toAnnouncementReview } from "../../data/mappers/announcement-review-mapper";
import { previewDetail, reviewDetail } from "../../di/announcement-publication-fixture";
import { access, announcementId, companyId } from "../../di/communications-fixture";
import type { AnnouncementId } from "../../domain/entities/announcement";
import type { AnnouncementAudiencePreview } from "../../domain/entities/announcement-audience-preview";
import { AnnouncementCommandController } from "./announcement-command-controller";

const operation = "91000000-0000-4000-8000-000000000001";
const review = (version = 0) =>
  toAnnouncementReview(reviewDetail(version), companyId, announcementId as AnnouncementId);
const preview = (version = 0) =>
  toAnnouncementAudiencePreview(previewDetail(version), review(version).announcement);
const fields = { reason: "Release notice", scheduledFor: "2026-11-01T01:00:00Z" };
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((finish) => {
    resolve = finish;
  });
  return { promise, resolve };
}
const actions = () => ({
  loadReview: { execute: vi.fn().mockResolvedValue(success(review())) },
  previewAudience: { execute: vi.fn().mockResolvedValue(success(preview())) },
  publish: { execute: vi.fn().mockResolvedValue(success({ id: announcementId, version: 1 })) },
  archive: { execute: vi.fn().mockResolvedValue(success({ id: announcementId, version: 1 })) },
  returnToDraft: {
    execute: vi.fn().mockResolvedValue(success({ id: announcementId, version: 1 })),
  },
});
describe("publication command ownership", () => {
  it("requires a successful preview and explicit confirmation before assigning an operation", async () => {
    const calls = actions();
    const next = vi.fn(() => operation);
    const controller = new AnnouncementCommandController(
      calls,
      access,
      announcementId,
      "PUBLISH",
      next,
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    controller.prepare(fields);
    expect(controller.getSnapshot().stage).toBe("confirming");
    expect(next).not.toHaveBeenCalled();
    expect(calls.publish.execute).not.toHaveBeenCalled();
    controller.dismiss();
    expect(controller.getSnapshot().stage).toBe("editing");
    controller.prepare({ ...fields, reason: "" });
    expect(controller.getSnapshot()).toMatchObject({
      stage: "editing",
      failure: { code: "invalid_announcement_command" },
    });
    controller.prepare(fields);
    await Promise.all([controller.confirm(), controller.confirm()]);
    expect(next).toHaveBeenCalledTimes(1);
    expect(calls.publish.execute).toHaveBeenCalledTimes(1);
    expect(controller.getSnapshot()).toMatchObject({ stage: "saved", receipt: { version: 1 } });
    controller.deactivate();
  });
  it("retains one immutable schedule and operation through a lost receipt, MFA and later conflict", async () => {
    const calls = actions();
    calls.publish.execute
      .mockResolvedValueOnce(failed("request_timeout"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_version"));
    const controller = new AnnouncementCommandController(
      calls,
      access,
      announcementId,
      "PUBLISH",
      () => operation,
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    controller.prepare(fields);
    await controller.confirm();
    const original = calls.publish.execute.mock.calls[0];
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.refresh();
    controller.prepare({ ...fields, scheduledFor: null });
    await controller.confirm();
    expect(calls.loadReview.execute).toHaveBeenCalledTimes(1);
    await controller.retry();
    await controller.retry();
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.retry();
    expect(controller.getSnapshot().stage).toBe("saved");
    expect(calls.publish.execute.mock.calls[3]?.[2]).toBe(original?.[2]);
    expect(calls.publish.execute.mock.calls[3]?.[1]).toBe(operation);
    expect(original?.[2]).toMatchObject({ expectedVersion: 0, scheduledFor: fields.scheduledFor });
    expect(Object.isFrozen(original?.[2])).toBe(true);
    controller.deactivate();
  });
  it("requires fresh content and preview after a definite conflict", async () => {
    const calls = actions();
    calls.publish.execute.mockResolvedValueOnce(failed("stale_version"));
    calls.loadReview.execute
      .mockResolvedValueOnce(success(review()))
      .mockResolvedValueOnce(success(review(2)));
    calls.previewAudience.execute
      .mockResolvedValueOnce(success(preview()))
      .mockResolvedValueOnce(success(preview(2)));
    const controller = new AnnouncementCommandController(
      calls,
      access,
      announcementId,
      "PUBLISH",
      () => operation,
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    controller.prepare(fields);
    await controller.confirm();
    expect(controller.getSnapshot().stage).toBe("conflict");
    controller.prepare(fields);
    await controller.confirm();
    expect(calls.publish.execute).toHaveBeenCalledTimes(1);
    await controller.refresh();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "editing",
      review: { announcement: { version: 2 } },
      preview: { version: 2 },
    });
    controller.deactivate();
  });
  it("cancels a pending preview and ignores late success after disposal", async () => {
    const calls = actions();
    const pending = deferred<Result<AnnouncementAudiencePreview>>();
    calls.previewAudience.execute.mockReturnValueOnce(pending.promise);
    const controller = new AnnouncementCommandController(
      calls,
      access,
      announcementId,
      "PUBLISH",
      () => operation,
    );
    controller.activate();
    await vi.waitFor(() => expect(calls.previewAudience.execute).toHaveBeenCalledTimes(1));
    const signal = calls.previewAudience.execute.mock.calls[0]?.[2] as AbortSignal;
    controller.deactivate();
    expect(signal.aborted).toBe(true);
    pending.resolve(success(preview()));
    await pending.promise;
    await Promise.resolve();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "loading",
      review: null,
      preview: null,
    });
  });
  it("cannot restore a cancelled command receipt or release actions absent from the server review", async () => {
    const calls = actions();
    const pending = deferred<Result<MutationReceipt>>();
    calls.archive.execute.mockReturnValueOnce(pending.promise);
    const controller = new AnnouncementCommandController(
      calls,
      access,
      announcementId,
      "ARCHIVE",
      () => operation,
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    expect(calls.previewAudience.execute).not.toHaveBeenCalled();
    controller.prepare({ reason: "Withdraw notice", scheduledFor: null });
    const submitted = controller.confirm();
    const signal = calls.archive.execute.mock.calls[0]?.[3] as AbortSignal;
    controller.deactivate();
    expect(signal.aborted).toBe(true);
    pending.resolve(success({ id: announcementId, version: 1 }));
    await submitted;
    expect(controller.getSnapshot()).toMatchObject({ stage: "loading", receipt: null });
    calls.loadReview.execute.mockResolvedValue(success({ ...review(), availableActions: [] }));
    const denied = new AnnouncementCommandController(
      calls,
      access,
      announcementId,
      "PUBLISH",
      () => operation,
    );
    denied.activate();
    await vi.waitFor(() =>
      expect(denied.getSnapshot()).toMatchObject({
        stage: "unavailable",
        failure: { code: "announcement_action_unavailable" },
      }),
    );
    expect(calls.previewAudience.execute).not.toHaveBeenCalled();
    denied.deactivate();
  });
});
