import { describe, expect, it, vi } from "vitest";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toAnnouncementDelivery } from "../../data/mappers/announcement-delivery-mapper";
import { deliveryDetail } from "../../di/announcement-delivery-fixture";
import { access, announcementId } from "../../di/communications-fixture";
import type { AnnouncementId } from "../../domain/entities/announcement";
import type { AnnouncementDelivery } from "../../domain/entities/announcement-delivery";
import { AnnouncementDeliveryController } from "./announcement-delivery-controller";

const value = () => toAnnouncementDelivery(deliveryDetail(), announcementId as AnnouncementId);
function deferred() {
  let resolve!: (result: Result<AnnouncementDelivery>) => void;
  const promise = new Promise<Result<AnnouncementDelivery>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
describe("delivery observation ownership", () => {
  it("replaces reads, immediately clears old counts, and ignores a late revoked response", async () => {
    const old = deferred(),
      current = deferred();
    const execute = vi
      .fn()
      .mockResolvedValueOnce(success(value()))
      .mockReturnValueOnce(old.promise)
      .mockReturnValueOnce(current.promise);
    const c = new AnnouncementDeliveryController({ execute }, access, announcementId);
    c.activate();
    await Promise.resolve();
    expect(c.getSnapshot().delivery).not.toBeNull();
    const replaced = c.refresh();
    expect(c.getSnapshot().delivery).toBeNull();
    const refresh = c.refresh();
    expect((execute.mock.calls[1]?.[2] as AbortSignal | undefined)?.aborted).toBe(true);
    current.resolve(success(value()));
    await refresh;
    old.resolve(failed("access_denied"));
    await replaced;
    expect(c.getSnapshot()).toMatchObject({ stage: "ready", failure: null });
    c.deactivate();
    expect(c.getSnapshot()).toEqual({ stage: "idle", delivery: null, failure: null });
  });
  it("cancels on hide/disposal and reloads only on a new activation", async () => {
    const old = deferred(),
      execute = vi.fn().mockReturnValueOnce(old.promise).mockResolvedValueOnce(success(value()));
    const c = new AnnouncementDeliveryController({ execute }, access, announcementId),
      listener = vi.fn();
    const unsubscribe = c.subscribe(listener);
    await c.refresh();
    expect(execute).not.toHaveBeenCalled();
    c.activate();
    c.activate();
    expect(execute).toHaveBeenCalledTimes(1);
    c.deactivate();
    expect((execute.mock.calls[0]?.[2] as AbortSignal | undefined)?.aborted).toBe(true);
    old.resolve(success(value()));
    await old.promise;
    await Promise.resolve();
    expect(c.getSnapshot().delivery).toBeNull();
    unsubscribe();
    listener.mockClear();
    c.activate();
    await Promise.resolve();
    expect(c.getSnapshot().stage).toBe("ready");
    expect(execute).toHaveBeenCalledTimes(2);
    expect(listener).not.toHaveBeenCalled();
    c.deactivate();
  });
  it("clears the view on access failure or unexpected exception without an automatic retry", async () => {
    const execute = vi
      .fn()
      .mockResolvedValueOnce(failed("access_denied"))
      .mockRejectedValueOnce(new Error("hidden implementation detail"));
    const c = new AnnouncementDeliveryController({ execute }, access, announcementId);
    c.activate();
    await Promise.resolve();
    expect(c.getSnapshot()).toMatchObject({
      stage: "unavailable",
      delivery: null,
      failure: { code: "access_denied" },
    });
    await c.refresh();
    expect(c.getSnapshot()).toMatchObject({
      stage: "unavailable",
      delivery: null,
      failure: { code: "unexpected_error" },
    });
    expect(execute).toHaveBeenCalledTimes(2);
    c.deactivate();
  });
});
