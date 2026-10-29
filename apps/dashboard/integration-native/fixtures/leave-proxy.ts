import { createHash } from "node:crypto";
import { existsSync, renameSync, writeFileSync } from "node:fs";
import { createServer, request as forward } from "node:http";

export function leaveFixtureJson(path: string, value: unknown) {
  writeFileSync(`${path}.tmp`, JSON.stringify(value), { mode: 0o600 });
  renameSync(`${path}.tmp`, path);
}

/** Owns fixed loopback forwarding and one dropped committed leave plan response. */
export async function leaveProxy(prefix: string) {
  let count = 0;
  let admissions = 0;
  let interrupted = false;
  const mutations: { method: string; operation: string; body: string; status: number }[] = [];
  const events: { method: string; path: string; forwarded: boolean }[] = [];
  const snapshot = () => ({ count, interrupted, mutations: [...mutations], events: [...events] });
  const publish = () => leaveFixtureJson(`${prefix}-http.json`, snapshot());
  const proxy = createServer({ maxHeaderSize: 16_384 }, (incoming, outgoing) => {
    const path = new URL(incoming.url ?? "/", "http://127.0.0.1").pathname;
    const mutation =
      incoming.method === "POST" &&
      /^\/api\/v1\/companies\/[a-f0-9-]{36}\/leave\/requests$/u.test(path);
    if (mutation) admissions++;
    const blocked =
      (interrupted && !existsSync(`${prefix}-recover`)) || (mutation && admissions > 16);
    count++;
    events.push({
      method: incoming.method ?? "",
      path: path.replace(/[a-f0-9]{8}-[a-f0-9-]{27}/gu, ":id"),
      forwarded: !blocked,
    });
    if (events.length > 512) events.shift();
    publish();
    if (blocked) {
      outgoing.writeHead(503, { "Content-Length": "0", Connection: "close" });
      outgoing.end();
      return;
    }
    let requestBytes = 0;
    const hash = createHash("sha256");
    let digest: string | null = null;
    incoming.on("data", (chunk: Buffer) => {
      requestBytes += chunk.length;
      if (requestBytes > 8192) incoming.destroy();
      else if (mutation) hash.update(chunk);
    });
    incoming.on("end", () => {
      if (mutation) digest = hash.digest("hex");
    });
    const upstream = forward(
      {
        host: "127.0.0.1",
        port: 18081,
        path: incoming.url,
        method: incoming.method,
        headers: { ...incoming.headers, host: "127.0.0.1:18081" },
        timeout: 15_000,
      },
      (response) => {
        response.on("error", () => outgoing.destroy());
        if (!mutation) {
          outgoing.writeHead(response.statusCode ?? 502, response.headers);
          response.pipe(outgoing);
          return;
        }
        const parts: Buffer[] = [];
        let bytes = 0;
        response.on("data", (chunk: Buffer) => {
          bytes += chunk.length;
          if (bytes > 8192) response.destroy();
          else parts.push(chunk);
        });
        response.on("end", () => {
          const operation = incoming.headers["idempotency-key"];
          if (
            digest === null ||
            typeof operation !== "string" ||
            !/^[a-f0-9-]{36}$/u.test(operation)
          ) {
            outgoing.destroy();
            return;
          }
          mutations.push({
            method: incoming.method ?? "",
            operation: createHash("sha256").update(operation).digest("hex"),
            body: digest,
            status: response.statusCode ?? 502,
          });
          if (!interrupted && response.statusCode === 200) {
            interrupted = true;
            publish();
            outgoing.destroy();
            return;
          }
          publish();
          outgoing.writeHead(response.statusCode ?? 502, response.headers);
          outgoing.end(Buffer.concat(parts));
        });
      },
    );
    upstream.on("timeout", () => upstream.destroy());
    upstream.on("error", () => {
      if (!outgoing.headersSent) outgoing.writeHead(502);
      outgoing.end();
    });
    incoming.on("aborted", () => upstream.destroy());
    incoming.on("error", () => upstream.destroy());
    outgoing.on("close", () => upstream.destroy());
    incoming.pipe(upstream);
  });
  proxy.requestTimeout = 20_000;
  proxy.headersTimeout = 10_000;
  proxy.maxConnections = 8;
  await new Promise<void>((resolve, reject) => {
    proxy.once("error", reject);
    proxy.listen(18082, "127.0.0.1", resolve);
  });
  return {
    snapshot,
    close: async () => {
      proxy.closeAllConnections();
      await new Promise<void>((resolve) => proxy.close(() => resolve()));
    },
  };
}
