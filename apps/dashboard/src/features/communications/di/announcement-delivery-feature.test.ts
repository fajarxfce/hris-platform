import { describe, expect, it, vi } from "vitest";
import type { HttpRequest } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import { deliveryDetail } from "./announcement-delivery-fixture";
import { createCommunicationsFeature } from "./communications-feature";
import { access, announcementId, companyId } from "./communications-fixture";

const signal = () => new AbortController().signal;
describe("announcement delivery contract", () => {
  it("reads one scoped immutable aggregate without recipient or token details", async () => {
    const request = vi.fn(async (_input: HttpRequest) => deliveryDetail());
    const result = await createCommunicationsFeature({ request }).loadDelivery.execute(
      access,
      announcementId.toUpperCase(),
      signal(),
    );
    expect(result).toEqual({ ok: true, value: deliveryDetail() });
    if (result.ok) expect(Object.isFrozen(result.value)).toBe(true);
    expect(request).toHaveBeenCalledTimes(1);
    expect(request.mock.calls[0]?.[0].method ?? "GET").toBe("GET");
    expect(request.mock.calls[0]?.[0]).toMatchObject({
      path: `/api/v1/companies/${companyId}/announcements/${announcementId}/delivery`,
    });
  });
  it.each([
    { recipientCount: 5001 },
    { pending: -1 },
    { pending: 0.5 },
    { pending: 2 },
    { unavailableRecipients: 3 },
    { processedDevices: 3001 },
    { acceptedDevices: 8 },
    { rejectedDevices: 4 },
    { observedAt: "invalid" },
    { announcementId: "91000000-0000-4000-8000-000000000001" },
    { token: "must-not-be-exposed" },
  ])("fails closed on inconsistent, unbounded or expanded data: %j", async (changes) => {
    const result = await createCommunicationsFeature({
      request: async () => ({ ...deliveryDetail(), ...changes }),
    }).loadDelivery.execute(access, announcementId, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("accepts empty drafts and unavailable retained publication recipients", async () => {
    for (const count of [0, 5000]) {
      const response = {
        ...deliveryDetail(),
        recipientCount: count,
        pending: 0,
        processing: 0,
        completed: 0,
        failed: 0,
        superseded: 0,
        unavailableRecipients: count,
        processedDevices: 0,
        acceptedDevices: 0,
        rejectedDevices: 0,
      };
      expect(
        await createCommunicationsFeature({ request: async () => response }).loadDelivery.execute(
          access,
          announcementId,
          signal(),
        ),
      ).toEqual({ ok: true, value: response });
    }
  });
  it("does no I/O for absent original permission, malformed ID or cancellation", async () => {
    const request = vi.fn(),
      load = createCommunicationsFeature({ request }).loadDelivery;
    expect(
      await load.execute({ ...access, permissions: [] }, announcementId, signal()),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(await load.execute(access, "bad", signal())).toMatchObject({
      ok: false,
      failure: { code: "announcement_not_found" },
    });
    const aborted = new AbortController();
    aborted.abort();
    expect(() => load.execute(access, announcementId, aborted.signal)).toThrow();
    expect(request).not.toHaveBeenCalled();
  });
  it("preserves cancellation and server revocation without retry", async () => {
    const aborted = new DOMException("Aborted", "AbortError");
    const request = vi
      .fn()
      .mockRejectedValueOnce(aborted)
      .mockRejectedValueOnce(new HttpResponseError(403, { code: "company_access_denied" }));
    const load = createCommunicationsFeature({ request }).loadDelivery;
    await expect(load.execute(access, announcementId, signal())).rejects.toBe(aborted);
    expect(await load.execute(access, announcementId, signal())).toMatchObject({
      ok: false,
      failure: { code: "company_access_denied" },
    });
    expect(request).toHaveBeenCalledTimes(2);
  });
});
