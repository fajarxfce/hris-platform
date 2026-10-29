import assert from "node:assert/strict";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { createServer, request } from "node:http";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { downloadProxy } from "./download-proxy.ts";

const path =
  "/api/v1/companies/10000000-0000-4000-8000-000000000001/documents/revisions/20000000-0000-4000-8000-000000000001/content";
const range = "bytes=0-1048575";
function read(selected = range) {
  return new Promise((resolve, reject) => {
    const call = request(
      {
        host: "127.0.0.1",
        port: 18082,
        path,
        agent: false,
        headers: { Range: selected },
        timeout: 2_000,
      },
      (response) => {
        let bytes = 0;
        response.on("data", (chunk) => {
          bytes += chunk.length;
          if (bytes > 1_048_576) response.destroy();
        });
        response.on("error", reject);
        response.on("end", () => resolve({ status: response.statusCode, bytes }));
      },
    );
    call.on("timeout", () => call.destroy(new Error("Owned fixture timeout")));
    call.on("error", reject);
    call.end();
  });
}
async function stop(server) {
  server.closeAllConnections();
  await new Promise((resolve) => server.close(resolve));
}

test("real loopback interruption, admission capacity and active-stream cleanup", async () => {
  const directory = await mkdtemp(join(tmpdir(), "hris-native-download-"));
  const prefix = join(directory, "control");
  let upstreamReads = 0;
  let hold = false;
  let started;
  let released;
  const upstream = createServer((_incoming, outgoing) => {
    upstreamReads++;
    outgoing.writeHead(206, { "Content-Length": "1048576" });
    if (hold) {
      outgoing.once("close", () => released?.());
      outgoing.write(Buffer.alloc(16));
      started?.();
    } else outgoing.end(Buffer.alloc(1_048_576));
  });
  let proxy;
  try {
    await new Promise((resolve, reject) => {
      upstream.once("error", reject);
      upstream.listen(18081, "127.0.0.1", resolve);
    });
    proxy = await downloadProxy(prefix);
    assert.deepEqual(await read(), { status: 206, bytes: 1_048_576 });
    assert.deepEqual(await read("bytes=1048576-2097151"), { status: 503, bytes: 0 });
    assert.equal(upstreamReads, 1);
    await writeFile(`${prefix}-recover`, "", { mode: 0o600 });
    assert.deepEqual(await read("bytes=1048576-2097151"), { status: 206, bytes: 1_048_576 });
    for (let attempt = 4; attempt <= 14; attempt++) assert.equal((await read()).status, 206);
    const competing = await Promise.all(Array.from({ length: 4 }, () => read()));
    assert.equal(competing.filter((response) => response.status === 206).length, 2);
    assert.equal(competing.filter((response) => response.status === 503).length, 2);
    assert.equal(upstreamReads, 15);
    assert.equal(proxy.snapshot().ranges.length, 15);
    await proxy.close();
    proxy = await downloadProxy(join(directory, "cleanup"));
    hold = true;
    const entered = new Promise((resolve) => {
      started = resolve;
    });
    const closed = new Promise((resolve) => {
      released = resolve;
    });
    const pending = read();
    const rejected = assert.rejects(pending);
    await entered;
    await proxy.close();
    proxy = undefined;
    await rejected;
    await closed;
  } finally {
    await proxy?.close();
    await stop(upstream);
    await rm(directory, { recursive: true, force: true });
  }
});
