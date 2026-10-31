import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { randomUUID } from "node:crypto";
import { execFileSync, spawn } from "node:child_process";
import { test, expect } from "@playwright/test";

const fixture = JSON.parse(readFileSync(process.env.HRIS_COLLAB_PROOF_FIXTURE, "utf8"));

// Register only when explicitly requested. Browser-only evidence never claims
// the native gate; the driver's result records which experiment actually ran.
if (fixture.nativeDevice) {
  test("Android SDK queue preserves encrypted logical message IDs through credential refresh", async ({ browser, baseURL, request }) => {
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
      const expectedLogicalId = randomUUID();
      const replyLogicalId = randomUUID();
      const offlineBody = `fictional-native-offline-invite-${randomUUID()}`;
      const offlineLogicalId = randomUUID();
      // Credentials go through stdin into the app sandbox, never shell arguments.
      execFileSync(adb, [...args, "shell", "run-as", application, "sh", "-c",
        "'cat > files/protocol-proof/fixture.json'"], {
        input: JSON.stringify({ ...fixture.matrix.native, syncMode: fixture.nativeDevice.syncMode ?? "native", roomId, expectedBody, replyBody, expectedLogicalId, replyLogicalId, offlineBody, offlineLogicalId, offlinePeer: fixture.matrix.nativeweb.userId }),
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
      const sentId = await page.evaluate(input => window.probe.send(input), { roomId, body: expectedBody, logicalId: expectedLogicalId });
      await expect.poll(() => ({ reply: readResult("reply"), instrumentation: readResult("instrumentation") }))
        .toMatchObject({ reply: { receivedEncrypted: true, receivedEventId: sentId } });
      const interim = readResult("reply");
      await expect.poll(() => page.evaluate(body => window.probe.lookup(body), replyBody))
        .toEqual({ roomId, eventId: interim.sentEventId, encrypted: true, logicalId: replyLogicalId });
      await page.evaluate(() => window.probe.stop());
      await context.setOffline(true);
      execFileSync(adb, [...args, "shell", "run-as", application, "sh", "-c",
        "'cat > files/protocol-proof/offline-ready.json'"], {
        input: JSON.stringify({ ready: true }), timeout: 15_000, stdio: ["pipe", "pipe", "pipe"],
      });
      await expect.poll(() => ({ result: readResult("result"), instrumentation: readResult("instrumentation") }))
        .toMatchObject({ result: { receivedEncrypted: true, receivedEventId: sentId }, instrumentation: { complete: true } });
      const received = readResult("result");
      expect(received.syncMode).toBe(fixture.nativeDevice.syncMode ?? "native");
      expect(received.sessionRefreshes).toBeGreaterThanOrEqual(1);
      expect(received.credentialsRotated).toBe(true);
      expect(received.persistedSessionCurrent).toBe(true);
      expect(received.deviceKeyUnchanged).toBe(true);
      expect(received.queuedWhilePaused).toBe(true);
      expect(received.queueTransactionId).toEqual(expect.any(String));
      expect(received.queueTransactionId.length).toBeGreaterThan(0);
      expect(received.receivedLogicalId).toBe(expectedLogicalId);
      expect(received.sentLogicalId).toBe(replyLogicalId);
      expect(received.offlineHistory).toBe("invited");
      expect(received.offlineRoomVersion).toBe("12");
      expect(received.offlineMembershipSource).toBe("sdk_members");
      expect(received.offlineLogicalId).toBe(offlineLogicalId);
      expect(received.offlineQueueTransactionId.length).toBeGreaterThan(0);
      // The instrumentation has already closed SDK resources. Force-stop only
      // the isolated proof application before the recipient returns and joins.
      command("shell", "am", "force-stop", application);
      await context.setOffline(false);
      await page.reload();
      await page.waitForFunction(() => Boolean(window.probe));
      await page.evaluate(session => window.probe.startMatrix(session), fixture.matrix.nativeweb);
      await page.evaluate(id => window.probe.join(id), received.offlineRoomId);
      await expect.poll(() => page.evaluate(id => window.probe.roomReadiness(id), received.offlineRoomId))
        .toMatchObject({ history: "invited", encrypted: true });
      await page.evaluate(id => window.probe.history(id), received.offlineRoomId);
      await expect.poll(() => page.evaluate(body => window.probe.lookup(body), offlineBody))
        .toEqual({ roomId: received.offlineRoomId, eventId: received.offlineSentEventId, encrypted: true, logicalId: offlineLogicalId });
      const offlineWire = await request.get(`${fixture.matrix.baseUrl}/_matrix/client/v3/rooms/${encodeURIComponent(received.offlineRoomId)}/event/${encodeURIComponent(received.offlineSentEventId)}`, {
        headers: { Authorization: `Bearer ${fixture.matrix.nativeweb.accessToken}` },
      });
      expect(offlineWire.ok()).toBe(true);
      const offlineCiphertext = await offlineWire.json();
      expect(offlineCiphertext.type).toBe("m.room.encrypted");
      expect(JSON.stringify(offlineCiphertext)).not.toContain(offlineBody);
      expect(JSON.stringify(offlineCiphertext)).not.toContain(offlineLogicalId);
      for (const eventId of [sentId, received.sentEventId]) {
        const wire = await request.get(`${fixture.matrix.baseUrl}/_matrix/client/v3/rooms/${encodeURIComponent(roomId)}/event/${encodeURIComponent(eventId)}`, {
          headers: { Authorization: `Bearer ${fixture.matrix.nativeweb.accessToken}` },
        });
        expect(wire.ok()).toBe(true);
        const ciphertext = await wire.json();
        expect(ciphertext.type).toBe("m.room.encrypted");
        expect(JSON.stringify(ciphertext)).not.toContain(expectedBody);
        expect(JSON.stringify(ciphertext)).not.toContain(replyBody);
        expect(JSON.stringify(ciphertext)).not.toContain(expectedLogicalId);
        expect(JSON.stringify(ciphertext)).not.toContain(replyLogicalId);
      }
    } finally {
      // Instrumentation records categories/code frames only, never exception messages.
      // Capture it before the owned package is stopped/uninstalled, including failures.
      try {
        writeFileSync(join(dirname(process.env.HRIS_COLLAB_PROOF_FIXTURE), "native-diagnostic.json"), JSON.stringify({
          instrumentation: readResult("instrumentation"),
          progress: readResult("progress"),
          replyComplete: readResult("reply")?.receivedEncrypted === true,
          resultComplete: readResult("result")?.receivedEncrypted === true,
        }), { mode: 0o600 });
      } catch { /* Diagnostics cannot prevent owned resource cleanup. */ }
      instrument?.kill("SIGTERM");
      try { command("shell", "am", "force-stop", application); } catch { /* driver retries owned cleanup */ }
      await page.evaluate(() => window.probe?.stop()).catch(() => {});
      await context.close();
    }
  });
}
