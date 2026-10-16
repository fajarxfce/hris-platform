import { expect, it, vi } from "vitest";
import { deviceSession, deviceSessionId } from "../../../../../tests/fixtures/device-sessions";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { DeviceSession, DeviceSessionId } from "../../domain/entities/device-session";
import type { DeviceSessionUseCases } from "../contracts/device-session-use-cases";
import { DeviceSessionsController } from "./device-sessions-controller";

const sessions: readonly DeviceSession[] = [1, 2].map((id) =>
  Object.freeze({ ...deviceSession(id), id: deviceSessionId(id) as DeviceSessionId }),
);
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
    loadDeviceSessions: {
      execute: vi
        .fn<DeviceSessionUseCases["loadDeviceSessions"]["execute"]>()
        .mockResolvedValue(success(sessions)),
    },
    revokeDeviceSession: {
      execute: vi
        .fn<DeviceSessionUseCases["revokeDeviceSession"]["execute"]>()
        .mockResolvedValue(success(null)),
    },
  };
  return { actions, controller: new DeviceSessionsController(actions) };
}
async function open(f: ReturnType<typeof fixture>) {
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
}
it("requires a current review and prevents duplicate commands or concurrent refresh", async () => {
  const f = fixture();
  await f.controller.revoke();
  await open(f);
  f.controller.activate();
  f.controller.review("unknown");
  await f.controller.revoke();
  expect(f.actions.revokeDeviceSession.execute).not.toHaveBeenCalled();
  f.controller.review(deviceSessionId());
  await f.controller.refresh();
  expect(f.actions.loadDeviceSessions.execute).toHaveBeenCalledOnce();
  f.controller.dismissReview();
  await f.controller.revoke();
  expect(f.actions.revokeDeviceSession.execute).not.toHaveBeenCalled();
  f.controller.review(deviceSessionId());
  const held = pending<Result<null>>();
  f.actions.revokeDeviceSession.execute.mockReturnValueOnce(held.promise);
  const ending = f.controller.revoke();
  await f.controller.revoke();
  f.controller.review(deviceSessionId(2));
  f.controller.dismissReview();
  await f.controller.refresh();
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "revoking",
    selected: { id: deviceSessionId() },
  });
  held.resolve(success(null));
  await ending;
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "ready",
    selected: null,
    ended: true,
    sessions: [sessions[1]],
  });
  expect(f.actions.revokeDeviceSession.execute).toHaveBeenCalledOnce();
  f.controller.deactivate();
});
it("retains the exact target across response loss and repeats it only on an explicit action", async () => {
  const f = fixture();
  await open(f);
  f.controller.review(deviceSessionId());
  f.actions.revokeDeviceSession.execute
    .mockResolvedValueOnce(failed("connection_unavailable"))
    .mockResolvedValueOnce(failed("request_timeout"));
  await f.controller.revoke();
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "unconfirmed",
    selected: sessions[0],
    sessions,
  });
  f.controller.review(deviceSessionId(2));
  expect(f.actions.revokeDeviceSession.execute).toHaveBeenCalledOnce();
  await f.controller.revoke();
  await f.controller.revoke();
  expect(f.actions.revokeDeviceSession.execute.mock.calls.map((call) => call[0])).toEqual([
    deviceSessionId(),
    deviceSessionId(),
    deviceSessionId(),
  ]);
  expect(f.controller.getSnapshot()).toMatchObject({ stage: "ready", ended: true });
  f.controller.deactivate();
});
it.each([true, false])(
  "resolves an unknown outcome from a fresh list with target missing=%s",
  async (missing) => {
    const f = fixture();
    await open(f);
    f.controller.review(deviceSessionId());
    f.actions.revokeDeviceSession.execute.mockResolvedValueOnce(failed("request_timeout"));
    await f.controller.revoke();
    f.actions.loadDeviceSessions.execute.mockResolvedValueOnce(
      success(missing ? sessions.slice(1) : sessions),
    );
    await f.controller.refresh();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "ready",
      selected: null,
      ended: missing,
    });
    expect(f.actions.revokeDeviceSession.execute).toHaveBeenCalledOnce();
    f.controller.deactivate();
  },
);
it("retains an unconfirmed target when its outcome read fails without claiming a known revocation failed", async () => {
  const f = fixture();
  await open(f);
  f.controller.review(deviceSessionId());
  f.actions.revokeDeviceSession.execute.mockRejectedValueOnce(
    new Error("Owned unexpected failure"),
  );
  await f.controller.revoke();
  f.actions.loadDeviceSessions.execute.mockResolvedValueOnce(failed("connection_unavailable"));
  await f.controller.refresh();
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "unconfirmed",
    sessions: null,
    selected: sessions[0],
  });
  await f.controller.revoke();
  expect(f.controller.getSnapshot()).toMatchObject({ stage: "ready", ended: true, selected: null });
  expect(f.actions.loadDeviceSessions.execute).toHaveBeenCalledTimes(2);
  f.controller.deactivate();
});
it("supersedes delayed reads and rejects stale failures and values", async () => {
  const f = fixture();
  const old = pending<Result<readonly DeviceSession[]>>();
  f.actions.loadDeviceSessions.execute.mockReturnValueOnce(old.promise);
  f.controller.activate();
  await f.controller.refresh();
  expect(f.actions.loadDeviceSessions.execute.mock.calls[0]?.[0].aborted).toBe(true);
  old.resolve(failed("session_revoked"));
  await Promise.resolve();
  expect(f.controller.getSnapshot()).toMatchObject({ stage: "ready", sessions, failure: null });
  const obsolete = pending<Result<readonly DeviceSession[]>>();
  f.actions.loadDeviceSessions.execute
    .mockReturnValueOnce(obsolete.promise)
    .mockResolvedValueOnce(success([]));
  const reading = f.controller.refresh();
  expect(f.controller.getSnapshot().sessions).toBeNull();
  await f.controller.refresh();
  obsolete.resolve(success(sessions));
  await reading;
  expect(f.controller.getSnapshot().sessions).toEqual([]);
  f.controller.deactivate();
});
it.each(["read", "revoke"] as const)(
  "disposal cancels %s, clears private metadata and ignores its late failure after reactivation",
  async (kind) => {
    const f = fixture();
    await open(f);
    const held = pending<never>();
    let work: Promise<void>;
    if (kind === "read") {
      f.actions.loadDeviceSessions.execute.mockReturnValueOnce(held.promise);
      work = f.controller.refresh();
    } else {
      f.controller.review(deviceSessionId());
      f.actions.revokeDeviceSession.execute.mockReturnValueOnce(held.promise);
      work = f.controller.revoke();
    }
    const listener = vi.fn();
    const unsubscribe = f.controller.subscribe(listener);
    unsubscribe();
    f.controller.deactivate();
    const aborted =
      kind === "read"
        ? f.actions.loadDeviceSessions.execute.mock.calls[1]?.[0]
        : f.actions.revokeDeviceSession.execute.mock.calls[0]?.[1];
    expect(aborted?.aborted).toBe(true);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "loading",
      sessions: null,
      selected: null,
    });
    await open(f);
    held.reject(new Error("Late private failure"));
    await work;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "ready", sessions, failure: null });
    expect(listener).not.toHaveBeenCalled();
    f.controller.deactivate();
  },
);
it.each(["read", "revoke"] as const)(
  "clears account data on a current %s authentication failure",
  async (kind) => {
    const f = fixture();
    await open(f);
    f.controller.review(deviceSessionId());
    f.actions.revokeDeviceSession.execute.mockResolvedValueOnce(
      failed(kind === "read" ? "request_timeout" : "session_revoked"),
    );
    await f.controller.revoke();
    if (kind === "read") {
      f.actions.loadDeviceSessions.execute.mockResolvedValueOnce(failed("authentication_required"));
      await f.controller.refresh();
    }
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      sessions: null,
      selected: null,
      ended: false,
    });
    f.controller.deactivate();
  },
);
it("a failed initial acquisition is explicitly recoverable without a retry loop", async () => {
  const f = fixture();
  f.actions.loadDeviceSessions.execute.mockRejectedValueOnce(new Error("Owned read failure"));
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("unavailable"));
  expect(f.actions.loadDeviceSessions.execute).toHaveBeenCalledOnce();
  expect(f.controller.getSnapshot().failure?.code).toBe("unexpected_error");
  await f.controller.refresh();
  expect(f.controller.getSnapshot().stage).toBe("ready");
  f.controller.deactivate();
});
it("does not loop on a rejected credential when the same workspace is readmitted", async () => {
  const f = fixture();
  f.actions.loadDeviceSessions.execute.mockResolvedValueOnce(failed("session_revoked"));
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("unavailable"));
  f.controller.deactivate();
  f.controller.activate();
  await Promise.resolve();
  expect(f.actions.loadDeviceSessions.execute).toHaveBeenCalledOnce();
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "unavailable",
    sessions: null,
    selected: null,
  });
  await f.controller.refresh();
  expect(f.controller.getSnapshot().stage).toBe("ready");
  f.controller.deactivate();
});
