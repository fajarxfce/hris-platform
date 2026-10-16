import { expect, type Page } from "@playwright/test";
import { deviceSession } from "./fixtures/device-sessions";
import { installIdentityApi } from "./identity-api";

function gate() {
  let release!: () => void;
  const promise = new Promise<void>((done) => {
    release = done;
  });
  return { promise, release };
}
export async function installDeviceSessionsApi(page: Page) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    noCompanies: true,
    permissions: [],
    mfaConfigured: true,
  });
  const sessions = new Map(
    [1, 2].map((id) => {
      const item = deviceSession(id);
      return [item.id, item];
    }),
  );
  const reads: string[] = [];
  const writes: string[] = [];
  let drop = false;
  let readFailure: string | null = null;
  let heldRead: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;
  let heldWrite: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;
  await page.route("**/api/v1/auth/native/sessions**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (request.method() === "GET" && path === "/api/v1/auth/native/sessions") {
      reads.push(path);
      const snapshot = [...sessions.values()].map((item) => ({ ...item }));
      const held = heldRead;
      heldRead = null;
      if (held) {
        held.entered.release();
        await held.released.promise;
      }
      if (readFailure) {
        const code = readFailure;
        readFailure = null;
        return route.fulfill({ status: code === "session_revoked" ? 401 : 503, json: { code } });
      }
      return route.fulfill({ json: snapshot });
    }
    const id = path.split("/").at(-1);
    if (request.method() !== "DELETE" || !id) return route.fallback();
    expect(request.headers()["x-csrf-token"]).toMatch(/^fixture-csrf-/u);
    expect(request.postData()).toBeNull();
    writes.push(id);
    const held = heldWrite;
    heldWrite = null;
    if (held) {
      held.entered.release();
      await held.released.promise;
    }
    sessions.delete(id);
    if (drop) {
      drop = false;
      return route.abort("failed");
    }
    return route.fulfill({ status: 204, body: "" });
  });
  return {
    identity,
    sessions,
    reads,
    writes,
    dropNext: () => {
      drop = true;
    },
    failRead: (code: string) => {
      readFailure = code;
    },
    holdRead: () => {
      if (heldRead) throw new Error("A session read is already held");
      heldRead = { entered: gate(), released: gate() };
      return { entered: heldRead.entered.promise, release: heldRead.released.release };
    },
    holdWrite: () => {
      if (heldWrite) throw new Error("A revocation is already held");
      heldWrite = { entered: gate(), released: gate() };
      return { entered: heldWrite.entered.promise, release: heldWrite.released.release };
    },
  };
}
