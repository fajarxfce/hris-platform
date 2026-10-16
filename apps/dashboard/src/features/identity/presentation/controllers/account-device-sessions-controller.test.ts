import { expect, it, vi } from "vitest";
import { deviceSession, deviceSessionId } from "../../../../../tests/fixtures/device-sessions";
import {
  managedAccount,
  managedAccountId,
  platformActorId,
  platformPermissions,
} from "../../../../../tests/fixtures/managed-accounts";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { AccountDeviceSessionDirectory } from "../../domain/entities/account-device-session-directory";
import type { DeviceSession, DeviceSessionId } from "../../domain/entities/device-session";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import { AccountDeviceSessionsController } from "./account-device-sessions-controller";

const access = { accountId: platformActorId as AccountId, permissions: platformPermissions };
const account = Object.freeze({ ...managedAccount(), id: managedAccountId() as AccountId });
const sessions: readonly DeviceSession[] = [1, 2].map((index) =>
  Object.freeze({ ...deviceSession(index), id: deviceSessionId(index) as DeviceSessionId }),
);
const directory: AccountDeviceSessionDirectory = Object.freeze({ account, sessions });
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((accept, fail) => {
    resolve = accept;
    reject = fail;
  });
  return { promise, resolve, reject };
}
function fixture() {
  const actions = {
    loadAccountDeviceSessions: {
      execute: vi
        .fn<IdentityAdministrationUseCases["loadAccountDeviceSessions"]["execute"]>()
        .mockResolvedValue(success(directory)),
    },
    revokeAccountDeviceSession: {
      execute: vi
        .fn<IdentityAdministrationUseCases["revokeAccountDeviceSession"]["execute"]>()
        .mockResolvedValue(success(null)),
    },
  };
  return {
    actions,
    controller: new AccountDeviceSessionsController(actions, access, managedAccountId()),
  };
}
async function open(f: ReturnType<typeof fixture>) {
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
}

