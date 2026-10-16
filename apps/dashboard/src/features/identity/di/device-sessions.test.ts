import { expect, it, vi } from "vitest";
import { deviceSession, deviceSessionId } from "../../../../tests/fixtures/device-sessions";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import { createIdentityFeature } from "./identity-feature";

const signal = () => new AbortController().signal;
it("acquires only current-account metadata and detaches immutable records", async () => {
  const dto = deviceSession();
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue([dto]);
  const active = signal();
  const result = await createIdentityFeature({ request }).loadDeviceSessions.execute(active);
  expect(request).toHaveBeenCalledExactlyOnceWith({ path: "/api/v1/auth/native/sessions" }, active);
  expect(result).toEqual({ ok: true, value: [dto] });
  if (result.ok) {
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(Object.isFrozen(result.value[0])).toBe(true);
    dto.deviceName = "Changed DTO";
    expect(result.value[0]?.deviceName).toBe("HRIS Android");
  }
});
it("accepts empty and maximum bounded session lists", async () => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValueOnce([])
    .mockResolvedValueOnce(Array.from({ length: 50 }, (_, i) => deviceSession(i + 1)));
  const actions = createIdentityFeature({ request });
  expect(await actions.loadDeviceSessions.execute(signal())).toEqual({ ok: true, value: [] });
  expect(await actions.loadDeviceSessions.execute(signal())).toMatchObject({
    ok: true,
    value: expect.any(Array),
  });
});
it.each([
  [deviceSession(), deviceSession()],
  [deviceSession(), { ...deviceSession(), id: deviceSessionId().toUpperCase() }],
  [{ ...deviceSession(), id: "../another-account" }],
  [{ ...deviceSession(), deviceName: " " }],
  [{ ...deviceSession(), deviceName: "Name\nDevice" }],
  [{ ...deviceSession(), deviceName: "a".repeat(101) }],
  [{ ...deviceSession(), createdAt: "2026-99-01T09:00:00Z" }],
  [{ ...deviceSession(), expiresAt: "2026-10-01T09:00:00Z" }],
  [{ ...deviceSession(), expiresAt: "2026-09-01T09:00:00Z" }],
  Array.from({ length: 51 }, (_, i) => deviceSession(i + 1)),
])("rejects inconsistent or oversized session metadata %#", async (...entries) => {
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue(entries);
  expect(
    await createIdentityFeature({ request }).loadDeviceSessions.execute(signal()),
  ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
});
it("ends the canonical session with the explicit empty acknowledgement contract", async () => {
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue(undefined);
  const active = signal();
  expect(
    await createIdentityFeature({ request }).revokeDeviceSession.execute(
      deviceSessionId().toUpperCase(),
      active,
    ),
  ).toEqual({ ok: true, value: null });
  expect(request).toHaveBeenCalledExactlyOnceWith(
    {
      path: `/api/v1/auth/native/sessions/${deviceSessionId()}`,
      method: "DELETE",
      response: { type: "empty", status: 204 },
    },
    active,
  );
});
it("treats only the defined missing-session result as an already completed revocation", async () => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockRejectedValueOnce(new HttpResponseError(404, { code: "native_session_not_found" }))
    .mockRejectedValueOnce(new HttpResponseError(404, { code: "resource_not_found" }))
    .mockRejectedValueOnce(
      new HttpResponseError(401, { code: "session_revoked", detail: "private session state" }),
    );
  const action = createIdentityFeature({ request }).revokeDeviceSession;
  expect(await action.execute(deviceSessionId(), signal())).toEqual({ ok: true, value: null });
  expect(await action.execute(deviceSessionId(), signal())).toMatchObject({
    ok: false,
    failure: { code: "resource_not_found" },
  });
  expect(await action.execute(deviceSessionId(), signal())).toEqual({
    ok: false,
    failure: { code: "session_revoked", fields: {}, parameters: {} },
  });
  expect(request).toHaveBeenCalledTimes(3);
});
it("rejects unsafe identifiers and cancelled callers before transport", async () => {
  const request = vi.fn<HttpClient["request"]>();
  const actions = createIdentityFeature({ request });
  expect(await actions.revokeDeviceSession.execute("../foreign-session", signal())).toMatchObject({
    ok: false,
    failure: { code: "invalid_identifier" },
  });
  const stopped = new AbortController();
  stopped.abort();
  expect(() => actions.loadDeviceSessions.execute(stopped.signal)).toThrow();
  await expect(
    actions.revokeDeviceSession.execute(deviceSessionId(), stopped.signal),
  ).rejects.toMatchObject({ name: "AbortError" });
  expect(request).not.toHaveBeenCalled();
});
it.each(["list", "revoke"] as const)(
  "propagates cancellation during %s instead of accepting a late response",
  async (kind) => {
    const stopped = new AbortController();
    const request = vi.fn<HttpClient["request"]>().mockImplementationOnce(async () => {
      stopped.abort();
      return kind === "list" ? [deviceSession()] : undefined;
    });
    const actions = createIdentityFeature({ request });
    await expect(
      kind === "list"
        ? actions.loadDeviceSessions.execute(stopped.signal)
        : actions.revokeDeviceSession.execute(deviceSessionId(), stopped.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(request).toHaveBeenCalledOnce();
  },
);
