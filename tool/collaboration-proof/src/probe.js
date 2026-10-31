import { createClient, ClientEvent, RoomEvent } from "matrix-js-sdk";
import {
  ExternalE2EEKeyProvider,
  LocalAudioTrack,
  LocalVideoTrack,
  Room,
  RoomEvent as MediaEvent,
} from "livekit-client";
import EncryptionWorker from "livekit-client/e2ee-worker?worker";

let client;
let mediaRoom;
let encryptionWorker;
let audioContext;
let oscillator;
let paintTimer;
let videoTrack;
let audioTrack;
let analyser;
let mediaKey;
let receivedVideo;
let eventListener;
const remoteTracks = [];
const seen = new Map();
const mutedLogger = {
  trace() {}, debug() {}, info() {}, warn() {}, error() {}, log() {},
  getChild() { return this; },
};

async function startMatrix(session) {
  await stopMatrix();
  client = createClient({ ...session, logger: mutedLogger });
  await client.initRustCrypto({ cryptoDatabasePrefix: `hris-proof-${session.deviceId}` });
  eventListener = async (event, room) => {
    if (!room) return;
    try {
      await client.decryptEventIfNeeded(event);
      const content = event.getContent();
      if (event.getType() === "m.room.message" && typeof content.body === "string") {
        seen.set(content.body, {
          roomId: room.roomId,
          eventId: event.getId(),
          encrypted: event.getWireType() === "m.room.encrypted",
          ...(typeof content["org.hris.message_id"] === "string" ? { logicalId: content["org.hris.message_id"] } : {}),
        });
      }
    } catch {
      // Crypto may finish when an incoming room key arrives. Reads retry decryption.
    }
  };
  client.on(RoomEvent.Timeline, eventListener);
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => { cleanup(); reject(new Error("Matrix initial sync timed out")); }, 30_000);
    const onSync = (state) => {
      if (state === "PREPARED") { cleanup(); resolve(); }
    };
    function cleanup() { clearTimeout(timer); client.off(ClientEvent.Sync, onSync); }
    client.on(ClientEvent.Sync, onSync);
    client.startClient({ initialSyncLimit: 50 });
  });
  return { userId: client.getUserId(), deviceId: client.getDeviceId() };
}

async function stopMatrix() {
  if (!client) return;
  if (eventListener) client.off(RoomEvent.Timeline, eventListener);
  client.stopClient();
  client = undefined;
  eventListener = undefined;
  seen.clear();
}

async function lookup(body) {
  for (const room of client.getRooms()) {
    for (const event of room.getLiveTimeline().getEvents()) {
      try { await client.decryptEventIfNeeded(event); } catch { continue; }
      if (event.getType() === "m.room.message" && event.getContent().body === body) {
        const logicalId = event.getContent()["org.hris.message_id"];
        return { roomId: room.roomId, eventId: event.getId(), encrypted: event.getWireType() === "m.room.encrypted",
          ...(typeof logicalId === "string" ? { logicalId } : {}) };
      }
    }
  }
  return seen.get(body) ?? null;
}

async function startMedia({ url, token, wrongKey = false }) {
  await stopMedia();
  if (!mediaKey && !wrongKey) throw new Error("Media key has not been acquired over E2EE");
  const provider = new ExternalE2EEKeyProvider();
  encryptionWorker = new EncryptionWorker();
  mediaRoom = new Room({
    adaptiveStream: true,
    dynacast: true,
    e2ee: { keyProvider: provider, worker: encryptionWorker },
  });
  mediaRoom.on(MediaEvent.TrackSubscribed, (track) => {
    remoteTracks.push(track);
    if (track.kind === "video") {
      receivedVideo = track.attach();
      receivedVideo.muted = true;
      receivedVideo.autoplay = true;
      document.getElementById("media").append(receivedVideo);
      receivedVideo.play().catch(() => {});
    }
    if (track.kind === "audio") {
      const playback = track.attach();
      playback.autoplay = true;
      playback.muted = true;
      document.getElementById("media").append(playback);
      playback.play().catch(() => {});
      audioContext ??= new AudioContext();
      const source = audioContext.createMediaStreamSource(new MediaStream([track.mediaStreamTrack]));
      analyser = audioContext.createAnalyser();
      source.connect(analyser);
      const silentOutput = audioContext.createGain();
      silentOutput.gain.value = 0;
      analyser.connect(silentOutput);
      silentOutput.connect(audioContext.destination);
      audioContext.resume().catch(() => {});
    }
  });
  await provider.setKey(wrongKey ? "deliberately-unrelated-fixture-key" : mediaKey);
  await mediaRoom.setE2EEEnabled(true);
  await mediaRoom.connect(url, token, { autoSubscribe: true });
  return { connected: mediaRoom.state === "connected", encrypted: mediaRoom.isE2EEEnabled };
}

