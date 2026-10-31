import { readFileSync } from "node:fs";
import { randomUUID } from "node:crypto";
import { execFileSync, spawn } from "node:child_process";
import { test, expect } from "@playwright/test";

const fixture = JSON.parse(readFileSync(process.env.HRIS_COLLAB_PROOF_FIXTURE, "utf8"));

// Register only when explicitly requested. Browser-only evidence never claims
// the native gate; the driver's result records which experiment actually ran.
if (fixture.nativeDevice) {
  test("Android Rust SDK renews credentials without changing encrypted chat keys", async ({ browser, baseURL, request }) => {
    const { adb, serial, package: application } = fixture.nativeDevice;
    if (serial !== "emulator-5560" || application !== "dev.fajar.hris.collaboration.proof") {
      throw new Error("Invalid native proof target");
    }
    const args = ["-s", serial];
    const command = (...parameters) => execFileSync(adb, [...args, ...parameters], {
      timeout: 15_000, encoding: "utf8", stdio: ["pipe", "pipe", "pipe"], maxBuffer: 64 * 1024,
    });
    const readResult = name => {
      try { return JSON.parse(command("exec-out", "run-as", application, "cat", `files/protocol-proof/${name}.json`)); }
      catch { return null; }
    };
    const context = await browser.newContext();
    const page = await context.newPage();
    let instrument;
    try {
      await page.goto(baseURL);
      await page.waitForFunction(() => Boolean(window.probe));
      await page.evaluate(session => window.probe.startMatrix(session), fixture.matrix.nativeweb);
      const roomId = await page.evaluate(id => window.probe.createRoom(id), fixture.matrix.native.userId);
      const expectedBody = `fictional-native-web-${randomUUID()}`;
      const replyBody = `fictional-native-android-${randomUUID()}`;
      // Credentials go through stdin into the app sandbox, never shell arguments.
      execFileSync(adb, [...args, "shell", "run-as", application, "sh", "-c",
        "'cat > files/protocol-proof/fixture.json'"], {
        input: JSON.stringify({ ...fixture.matrix.native, roomId, expectedBody, replyBody }),
        timeout: 15_000, stdio: ["pipe", "pipe", "pipe"],
      });
      instrument = spawn(adb, [...args, "shell", "am", "instrument", "-w",
        `${application}/.MatrixProofInstrumentation`], { stdio: ["ignore", "ignore", "ignore"] });
      let instrumentError;
      instrument.on("error", () => { instrumentError = true; });
      await expect.poll(() => ({ ready: readResult("ready")?.ready,
        failure: readResult("instrumentation"), instrumentError })).toMatchObject({ ready: true });
      const keys = readResult("ready");
      await expect.poll(async () => {
        try {
          await page.evaluate(device => window.probe.trust(device), {
            userId: keys.userId, deviceId: keys.deviceId, signingKey: keys.ed25519,
          });
          return true;
        } catch { return false; }
      }).toBe(true);
      const sentId = await page.evaluate(input => window.probe.send(input), { roomId, body: expectedBody });
      await expect.poll(() => ({ result: readResult("result"), instrumentation: readResult("instrumentation") }))
        .toMatchObject({ result: { receivedEncrypted: true, receivedEventId: sentId }, instrumentation: { complete: true } });
      const received = readResult("result");
      expect(received.sessionRefreshes).toBeGreaterThanOrEqual(1);
      expect(received.credentialsRotated).toBe(true);
      expect(received.persistedSessionCurrent).toBe(true);
      expect(received.deviceKeyUnchanged).toBe(true);
      await expect.poll(() => page.evaluate(body => window.probe.lookup(body), replyBody))
        .toEqual({ roomId, eventId: received.sentEventId, encrypted: true });
      for (const eventId of [sentId, received.sentEventId]) {
        const wire = await request.get(`${fixture.matrix.baseUrl}/_matrix/client/v3/rooms/${encodeURIComponent(roomId)}/event/${encodeURIComponent(eventId)}`, {
          headers: { Authorization: `Bearer ${fixture.matrix.nativeweb.accessToken}` },
        });
        expect(wire.ok()).toBe(true);
        const ciphertext = await wire.json();
        expect(ciphertext.type).toBe("m.room.encrypted");
        expect(JSON.stringify(ciphertext)).not.toContain(expectedBody);
        expect(JSON.stringify(ciphertext)).not.toContain(replyBody);
      }
    } finally {
      instrument?.kill("SIGTERM");
      try { command("shell", "am", "force-stop", application); } catch { /* driver retries owned cleanup */ }
      await page.evaluate(() => window.probe?.stop()).catch(() => {});
      await context.close();
    }
  });
}
