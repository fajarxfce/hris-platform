import { createHash, randomUUID } from "node:crypto";
import { existsSync, renameSync, unlinkSync, writeFileSync } from "node:fs";
import { createServer, request as forward } from "node:http";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import { createLeaveScenario } from "../integration/fixtures/leave-scenario";

const prefix = "../../.work/inbox-native";
function write(path: string, value: unknown) {
  writeFileSync(`${path}.tmp`, JSON.stringify(value), { mode: 0o600 });
  renameSync(`${path}.tmp`, path);
}

test("owned Android pending-command recovery fixture", async ({ page, context, request }) => {
  for (const suffix of ["ready.json", "recover", "done"])
    if (existsSync(`${prefix}-${suffix}`))
      throw new Error("Previous native fixture requires review");
  let dropped = false;
  let count = 0;
  const writes: { operation: string; body: string; status: number }[] = [];
  const events: { ordinal: number; method: string; path: string; forwarded: boolean }[] = [];
  const publish = () => write(`${prefix}-http.json`, { count, dropped, writes, events });
  const proxy = createServer((incoming, outgoing) => {
    const path = new URL(incoming.url ?? "/", "http://127.0.0.1").pathname;
    const action = incoming.method === "POST" && /\/inbox\/[0-9a-f-]{36}\/read$/u.test(path);
    const blocked = (dropped && !existsSync(`${prefix}-recover`)) || (action && writes.length >= 8);
    events.push({
      ordinal: ++count,
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
    const bodyHash = createHash("sha256");
    let bodyBytes = 0;
    if (action)
      incoming.on("data", (chunk: Buffer) => {
        bodyBytes += chunk.length;
        if (bodyBytes > 8192) incoming.destroy();
        else bodyHash.update(chunk);
      });
    const upstream = forward(
      {
        host: "127.0.0.1",
        port: 18081,
        path: incoming.url,
        method: incoming.method,
        headers: { ...incoming.headers, host: "127.0.0.1:18081" },
        timeout: 15000,
      },
      (response) => {
        response.on("error", () => outgoing.destroy());
        if (!action) {
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
          if (typeof operation !== "string" || !/^[a-f0-9-]{36}$/u.test(operation)) {
            outgoing.destroy();
            return;
          }
          writes.push({
            operation: createHash("sha256").update(operation).digest("hex"),
            body: bodyHash.digest("hex"),
            status: response.statusCode ?? 502,
          });
          if (writes.length > 8) {
            outgoing.destroy();
            return;
          }
          if (!dropped && response.statusCode === 200) {
            dropped = true;
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
  try {
    const { company, csrf } = await createAdminCompany(
      page,
      context,
      "browser-leave-creation-admin@example.invalid",
      "PENDING_COMMAND",
      "Pending Command Acceptance",
    );
    const base = `/api/v1/companies/${company}`;
    const member = "a0000000-0000-4000-8000-000000000008";
    const reviewer = "a0000000-0000-4000-8000-000000000037";
    const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
    for (const [account, permissions] of [
      [reviewer, ["leave.approve"]],
      [
        member,
        [
          "company.read",
          "people.self.read",
          "announcements.read",
          "leave.self.manage",
          "attendance.self.record",
          "overtime.self.manage",
          "expenses.self.manage",
          "documents.self.read",
        ],
      ],
    ] as const) {
      const response = await context.request.put(`${base}/members/${account}`, {
        headers: headers(),
        data: { active: true, permissions, reason: "Owned native command fixture" },
      });
      expect(response.status()).toBe(200);
    }
    const source = await createLeaveScenario(context, company, reviewer, { submit: false });
    const binding = await context.request.post(
      `${base}/employees/${source.employee}/account-link`,
      {
        headers: headers(),
        data: { accountId: member, expectedVersion: 0, reason: "Owned command acceptance" },
      },
    );
    expect(binding.status()).toBe(200);
    const announcement = `${base}/announcements/${randomUUID()}`;
    expect(
      (
        await context.request.put(announcement, {
          headers: headers(),
          data: {
            title: "Pending command acceptance",
            body: "Read exactly once after a lost response.",
            audience: { kind: "COMPANY", targetIds: [] },
            acknowledgementRequired: true,
            reason: "Owned command fixture",
          },
        })
      ).status(),
    ).toBe(200);
    expect(
      (
        await context.request.post(`${announcement}/publish`, {
          headers: headers(),
          data: { expectedVersion: 0, reason: "Publish owned command fixture" },
        })
      ).status(),
    ).toBe(200);
    const login = await request.post("/api/v1/auth/native/login", {
      headers: { "Idempotency-Key": randomUUID() },
      data: {
        email: "browser-binding-employee@example.invalid",
        password: "Browser-fixture-password-123!",
        deviceName: "Independent native command observer",
      },
    });
    expect(login.status()).toBe(200);
    const token = (await login.json()).credentials.accessToken;
    const memberHeaders = {
      Authorization: `Bearer ${token}`,
      "X-HRIS-Client-Platform": "ANDROID",
      "X-HRIS-Client-Build": "1",
    };
    let inbox = "";
    await expect
      .poll(
        async () => {
          const response = await request.get(`${base}/inbox`, { headers: memberHeaders });
          expect(response.status()).toBe(200);
          const items = (await response.json()).items;
          inbox = items[0]?.id ?? "";
          return items.length;
        },
        { timeout: 30_000 },
      )
      .toBe(1);
    write(`${prefix}-ready.json`, {
      company,
      account: member,
      inbox,
      email: "browser-binding-employee@example.invalid",
      proxyPort: 18082,
    });
    console.log("Owned Android pending-command fixture ready");
    await expect
      .poll(() => existsSync(`${prefix}-done`), { timeout: 480_000, intervals: [1000] })
      .toBe(true);
    const actual = await request.get(`${base}/inbox/${inbox}`, { headers: memberHeaders });
    expect(actual.status()).toBe(200);
    const value = await actual.json();
    expect(value).toMatchObject({ version: 1, acknowledgedAt: null });
    expect(value.readAt).not.toBeNull();
    expect(dropped).toBe(true);
    expect(writes).toHaveLength(2);
    expect(writes[0]).toEqual(writes[1]);
    expect(writes[0]?.status).toBe(200);
    const mutations = events.filter(
      (event) =>
        event.forwarded && event.method === "POST" && event.path.startsWith("/api/v1/companies/"),
    );
    expect(mutations).toHaveLength(2);
    expect(mutations.every((event) => /\/inbox\/:id\/read$/u.test(event.path))).toBe(true);
    write(`${prefix}-server-result.json`, {
      responseDropped: true,
      writes: 2,
      sameOperationAndInput: true,
      serverVersion: value.version,
      acknowledged: false,
      requests: count,
    });
  } finally {
    proxy.closeAllConnections();
    await new Promise<void>((resolve) => proxy.close(() => resolve()));
    if (existsSync(`${prefix}-ready.json`)) unlinkSync(`${prefix}-ready.json`);
  }
});
