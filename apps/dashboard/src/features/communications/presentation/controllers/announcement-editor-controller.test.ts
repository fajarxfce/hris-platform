import { describe, expect, it, vi } from "vitest";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toAnnouncement } from "../../data/mappers/announcement-mapper";
import { access, announcementId, companyId, detail } from "../../di/communications-fixture";
import type { Announcement, AnnouncementId } from "../../domain/entities/announcement";
import {
  AnnouncementEditorController,
  type AnnouncementFields,
} from "./announcement-editor-controller";

const operation = "11000000-0000-4000-8000-000000000002";
const fields: AnnouncementFields = {
  title: "Office hours",
  body: "New office hours.",
  audienceKind: "COMPANY",
  targetIds: [],
  acknowledgementRequired: true,
  reason: "Updated hours",
};
const record = (version = 0) =>
  toAnnouncement(detail(version), companyId, announcementId as AnnouncementId, null);
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

describe("announcement editor ownership", () => {
  it("keeps one frozen operation through response loss and later rejection", async () => {
    const pending = deferred<Result<MutationReceipt>>();
    const execute = vi
      .fn()
      .mockReturnValueOnce(pending.promise)
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(success({ id: announcementId, version: 0 }));
    const next = vi.fn(() => operation);
    const controller = new AnnouncementEditorController(
      { loadAnnouncement: { execute: vi.fn() }, saveAnnouncement: { execute } },
      access,
      true,
      announcementId,
      next,
    );
    controller.activate();
    const targets = ["51000000-0000-4000-8000-000000000001"];
    const save = controller.save({ ...fields, audienceKind: "GROUP", targetIds: targets });
    await controller.save(fields);
    expect(next).toHaveBeenCalledTimes(1);
    targets.length = 0;
    pending.resolve(failed("request_timeout"));
    await save;
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    const original = execute.mock.calls[0];
    expect(original?.[2].targetIds).toHaveLength(1);
    expect(Object.isFrozen(original?.[2].targetIds)).toBe(true);
    await controller.refresh();
    await controller.save(fields);
    expect(execute).toHaveBeenCalledTimes(1);
    await controller.retrySave();
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.retrySave();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "saved",
      receipt: { id: announcementId, version: 0 },
      operationId: null,
    });
    expect(execute.mock.calls[1]?.[1]).toBe(original?.[1]);
    expect(execute.mock.calls[2]?.[2]).toBe(original?.[2]);
    controller.deactivate();
  });
  it("requires explicit review after a version conflict and cannot edit a published notice", async () => {
    const load = vi
      .fn()
      .mockResolvedValueOnce(success(record(4)))
      .mockResolvedValueOnce(success({ ...record(6), status: "PUBLISHED" }));
    const save = vi.fn().mockResolvedValue(failed("stale_version"));
    const controller = new AnnouncementEditorController(
      { loadAnnouncement: { execute: load }, saveAnnouncement: { execute: save } },
      access,
      false,
      announcementId,
      () => operation,
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    await controller.save(fields);
    expect(save.mock.calls[0]?.[2]).toMatchObject({ expectedVersion: 4, id: announcementId });
    expect(controller.getSnapshot().stage).toBe("conflict");
    await controller.save(fields);
    expect(save).toHaveBeenCalledTimes(1);
    await controller.refresh();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      announcement: null,
      failure: { code: "announcement_not_draft" },
    });
    controller.deactivate();
  });
  it("drops late reads and late save receipts when their scope is disposed", async () => {
    const old = deferred<Result<Announcement>>();
    const fresh = deferred<Result<Announcement>>();
    const load = vi.fn().mockReturnValueOnce(old.promise).mockReturnValueOnce(fresh.promise);
    const command = deferred<Result<MutationReceipt>>();
    const save = vi.fn().mockReturnValue(command.promise);
    const controller = new AnnouncementEditorController(
      { loadAnnouncement: { execute: load }, saveAnnouncement: { execute: save } },
      access,
      false,
      announcementId,
      () => operation,
    );
    controller.activate();
    const refresh = controller.refresh();
    const oldSignal = load.mock.calls[0]?.[3] as AbortSignal;
    expect(oldSignal.aborted).toBe(true);
    fresh.resolve(success(record(2)));
    await refresh;
    old.resolve(failed("company_access_denied"));
    await old.promise;
    expect(controller.getSnapshot()).toMatchObject({
      stage: "editing",
      announcement: { version: 2 },
    });
    const saving = controller.save(fields);
    const commandSignal = save.mock.calls[0]?.[3] as AbortSignal;
    controller.deactivate();
    expect(commandSignal.aborted).toBe(true);
    command.resolve(success({ id: announcementId, version: 3 }));
    await saving;
    expect(controller.getSnapshot()).toMatchObject({
      stage: "loading",
      receipt: null,
      announcement: null,
      operationId: null,
    });
  });
  it("returns a definitely rejected first attempt to editing without retaining its operation", async () => {
    const execute = vi.fn().mockResolvedValue(failed("invalid_announcement"));
    const controller = new AnnouncementEditorController(
      { loadAnnouncement: { execute: vi.fn() }, saveAnnouncement: { execute } },
      access,
      true,
      announcementId,
      () => operation,
    );
    controller.activate();
    await controller.save(fields);
    expect(controller.getSnapshot()).toMatchObject({
      stage: "editing",
      operationId: null,
      failure: { code: "invalid_announcement" },
    });
    await controller.retrySave();
    expect(execute).toHaveBeenCalledTimes(1);
    controller.deactivate();
  });
});
