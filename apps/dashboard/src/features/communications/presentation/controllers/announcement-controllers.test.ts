import { describe, expect, it, vi } from "vitest";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toAnnouncement, toAnnouncementPage } from "../../data/mappers/announcement-mapper";
import {
  access,
  announcementId,
  companyId,
  detail,
  summary,
} from "../../di/communications-fixture";
import type { Announcement, AnnouncementId } from "../../domain/entities/announcement";
import type { AnnouncementPage } from "../../domain/entities/announcement-page";
import { AnnouncementController } from "./announcement-controller";
import { AnnouncementListController } from "./announcement-list-controller";

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
const page = (version: number) =>
  toAnnouncementPage({ items: [summary(version)], nextCursor: null }, companyId, null, null);

describe("announcement request ownership", () => {
  it("drops a late failure after a replacement refresh and cancels on disposal", async () => {
    const old = deferred<Result<AnnouncementPage>>();
    const current = deferred<Result<AnnouncementPage>>();
    const execute = vi.fn().mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise);
    const controller = new AnnouncementListController(
      { loadAnnouncements: { execute }, loadHistory: { execute: vi.fn() } },
      access,
      null,
      null,
    );
    controller.activate();
    const refreshed = controller.refresh();
    const superseded = execute.mock.calls[0]?.[2] as AbortSignal | undefined;
    expect(superseded?.aborted).toBe(true);
    current.resolve(success(page(2)));
    await refreshed;
    old.resolve(failed("company_access_denied"));
    await old.promise;
    await Promise.resolve();
    expect(controller.getSnapshot().page?.items[0]?.version).toBe(2);
    expect(controller.getSnapshot().failure).toBeNull();
    controller.deactivate();
    expect(controller.getSnapshot().page).toBeNull();
    expect(controller.getSnapshot().stage).toBe("idle");
  });
  it("routes history through its use case and preserves displayed rows only while refresh is pending", async () => {
    const history = { execute: vi.fn().mockResolvedValue(success(page(1))) };
    const directory = { execute: vi.fn() };
    const controller = new AnnouncementListController(
      { loadAnnouncements: directory, loadHistory: history },
      access,
      announcementId,
      null,
    );
    controller.activate();
    await Promise.resolve();
    expect(history.execute).toHaveBeenCalledWith(
      access,
      announcementId,
      null,
      expect.any(AbortSignal),
    );
    expect(directory.execute).not.toHaveBeenCalled();
    const pending = deferred<Result<AnnouncementPage>>();
    history.execute.mockReturnValueOnce(pending.promise);
    const refreshed = controller.refresh();
    expect(controller.getSnapshot().page).not.toBeNull();
    pending.resolve(failed("access_denied"));
    await refreshed;
    expect(controller.getSnapshot().page).toBeNull();
    controller.deactivate();
  });
  it("a disposed detail cannot publish a delayed revision or retain private text", async () => {
    const pending = deferred<Result<Announcement>>();
    const execute = vi.fn(() => pending.promise);
    const controller = new AnnouncementController({ execute }, access, announcementId, null);
    controller.activate();
    controller.deactivate();
    pending.resolve(
      success(toAnnouncement(detail(), companyId, announcementId as AnnouncementId, null)),
    );
    await pending.promise;
    await Promise.resolve();
    expect(controller.getSnapshot()).toEqual({ stage: "idle", announcement: null, failure: null });
  });
});