async function publishMedia() {
  const canvas = document.createElement("canvas");
  canvas.width = 320;
  canvas.height = 180;
  const context = canvas.getContext("2d");
  let tick = 0;
  const paint = () => {
    context.fillStyle = tick++ % 2 ? "#208050" : "#30a060";
    context.fillRect(0, 0, 320, 180);
  };
  paint();
  paintTimer = setInterval(paint, 100);
  videoTrack = new LocalVideoTrack(canvas.captureStream(10).getVideoTracks()[0]);
  audioContext ??= new AudioContext();
  oscillator = audioContext.createOscillator();
  oscillator.frequency.value = 440;
  const destination = audioContext.createMediaStreamDestination();
  oscillator.connect(destination);
  oscillator.start();
  await audioContext.resume();
  audioTrack = new LocalAudioTrack(destination.stream.getAudioTracks()[0]);
  await mediaRoom.localParticipant.publishTrack(videoTrack, { simulcast: false, videoCodec: "vp8" });
  await mediaRoom.localParticipant.publishTrack(audioTrack);
  return { published: mediaRoom.localParticipant.trackPublications.size };
}

function decodedMedia() {
  let peak = 0;
  if (analyser) {
    const samples = new Float32Array(analyser.fftSize);
    analyser.getFloatTimeDomainData(samples);
    for (const sample of samples) peak = Math.max(peak, Math.abs(sample));
  }
  return {
    videoWidth: receivedVideo?.videoWidth ?? 0,
    videoHeight: receivedVideo?.videoHeight ?? 0,
    audioPeak: peak,
    connected: mediaRoom?.state === "connected",
    encrypted: mediaRoom?.isE2EEEnabled === true,
    audioContextState: audioContext?.state ?? "absent",
  };
}

async function mediaStats() {
  let bytesReceived = 0;
  let framesDecoded = 0;
  let audioEnergy = 0;
  for (const track of remoteTracks) {
    const stats = await track.getRTCStatsReport();
    stats?.forEach(entry => {
      if (entry.type === "inbound-rtp") {
        bytesReceived += entry.bytesReceived ?? 0;
        framesDecoded += entry.framesDecoded ?? 0;
        audioEnergy += entry.totalAudioEnergy ?? 0;
      }
    });
  }
  return { bytesReceived, framesDecoded, audioEnergy, tracks: remoteTracks.map(track => ({
    kind: track.kind, enabled: track.mediaStreamTrack.enabled, muted: track.mediaStreamTrack.muted,
  })) };
}

async function stopMedia() {
  clearInterval(paintTimer);
  paintTimer = undefined;
  oscillator?.stop();
  oscillator = undefined;
  videoTrack?.stop();
  audioTrack?.stop();
  videoTrack = undefined;
  audioTrack = undefined;
  if (mediaRoom) await mediaRoom.disconnect();
  mediaRoom = undefined;
  encryptionWorker?.terminate();
  encryptionWorker = undefined;
  if (audioContext) await audioContext.close();
  audioContext = undefined;
  analyser = undefined;
  receivedVideo = undefined;
  remoteTracks.length = 0;
  document.getElementById("media").replaceChildren();
}

window.probe = {
  startMatrix,
  async ownKeys() { return client.getCrypto().getOwnDeviceKeys(); },
  async trust({ userId, deviceId, signingKey }) {
    const users = await client.getCrypto().getUserDeviceInfo([userId], true);
    const device = users.get(userId)?.get(deviceId);
    if (!device) throw new Error("Peer device is not published yet");
    if (device.getFingerprint() !== signingKey) throw new Error("Device fingerprint mismatch");
    await client.getCrypto().setDeviceVerified(userId, deviceId, true);
  },
  async createRoom(invitee) {
    const result = await client.createRoom({
      preset: "private_chat",
      invite: [invitee],
      initial_state: [
        { type: "m.room.encryption", state_key: "", content: { algorithm: "m.megolm.v1.aes-sha2" } },
        { type: "m.room.history_visibility", state_key: "", content: { history_visibility: "joined" } },
      ],
    });
    return result.room_id;
  },
  async join(roomId) { await client.joinRoom(roomId); return true; },
  async send({ roomId, body, logicalId }) {
    return (await client.sendEvent(roomId, "m.room.message", { msgtype: "m.text", body,
      ...(logicalId ? { "org.hris.message_id": logicalId } : {}) })).event_id;
  },
  lookup,
  async shareMediaKey(roomId) {
    mediaKey = Array.from(crypto.getRandomValues(new Uint8Array(32)), b => b.toString(16).padStart(2, "0")).join("");
    const result = await client.sendEvent(roomId, "dev.hris.proof.media_key", { key: mediaKey, purpose: "isolated-e2ee-proof" });
    return result.event_id;
  },
  async receiveMediaKey({ roomId, eventId }) {
    const event = client.getRoom(roomId)?.findEventById(eventId);
    if (!event) return false;
    try { await client.decryptEventIfNeeded(event); } catch { return false; }
    if (event.getType() !== "dev.hris.proof.media_key" || event.getWireType() !== "m.room.encrypted") return false;
    mediaKey = event.getContent().key;
    return typeof mediaKey === "string" && mediaKey.length === 64;
  },
  startMedia,
  publishMedia,
  decodedMedia,
  mediaStats,
  async stop() { await stopMedia(); await stopMatrix(); mediaKey = undefined; },
};
