import { expect, it, vi } from "vitest";
import { deviceSession, deviceSessionId } from "../../../../tests/fixtures/device-sessions";
import {
  managedAccountId,
  managedAccountReview,
  platformActorId,
  platformPermissions,
} from "../../../../tests/fixtures/managed-accounts";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId } from "../../../core/domain/identifiers";
import { createIdentityFeature } from "./identity-feature";

const access = { accountId: platformActorId as AccountId, permissions: platformPermissions };
const signal = () => new AbortController().signal;

it("loads the selected account and immutable session metadata with one shared cancellation scope", async () => {
  const account = managedAccountReview();
  const device = deviceSession();
  const sessions = [device];
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValueOnce(account)
    .mockResolvedValueOnce(sessions);
  const active = signal();
  const result = await createIdentityFeature({ request }).loadAccountDeviceSessions.execute(
    access,
    managedAccountId().toUpperCase(),
    active,
  );
  expect(request.mock.calls).toEqual([
    [{ path: `/api/v1/identity/accounts/${managedAccountId()}` }, active],
    [{ path: `/api/v1/identity/accounts/${managedAccountId()}/native-sessions` }, active],
  ]);
  expect(result).toMatchObject({
    ok: true,
    value: { account: { id: managedAccountId() }, sessions },
  });
  if (result.ok) {
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(Object.isFrozen(result.value.account)).toBe(true);
    expect(Object.isFrozen(result.value.sessions)).toBe(true);
    expect(Object.isFrozen(result.value.sessions[0])).toBe(true);
    account.account.displayName = "Changed DTO";
    device.deviceName = "Changed DTO";
    expect(result.value.account.displayName).toBe("Example Colleague 1");
    expect(result.value.sessions[0]?.deviceName).toBe("HRIS Android");
  }
});
it("allows retained sessions of an inactive account and accepts a bounded empty list", async () => {
  const account = managedAccountReview();
  account.account.active = false;
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValueOnce(account)
    .mockResolvedValueOnce([]);
  expect(
    await createIdentityFeature({ request }).loadAccountDeviceSessions.execute(
      access,
      managedAccountId(),
      signal(),
    ),
  ).toMatchObject({ ok: true, value: { account: { active: false }, sessions: [] } });
  expect(request).toHaveBeenCalledTimes(2);
});
it("rejects a mismatched account response before reading its devices", async () => {
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue(managedAccountReview(2));
  expect(
    await createIdentityFeature({ request }).loadAccountDeviceSessions.execute(
      access,
      managedAccountId(),
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  expect(request).toHaveBeenCalledOnce();
});
it.each([[], Array.from({ length: 50 }, (_, index) => deviceSession(index + 1))])(
  "accepts the existing metadata bounds %#",
  async (...sessions) => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce(managedAccountReview())
      .mockResolvedValueOnce(sessions);
    const result = await createIdentityFeature({ request }).loadAccountDeviceSessions.execute(
      access,
      managedAccountId(),
      signal(),
    );
    expect(result).toMatchObject({ ok: true, value: { sessions } });
  },
);
it.each([
  [deviceSession(), deviceSession()],
  Array.from({ length: 51 }, (_, index) => deviceSession(index + 1)),
])("rejects inconsistent session collections %#", async (...sessions) => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValueOnce(managedAccountReview())
    .mockResolvedValueOnce(sessions);
  expect(
    await createIdentityFeature({ request }).loadAccountDeviceSessions.execute(
      access,
      managedAccountId(),
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
});
it("ends the exact account/session family using its explicit 204 contract", async () => {
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue(undefined);
  const active = signal();
  expect(
    await createIdentityFeature({ request }).revokeAccountDeviceSession.execute(
      access,
      managedAccountId().toUpperCase(),
      deviceSessionId().toUpperCase(),
      active,
    ),
  ).toEqual({ ok: true, value: null });
  expect(request).toHaveBeenCalledExactlyOnceWith(
    {
      path: `/api/v1/identity/accounts/${managedAccountId()}/native-sessions/${deviceSessionId()}`,
      method: "DELETE",
      response: { type: "empty", status: 204 },
    },
    active,
  );
});
it("treats only defined session absence as completed without hiding missing accounts or denied access", async () => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockRejectedValueOnce(new HttpResponseError(404, { code: "native_session_not_found" }))
    .mockRejectedValueOnce(new HttpResponseError(404, { code: "account_not_found" }))
    .mockRejectedValueOnce(
      new HttpResponseError(403, {
        code: "platform_administrator_required",
        detail: "Private technical detail",
      }),
    );
  const revoke = createIdentityFeature({ request }).revokeAccountDeviceSession;
  expect(await revoke.execute(access, managedAccountId(), deviceSessionId(), signal())).toEqual({
    ok: true,
    value: null,
  });
  expect(
    await revoke.execute(access, managedAccountId(), deviceSessionId(), signal()),
  ).toMatchObject({ ok: false, failure: { code: "account_not_found" } });
  expect(await revoke.execute(access, managedAccountId(), deviceSessionId(), signal())).toEqual({
    ok: false,
    failure: { code: "platform_administrator_required", fields: {}, parameters: {} },
  });
  expect(request).toHaveBeenCalledTimes(3);
});
it("rejects missing authority and malformed identifiers before transport", async () => {
  const request = vi.fn<HttpClient["request"]>();
  const actions = createIdentityFeature({ request });
  const denied = { ...access, permissions: [] };
  expect(
    await actions.loadAccountDeviceSessions.execute(denied, managedAccountId(), signal()),
  ).toMatchObject({ ok: false, failure: { code: "platform_administrator_required" } });
  expect(
    await actions.revokeAccountDeviceSession.execute(
      denied,
      managedAccountId(),
      deviceSessionId(),
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "platform_administrator_required" } });
  expect(
    await actions.loadAccountDeviceSessions.execute(access, "../account", signal()),
  ).toMatchObject({ ok: false, failure: { code: "invalid_identifier" } });
  for (const [account, session] of [
    ["../account", deviceSessionId()],
    [managedAccountId(), "../device"],
  ] as const) {
    expect(
      await actions.revokeAccountDeviceSession.execute(access, account, session, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_identifier" } });
  }
  expect(request).not.toHaveBeenCalled();
});
it("preserves an account-read failure and never issues the dependent device read", async () => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockRejectedValue(new HttpResponseError(403, { code: "mfa_required" }));
  expect(
    await createIdentityFeature({ request }).loadAccountDeviceSessions.execute(
      access,
      managedAccountId(),
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "mfa_required" } });
  expect(request).toHaveBeenCalledOnce();
});
it.each(["account", "sessions", "revoke"] as const)(
  "propagates cancellation during %s",
  async (stage) => {
    const stopped = new AbortController();
    const request = vi.fn<HttpClient["request"]>();
    if (stage === "sessions") request.mockResolvedValueOnce(managedAccountReview());
    request.mockImplementationOnce(async () => {
      stopped.abort();
      return stage === "account"
        ? managedAccountReview()
        : stage === "sessions"
          ? [deviceSession()]
          : undefined;
    });
    const actions = createIdentityFeature({ request });
    await expect(
      stage === "revoke"
        ? actions.revokeAccountDeviceSession.execute(
            access,
            managedAccountId(),
            deviceSessionId(),
            stopped.signal,
          )
        : actions.loadAccountDeviceSessions.execute(access, managedAccountId(), stopped.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(request).toHaveBeenCalledTimes(stage === "sessions" ? 2 : 1);
  },
);
it("rejects cancelled callers without transport", async () => {
  const stopped = new AbortController();
  stopped.abort();
  const request = vi.fn<HttpClient["request"]>();
  const actions = createIdentityFeature({ request });
  await expect(
    actions.loadAccountDeviceSessions.execute(access, managedAccountId(), stopped.signal),
  ).rejects.toMatchObject({ name: "AbortError" });
  await expect(
    actions.revokeAccountDeviceSession.execute(
      access,
      managedAccountId(),
      deviceSessionId(),
      stopped.signal,
    ),
  ).rejects.toMatchObject({ name: "AbortError" });
  expect(request).not.toHaveBeenCalled();
});
