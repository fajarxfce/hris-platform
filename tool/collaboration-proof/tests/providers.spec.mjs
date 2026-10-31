import { readFileSync } from "node:fs";
import { randomUUID } from "node:crypto";
import { test, expect } from "@playwright/test";
import { AccessToken, RoomServiceClient } from "livekit-server-sdk";

const fixture = JSON.parse(readFileSync(process.env.HRIS_COLLAB_PROOF_FIXTURE, "utf8"));
const marker = `fictional-proof-${randomUUID()}`;

async function createClient(browser, baseURL, session) {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(baseURL);
  await page.waitForFunction(() => Boolean(window.probe));
  await page.evaluate(session => window.probe.startMatrix(session), session);
  return { context, page, session };
}

async function verifyPeer(owner, peer) {
  const keys = await peer.page.evaluate(() => window.probe.ownKeys());
  await expect.poll(async () => {
    try {
      await owner.page.evaluate(device => window.probe.trust(device), {
        userId: peer.session.userId, deviceId: peer.session.deviceId, signingKey: keys.ed25519,
      });
      return true;
    } catch (error) {
      return String(error.message).split("\n")[0].replace(/[A-Za-z0-9_=-]{32,}/gu, "[redacted]");
    }
  }).toBe(true);
}

async function mediaToken(identity, room) {
  const token = new AccessToken(fixture.media.apiKey, fixture.media.apiSecret, { identity, ttl: "5m" });
  token.addGrant({ roomJoin: true, room, canPublish: true, canSubscribe: true, canPublishData: true });
  return token.toJwt();
}