it("pins account and device selection while one reviewed command is pending", async () => {
  const f = fixture();
  await f.controller.revoke();
  await open(f);
  f.controller.activate();
  expect(f.actions.loadAccountDeviceSessions.execute).toHaveBeenCalledExactlyOnceWith(
    access,
    managedAccountId(),
    expect.any(AbortSignal),
  );
  f.controller.review("missing");
  await f.controller.revoke();
  expect(f.actions.revokeAccountDeviceSession.execute).not.toHaveBeenCalled();
  f.controller.review(deviceSessionId());
  f.controller.dismissReview();
  await f.controller.revoke();
  expect(f.actions.revokeAccountDeviceSession.execute).not.toHaveBeenCalled();
  f.controller.review(deviceSessionId());
  const held = pending<Result<null>>();
  f.actions.revokeAccountDeviceSession.execute.mockReturnValueOnce(held.promise);
  const ending = f.controller.revoke();
  f.controller.review(deviceSessionId(2));
  f.controller.dismissReview();
  await f.controller.revoke();
  await f.controller.refresh();
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "revoking",
    account,
    selected: sessions[0],
  });
  expect(f.actions.loadAccountDeviceSessions.execute).toHaveBeenCalledOnce();
  held.resolve(success(null));
  await ending;
  expect(f.actions.revokeAccountDeviceSession.execute).toHaveBeenCalledExactlyOnceWith(
    access,
    managedAccountId(),
    deviceSessionId(),
    expect.any(AbortSignal),
  );
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "ready",
    account,
    sessions: [sessions[1]],
    selected: null,
    ended: true,
  });
  f.controller.deactivate();
});
it("keeps the original account/device after response loss and only explicitly retries it", async () => {
  const f = fixture();
  await open(f);
  f.controller.review(deviceSessionId());
  f.actions.revokeAccountDeviceSession.execute.mockResolvedValueOnce(failed("request_timeout"));
  await f.controller.revoke();
  f.controller.review(deviceSessionId(2));
  f.controller.dismissReview();
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "unconfirmed",
    account,
    selected: sessions[0],
  });
  expect(f.actions.revokeAccountDeviceSession.execute).toHaveBeenCalledOnce();
  await f.controller.revoke();
  expect(
    f.actions.revokeAccountDeviceSession.execute.mock.calls.map((call) => call.slice(0, 3)),
  ).toEqual([
    [access, managedAccountId(), deviceSessionId()],
    [access, managedAccountId(), deviceSessionId()],
  ]);
  f.controller.deactivate();
});
it.each([true, false])(
  "reconciles an uncertain result using a fresh list with absence=%s",
  async (absent) => {
    const f = fixture();
    await open(f);
    f.controller.review(deviceSessionId());
    f.actions.revokeAccountDeviceSession.execute.mockResolvedValueOnce(
      failed("connection_unavailable"),
    );
    await f.controller.revoke();
    f.actions.loadAccountDeviceSessions.execute.mockResolvedValueOnce(
      success({ account, sessions: absent ? sessions.slice(1) : sessions }),
    );
    await f.controller.refresh();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "ready",
      account,
      selected: null,
      ended: absent,
    });
    expect(f.actions.revokeAccountDeviceSession.execute).toHaveBeenCalledOnce();
    f.controller.deactivate();
  },
);
it("retains a scoped uncertain selection if reconciliation is unavailable", async () => {
  const f = fixture();
  await open(f);
  f.controller.review(deviceSessionId());
  f.actions.revokeAccountDeviceSession.execute.mockRejectedValueOnce(
    new Error("Owned write failure"),
  );
  await f.controller.revoke();
  f.actions.loadAccountDeviceSessions.execute.mockResolvedValueOnce(
    failed("connection_unavailable"),
  );
  await f.controller.refresh();
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "unconfirmed",
    account,
    sessions: null,
    selected: sessions[0],
  });
  await f.controller.revoke();
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "ready",
    account,
    sessions: null,
    selected: null,
    ended: true,
  });
  f.controller.deactivate();
});
it("supersedes old reads without accepting a late denied result", async () => {
  const f = fixture();
  const held = pending<Result<AccountDeviceSessionDirectory>>();
  f.actions.loadAccountDeviceSessions.execute.mockReturnValueOnce(held.promise);
  f.controller.activate();
  await f.controller.refresh();
  expect(f.actions.loadAccountDeviceSessions.execute.mock.calls[0]?.[2].aborted).toBe(true);
  held.resolve(failed("platform_administrator_required"));
  await Promise.resolve();
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "ready",
    account,
    sessions,
    failure: null,
  });
  f.controller.deactivate();
});
it.each(["read", "revoke"] as const)(
  "disposal clears metadata and rejects a late %s after readmission",
  async (kind) => {
    const f = fixture();
    await open(f);
    const held = pending<never>();
    let work: Promise<void>;
    if (kind === "read") {
      f.actions.loadAccountDeviceSessions.execute.mockReturnValueOnce(held.promise);
      work = f.controller.refresh();
    } else {
      f.controller.review(deviceSessionId());
      f.actions.revokeAccountDeviceSession.execute.mockReturnValueOnce(held.promise);
      work = f.controller.revoke();
    }
    const listener = vi.fn();
    const unsubscribe = f.controller.subscribe(listener);
    unsubscribe();
    f.controller.deactivate();
    expect(
      kind === "read"
        ? f.actions.loadAccountDeviceSessions.execute.mock.calls[1]?.[2].aborted
        : f.actions.revokeAccountDeviceSession.execute.mock.calls[0]?.[3].aborted,
    ).toBe(true);
    expect(f.controller.getSnapshot()).toMatchObject({
      account: null,
      sessions: null,
      selected: null,
    });
    await open(f);
    held.reject(new Error("Late scoped failure"));
    await work;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "ready",
      account,
      sessions,
      failure: null,
    });
    expect(listener).not.toHaveBeenCalled();
    f.controller.deactivate();
  },
);
it.each([
  "platform_administrator_required",
  "session_revoked",
  "mfa_required",
  "recent_authentication_required",
])("withdraws metadata on %s and cannot loop during unchanged readmission", async (code) => {
  const f = fixture();
  await open(f);
  f.controller.review(deviceSessionId());
  f.actions.revokeAccountDeviceSession.execute.mockResolvedValueOnce(failed(code));
  await f.controller.revoke();
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "unavailable",
    account: null,
    sessions: null,
    selected: null,
    failure: { code },
  });
  f.controller.deactivate();
  f.controller.activate();
  await Promise.resolve();
  expect(f.actions.loadAccountDeviceSessions.execute).toHaveBeenCalledOnce();
  expect(f.actions.revokeAccountDeviceSession.execute).toHaveBeenCalledOnce();
  await f.controller.refresh();
  expect(f.controller.getSnapshot()).toMatchObject({ stage: "ready", account, sessions });
  f.controller.deactivate();
});
it.each([
  "account_not_found",
  "platform_administrator_required",
  "session_revoked",
  "mfa_required",
])("clears a previous uncertain selection when its read returns %s", async (code) => {
  const f = fixture();
  await open(f);
  f.controller.review(deviceSessionId());
  f.actions.revokeAccountDeviceSession.execute.mockResolvedValueOnce(failed("request_timeout"));
  await f.controller.revoke();
  f.actions.loadAccountDeviceSessions.execute.mockResolvedValueOnce(failed(code));
  await f.controller.refresh();
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "unavailable",
    account: null,
    sessions: null,
    selected: null,
    failure: { code },
  });
  await f.controller.revoke();
  expect(f.actions.revokeAccountDeviceSession.execute).toHaveBeenCalledOnce();
  f.controller.deactivate();
});
it("unexpected initial failures remain explicitly recoverable", async () => {
  const f = fixture();
  f.actions.loadAccountDeviceSessions.execute.mockRejectedValueOnce(new Error("Owned failure"));
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("unavailable"));
  expect(f.controller.getSnapshot()).toMatchObject({
    account: null,
    failure: { code: "unexpected_error" },
  });
  expect(f.actions.loadAccountDeviceSessions.execute).toHaveBeenCalledOnce();
  await f.controller.refresh();
  expect(f.controller.getSnapshot().stage).toBe("ready");
  f.controller.deactivate();
});
