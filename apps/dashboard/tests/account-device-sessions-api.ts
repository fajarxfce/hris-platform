import { expect, type Page } from "@playwright/test";
import { deviceSession } from "./fixtures/device-sessions";
import { managedAccount, managedAccountId } from "./fixtures/managed-accounts";
import { installManagedAccountsApi } from "./managed-accounts-api";

function gate() {
  let release!: () => void;
  const promise = new Promise<void>((done) => {
    release = done;
  });
  return { promise, release };
}
export async function installAccountDeviceSessionsApi(page: Page, allowed = true) {
  const f = await installManagedAccountsApi(page, { noCompanies: true, allowed });
  f.records.set(managedAccountId(2), managedAccount(2));
  const devices = new Map([
    [
      managedAccountId(),
      new Map(
        [1, 2].map((id) => {
          const device = deviceSession(id);
          return [device.id, device];
        }),
      ),
    ],
    [
      managedAccountId(2),
      new Map([
        [deviceSession(3).id, { ...deviceSession(3), deviceName: "Second account device" }],
      ]),
    ],
  ]);
  const known = new Set(
    [...devices].flatMap(([account, sessions]) =>
      [...sessions.keys()].map((id) => `${account}:${id}`),
    ),
  );
  const reads: string[] = [];
  const writes: { accountId: string; id: string }[] = [];
  let audits = 0;
  let drop = false;
  let readFailure: string | null = null;
  let writeFailure: string | null = null;
  let heldRead: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;
  let heldWrite: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;
  await page.route("**/api/v1/identity/accounts/*/native-sessions**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const match = /^\/api\/v1\/identity\/accounts\/([^/]+)\/native-sessions(?:\/([^/]+))?$/u.exec(
      path,
    );
    if (!match?.[1]) return route.fallback();
    const accountId = match[1];
    const id = match[2];
    if (request.method() === "GET" && !id) {
      reads.push(accountId);
      const snapshot = [...(devices.get(accountId)?.values() ?? [])].map((item) => ({ ...item }));
      const held = heldRead;
      heldRead = null;
      if (held) {
        held.entered.release();
        await held.released.promise;
      }
      if (readFailure) {
        const code = readFailure;
        readFailure = null;
        return route.fulfill({ status: code === "session_revoked" ? 401 : 403, json: { code } });
      }
      return route.fulfill({ json: snapshot });
    }
    if (request.method() !== "DELETE" || !id) return route.fallback();
    expect(request.headers()["x-csrf-token"]).toMatch(/^fixture-csrf-/u);
    expect(request.headers()["idempotency-key"]).toBeUndefined();
    expect(request.postData()).toBeNull();
    writes.push({ accountId, id });
    const held = heldWrite;
    heldWrite = null;
    if (held) {
      held.entered.release();
      await held.released.promise;
    }
    if (writeFailure) {
      const code = writeFailure;
      writeFailure = null;
      return route.fulfill({ status: 403, json: { code } });
    }
    if (!known.has(`${accountId}:${id}`))
      return route.fulfill({ status: 404, json: { code: "native_session_not_found" } });
    if (devices.get(accountId)?.delete(id)) audits += 1;
    if (drop) {
      drop = false;
      return route.abort("failed");
    }
    return route.fulfill({ status: 204, body: "" });
  });
  return {
    ...f,
    devices,
    sessionReads: reads,
    sessionWrites: writes,
    audits: () => audits,
    dropNext: () => {
      drop = true;
    },
    failSessionRead: (code: string) => {
      readFailure = code;
    },
    rejectEnd: (code: string) => {
      writeFailure = code;
    },
    holdSessionRead: () => {
      if (heldRead) throw new Error("A session read is already held");
      heldRead = { entered: gate(), released: gate() };
      return { entered: heldRead.entered.promise, release: heldRead.released.release };
    },
    holdEnd: () => {
      if (heldWrite) throw new Error("An end action is already held");
      heldWrite = { entered: gate(), released: gate() };
      return { entered: heldWrite.entered.promise, release: heldWrite.released.release };
    },
  };
}
