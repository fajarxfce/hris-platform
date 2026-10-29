import { createHash } from "node:crypto";
import { existsSync, renameSync, writeFileSync } from "node:fs";
import { createServer, request as forward } from "node:http";

export function privateJson(path: string, value: unknown) {
  writeFileSync(`${path}.tmp`, JSON.stringify(value), { mode: 0o600 });
  renameSync(`${path}.tmp`, path);
}

/** Fixed loopback upstream, bounded metadata, no credential/body logging or public controls. */
export async function downloadProxy(prefix: string) {
  let count = 0;
  let contentCalls = 0;
  let interrupted = false;
  const ranges: { range: string; status: number; bytes: number; sha256: string }[] = [];
  const events: { method: string; path: string; forwarded: boolean }[] = [];
  const publish = () => privateJson(`${prefix}-http.json`, { count, interrupted, ranges, events });
  const proxy = createServer({ maxHeaderSize: 16_384 }, (incoming, outgoing) => {
    const path = new URL(incoming.url ?? "/", "http://127.0.0.1").pathname;
    const content =
      incoming.method === "GET" && /\/documents\/revisions\/[0-9a-f-]{36}\/content$/u.test(path);
    const range = typeof incoming.headers.range === "string" ? incoming.headers.range : "";
    if (content) contentCalls++;
    const second = content && /^bytes=1048576-/u.test(range);
    if (!interrupted && second) interrupted = true;
    const blocked =
      (interrupted && !existsSync(`${prefix}-recover`)) || (content && contentCalls > 16);
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
    incoming.on("data", (chunk: Buffer) => {
      requestBytes += chunk.length;
      if (requestBytes > 8192) incoming.destroy();
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
        const digest = createHash("sha256");
        let bytes = 0;
        response.on("data", (chunk: Buffer) => {
          if (content) {
            bytes += chunk.length;
            if (bytes > 1_048_576) response.destroy();
            else digest.update(chunk);
          }
        });
        response.on("end", () => {
          if (content) {
            ranges.push({
              range,
              status: response.statusCode ?? 502,
              bytes,
              sha256: digest.digest("hex"),
            });
            publish();
          }
        });
        outgoing.writeHead(response.statusCode ?? 502, response.headers);
        response.pipe(outgoing);
      },
    );
    upstream.on("timeout", () => upstream.destroy());
    upstream.on("error", () => {
      if (!outgoing.headersSent) outgoing.writeHead(502);
      outgoing.end();
    });
    incoming.on("aborted", () => upstream.destroy());
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
    snapshot: () => ({ count, interrupted, ranges: [...ranges], events: [...events] }),
    close: async () => {
      proxy.closeAllConnections();
      await new Promise<void>((resolve) => proxy.close(() => resolve()));
    },
  };
}
