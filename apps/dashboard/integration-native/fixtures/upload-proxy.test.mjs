import assert from "node:assert/strict";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { createServer, request } from "node:http";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { uploadProxy } from "./upload-proxy.ts";

function save(body = Buffer.alloc(1_048_576, 0x20), upload = false) {
  return new Promise((resolve, reject) => {
    const call = request(
      {
        host: "127.0.0.1",
        port: 18082,
        path: upload
          ? "/api/v1/companies/10000000-0000-4000-8000-000000000001/documents/uploads"
          : "/api/v1/companies/10000000-0000-4000-8000-000000000001/documents/revisions/20000000-0000-4000-8000-000000000001/chunks",
        method: "POST",
        agent: false,
        headers: {
          "Upload-Offset": "0",
          "Idempotency-Key": "10000000-0000-4000-8000-000000000001",
        },
        timeout: 2000,
      },
      (response) => {
        response.on("data", () => {});
        response.on("error", reject);
        response.on("end", () => resolve(response.statusCode));
      },
    );
    call.on("timeout", () => call.destroy(new Error("Owned fixture timeout")));
    call.on("error", reject);
    call.end(body);
  });
}
async function stop(server) {
  server.closeAllConnections();
  await new Promise((resolve) => server.close(resolve));
}

test("committed mutation loss, private frozen replay, concurrent admission and stream cleanup", async () => {
  const directory = await mkdtemp(join(tmpdir(), "hris-native-upload-"));
  const prefix = join(directory, "control");
  let writes = 0;
  let hold = false;
  let started;
  let released;
  const upstream = createServer((incoming, outgoing) => {
    incoming.on("error", () => {});
    incoming.resume();
    incoming.on("end", () => {
      writes++;
      if (hold) {
        outgoing.writeHead(200, { "Content-Length": "32" });
        outgoing.once("close", () => released?.());
        outgoing.write(Buffer.alloc(16));
        started?.();
      } else outgoing.end("{}");
    });
  });
  let proxy;
  try {
    await new Promise((resolve, reject) => {
      upstream.once("error", reject);
      upstream.listen(18081, "127.0.0.1", resolve);
    });
    proxy = await uploadProxy(prefix);
    await assert.rejects(save());
    assert.equal(writes, 1);
    assert.equal(proxy.snapshot().interrupted, true);
    assert.equal(await save(), 503);
    assert.equal(writes, 1);
    await writeFile(`${prefix}-recover`, "", { mode: 0o600 });
    assert.equal(await save(), 200);
    assert.deepEqual(proxy.snapshot().mutations[0], proxy.snapshot().mutations[1]);
    assert.equal(proxy.snapshot().mutations[0].bytes, 1_048_576);
    assert.equal(proxy.snapshot().mutations[0].offset, "0");
    for (let attempt = 4; attempt <= 30; attempt++) assert.equal(await save(), 200);
    const competing = await Promise.all(Array.from({ length: 4 }, () => save()));
    assert.equal(competing.filter((status) => status === 200).length, 2);
    assert.equal(competing.filter((status) => status === 503).length, 2);
    assert.equal(writes, 31);
    assert.equal(proxy.snapshot().mutations.length, 31);
    await proxy.close();
    proxy = await uploadProxy(join(directory, "bounds"));
    await assert.rejects(save(Buffer.alloc(1_048_577)));
    await assert.rejects(save(Buffer.alloc(8193), true));
    assert.equal(writes, 31);
    await proxy.close();
    proxy = await uploadProxy(join(directory, "cleanup"));
    hold = true;
    const entered = new Promise((resolve) => {
      started = resolve;
    });
    const closed = new Promise((resolve) => {
      released = resolve;
    });
    const pending = assert.rejects(save());
    await entered;
    await proxy.close();
    proxy = undefined;
    await pending;
    await closed;
  } finally {
    await proxy?.close();
    await stop(upstream);
    await rm(directory, { recursive: true, force: true });
  }
});