test("real Matrix E2EE survives receiver restart and carries a client-owned key for real encrypted audio/video", async ({ browser, baseURL, request }) => {
  const clients = [];
  const service = new RoomServiceClient(fixture.media.httpUrl, fixture.media.apiKey, fixture.media.apiSecret);
  const mediaRoom = `proof-${randomUUID()}`;
  try {
    const alice = await createClient(browser, baseURL, fixture.matrix.alice);
    clients.push(alice);
    const bob = await createClient(browser, baseURL, fixture.matrix.bob);
    clients.push(bob);
    const roomId = await alice.page.evaluate(id => window.probe.createRoom(id), bob.session.userId);
    await bob.page.evaluate(id => window.probe.join(id), roomId);
    await verifyPeer(alice, bob);
    await verifyPeer(bob, alice);
    const eventId = await alice.page.evaluate(input => window.probe.send(input), { roomId, body: marker });
    await expect.poll(() => bob.page.evaluate(body => window.probe.lookup(body), marker)).toEqual({ roomId, eventId, encrypted: true });

    const eventUrl = `${fixture.matrix.baseUrl}/_matrix/client/v3/rooms/${encodeURIComponent(roomId)}/event/${encodeURIComponent(eventId)}`;
    const wire = await request.get(eventUrl, { headers: { Authorization: `Bearer ${alice.session.accessToken}` } });
    expect(wire.ok()).toBe(true);
    const ciphertext = await wire.json();
    expect(ciphertext.type).toBe("m.room.encrypted");
    expect(ciphertext.content.algorithm).toBe("m.megolm.v1.aes-sha2");
    expect(JSON.stringify(ciphertext)).not.toContain(marker);

    await bob.page.evaluate(() => window.probe.stop());
    await bob.context.setOffline(true);
    const delayed = `${marker}-while-recipient-offline`;
    const delayedId = await alice.page.evaluate(input => window.probe.send(input), { roomId, body: delayed });
    await bob.context.setOffline(false);
    await bob.page.reload();
    await bob.page.waitForFunction(() => Boolean(window.probe));
    await bob.page.evaluate(session => window.probe.startMatrix(session), bob.session);
    await expect.poll(() => bob.page.evaluate(body => window.probe.lookup(body), delayed)).toEqual({ roomId, eventId: delayedId, encrypted: true });

    // First message to an offline invitee must remain decryptable when the sender
    // is no longer online to answer key requests. Preserve Bob's already verified
    // encrypted store, but stop him before this second room exists.
    await bob.page.evaluate(() => window.probe.stop());
    await bob.context.setOffline(true);
    const invitedRoom = await alice.page.evaluate(id => window.probe.createRoom(id, "invited"), bob.session.userId);
    await expect.poll(() => alice.page.evaluate(id => window.probe.roomReadiness(id), invitedRoom)).toMatchObject({ history: "invited", encrypted: true, joined: 1, invited: 1 });
    const firstMessage = `${marker}-before-recipient-join`;
    const logicalId = randomUUID();
    const firstId = await alice.page.evaluate(input => window.probe.send(input), { roomId: invitedRoom, body: firstMessage, logicalId });
    const acceptedFirst = await request.get(`${fixture.matrix.baseUrl}/_matrix/client/v3/rooms/${encodeURIComponent(invitedRoom)}/event/${encodeURIComponent(firstId)}`, { headers: { Authorization: `Bearer ${alice.session.accessToken}` } });
    expect(acceptedFirst.ok()).toBe(true);
    expect((await acceptedFirst.json()).type).toBe("m.room.encrypted");
    await alice.page.evaluate(() => window.probe.stop());
    await alice.context.setOffline(true);
    await bob.context.setOffline(false);
    await bob.page.reload();
    await bob.page.waitForFunction(() => Boolean(window.probe));
    await bob.page.evaluate(session => window.probe.startMatrix(session), bob.session);
    await bob.page.evaluate(id => window.probe.join(id), invitedRoom);
    await expect.poll(() => bob.page.evaluate(id => window.probe.roomReadiness(id), invitedRoom)).toMatchObject({ history: "invited", encrypted: true });
    await bob.page.evaluate(id => window.probe.history(id), invitedRoom);
    await expect.poll(() => bob.page.evaluate(body => window.probe.lookup(body), firstMessage)).toEqual({ roomId: invitedRoom, eventId: firstId, encrypted: true, logicalId });
    const invitedWire = await request.get(`${fixture.matrix.baseUrl}/_matrix/client/v3/rooms/${encodeURIComponent(invitedRoom)}/event/${encodeURIComponent(firstId)}`, { headers: { Authorization: `Bearer ${bob.session.accessToken}` } });
    expect(invitedWire.ok()).toBe(true);
    const invitedCiphertext = await invitedWire.json();
    expect(invitedCiphertext.type).toBe("m.room.encrypted");
    expect(JSON.stringify(invitedCiphertext)).not.toContain(firstMessage);
    expect(JSON.stringify(invitedCiphertext)).not.toContain(logicalId);
    await alice.context.setOffline(false);
    await alice.page.reload();
    await alice.page.waitForFunction(() => Boolean(window.probe));
    await alice.page.evaluate(session => window.probe.startMatrix(session), alice.session);

    await expect.poll(() => alice.page.evaluate(id => window.probe.roomReadiness(id), roomId)).toMatchObject({ history: "joined", encrypted: true, joined: 2 });
    const keyEvent = await alice.page.evaluate(room => window.probe.shareMediaKey(room), roomId);
    await expect.poll(() => bob.page.evaluate(async input => ({ ready: await window.probe.receiveMediaKey(input), event: await window.probe.eventStatus(input) }), { roomId, eventId: keyEvent })).toMatchObject({ ready: true });
    const keyWire = await request.get(`${fixture.matrix.baseUrl}/_matrix/client/v3/rooms/${encodeURIComponent(roomId)}/event/${encodeURIComponent(keyEvent)}`, { headers: { Authorization: `Bearer ${alice.session.accessToken}` } });
    const keyCiphertext = await keyWire.json();
    expect(keyCiphertext.type).toBe("m.room.encrypted");
    expect(JSON.stringify(keyCiphertext)).not.toContain("isolated-e2ee-proof");
    expect(JSON.stringify(keyCiphertext)).not.toContain('"key"');

    await service.createRoom({ name: mediaRoom, emptyTimeout: 30, maxParticipants: 3 });
    const aliceMedia = await mediaToken("fixture-alice", mediaRoom);
    const bobMedia = await mediaToken("fixture-bob", mediaRoom);
    await alice.page.evaluate(options => window.probe.startMedia(options), { url: fixture.media.wsUrl, token: aliceMedia });
    await bob.page.evaluate(options => window.probe.startMedia(options), { url: fixture.media.wsUrl, token: bobMedia });
    await alice.page.evaluate(() => window.probe.publishMedia());
    await expect.poll(() => bob.page.evaluate(() => window.probe.decodedMedia())).toMatchObject({
      connected: true, encrypted: true, videoWidth: 320, videoHeight: 180,
    });
    await expect.poll(async () => {
      const decoded = await bob.page.evaluate(() => window.probe.decodedMedia());
      return { sound: decoded.audioPeak > 0.01, decoded, stats: await bob.page.evaluate(() => window.probe.mediaStats()) };
    }).toMatchObject({ sound: true });

    const outsider = await browser.newContext();
    const outsiderPage = await outsider.newPage();
    clients.push({ context: outsider, page: outsiderPage });
    await outsiderPage.goto(baseURL);
    await outsiderPage.waitForFunction(() => Boolean(window.probe));
    await outsiderPage.evaluate(options => window.probe.startMedia(options), {
      url: fixture.media.wsUrl, token: await mediaToken("fixture-wrong-key", mediaRoom), wrongKey: true,
    });
    await expect.poll(() => service.listParticipants(mediaRoom)).toHaveLength(3);
    await expect.poll(async () => (await outsiderPage.evaluate(() => window.probe.mediaStats())).bytesReceived).toBeGreaterThan(10_000);
    await expect.poll(async () => (await bob.page.evaluate(() => window.probe.mediaStats())).framesDecoded).toBeGreaterThan(10);
    // Actual encrypted packets reach the peer, but its unrelated key cannot
    // decode them. The admitted peer decodes the same publisher's live stream.
    for (let observation = 0; observation < 8; observation++) {
      await expect.poll(async () => (await bob.page.evaluate(() => window.probe.decodedMedia())).audioPeak).toBeGreaterThan(0.01);
      const rejected = await outsiderPage.evaluate(() => window.probe.decodedMedia());
      expect(rejected.connected).toBe(true);
      expect(rejected.videoWidth).toBe(0);
      expect(rejected.audioPeak).toBe(0);
    }
    expect((await outsiderPage.evaluate(() => window.probe.mediaStats())).framesDecoded).toBe(0);
    for (const participant of await service.listParticipants(mediaRoom)) {
      expect(participant.metadata).toBe("");
    }
  } finally {
    for (const { page, context } of clients.reverse()) {
      await page.evaluate(() => window.probe?.stop()).catch(() => {});
      await context.close();
    }
    await service.deleteRoom(mediaRoom).catch(() => {});
  }
});
