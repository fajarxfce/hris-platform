import { execFileSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import { signInAndEnrollAuthenticator } from "../integration/fixtures/sign-in-and-enroll-authenticator";
import { reportExportDto } from "../src/features/reporting/data/models/report-export-dto";
import { acceptMultipartReportExport } from "./fixtures/report-export-detail-workflow";

test("real private CSV execution recovers admission, retains captured facts and enforces owner/source access", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-report-export-admin@example.invalid",
    "EXPORT_REAL",
    "Export Acceptance",
  );
  const member = "a0000000-0000-4000-8000-000000000091",
    base = `/api/v1/companies/${company}`;
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  async function employee(number: string) {
    const response = await context.request.post(`${base}/employees`, {
      headers: headers(),
      data: {
        id: randomUUID(),
        employeeNumber: number,
        person: { id: randomUUID(), legalName: `Export ${number}`, nationality: "ID" },
        terms: {
          effectiveFrom: "2025-01-01",
          startDate: "2025-01-01",
          status: "ACTIVE",
          contract: "PERMANENT",
        },
        reason: "Owned report export acceptance",
      },
    });
    expect(response.status()).toBe(200);
  }
  await employee("EXPORT-001");
  const assigned = await context.request.put(`${base}/members/${member}`, {
    headers: headers(),
    data: {
      active: true,
      permissions: ["company.read", "reports.read", "people.read"],
      reason: "Independent report export owner",
    },
  });
  expect(assigned.status()).toBe(200);
  const membership = await assigned.json();
  if (!baseURL) throw new Error("Browser fixture URL required");
  const readerContext = await browser.newContext({ baseURL });
  try {
    const reader = await readerContext.newPage();
    await signInAndEnrollAuthenticator(reader, "browser-report-export-reader@example.invalid");
    await reader.goto(`/reports/headcount?company=${company}&companies=${company}&asOf=2026-10-01`);
    await expect(
      reader.getByRole("region", { name: "Employment records", exact: true }),
    ).toContainText("1");
    await reader.getByRole("link", { name: "Export applied filters", exact: true }).click();
    const commands: { operation: string; body: unknown; id: string }[] = [];
    let drop = true;
    await reader.route(`**/companies/${company}/reports/exports`, async (route) => {
      if (route.request().method() !== "POST") return route.continue();
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      const receipt = await response.json();
      commands.push({
        operation: route.request().headers()["idempotency-key"] ?? "",
        body: route.request().postDataJSON(),
        id: receipt.id,
      });
      if (drop) {
        drop = false;
        return route.abort("failed");
      }
      return route.fulfill({ response });
    });
    await reader.getByRole("button", { name: "Create CSV export", exact: true }).click();
    await reader.getByRole("button", { name: "Retry original command", exact: true }).click();
    await reader.getByRole("link", { name: "View export", exact: true }).click();
    expect(commands).toHaveLength(2);
    expect(commands[1]).toEqual(commands[0]);
    const id = commands[0]?.id;
    if (!id || !/^[0-9a-f-]{36}$/u.test(id)) throw new Error("Missing export receipt");
    expect(id).not.toBe(commands[0]?.operation);
    const resource = `${base}/reports/exports/${id}`;
    await expect
      .poll(
        async () => {
          const response = await readerContext.request.get(resource);
          expect(response.status()).toBe(200);
          const value = reportExportDto.parse(await response.json());
          if (["FAILED", "CANCELLED"].includes(value.jobStatus))
            throw new Error(`Export execution stopped: ${value.failureCode}`);
          return value.status;
        },
        { timeout: 60000, intervals: [250, 500, 1000] },
      )
      .toBe("READY");
    const status = reportExportDto.parse(await (await readerContext.request.get(resource)).json());
    expect(status).toMatchObject({ completedRows: 2, attempt: 1, jobStatus: "SUCCEEDED" });
    expect(status.bytes).toBeGreaterThan(100);
    await reader.getByRole("button", { name: "Refresh", exact: true }).click();
    const event = reader.waitForEvent("download");
    await reader.getByRole("button", { name: "Download CSV", exact: true }).click();
    const download = await event,
      content = await readFile(await download.path());
    expect(download.suggestedFilename()).toBe(`report-headcount-${id}.csv`);
    expect(content.length).toBe(status.bytes);
    expect(content.subarray(0, 3)).toEqual(Buffer.from([239, 187, 191]));
    const lines = content.toString("utf8").slice(1).trimEnd().split("\r\n");
    expect(lines).toHaveLength(3);
    expect(lines[0]).toContain('"definition_version","evaluated_at","as_of","row_type"');
    expect(lines[1]).toContain('"TOTAL"');
    expect(lines[2]).toContain(company);
    expect(lines[1]).toMatch(/,1,1,1,0,0,1,0$/u);
    const full = await readerContext.request.get(`${resource}/content`);
    expect(full.status()).toBe(200);
    expect(await full.body()).toEqual(content);
    const etag = full.headers().etag;
    if (!etag) throw new Error("Missing private content tag");
    expect(
      (
        await readerContext.request.get(`${resource}/content`, {
          headers: { "If-None-Match": etag },
        })
      ).status(),
    ).toBe(304);
    const range = await readerContext.request.get(`${resource}/content`, {
      headers: { Range: "bytes=5-30", "If-Range": etag },
    });
    expect(range.status()).toBe(206);
    expect(await range.body()).toEqual(content.subarray(5, 31));
    const head = await readerContext.request.head(`${resource}/content`);
    expect(head.status()).toBe(200);
    expect(head.headers()["content-length"]).toBe(String(content.length));
    await employee("EXPORT-002");
    const live = await readerContext.request.get(
      `/api/v1/reports/headcount?companies=${company}&asOf=2026-10-01`,
    );
    expect((await live.json()).totals.employments).toBe(2);
    const retained = await readerContext.request.get(`${resource}/content`);
    expect(
      createHash("sha256")
        .update(await retained.body())
        .digest("hex"),
    ).toBe(createHash("sha256").update(content).digest("hex"));
    expect(retained.headers().etag).toBe(etag);
    const owned = await readerContext.request.get(`${base}/reports/exports`);
    expect((await owned.json()).items).toHaveLength(1);
    expect((await (await context.request.get(`${base}/reports/exports`)).json()).items).toEqual([]);
    expect((await context.request.get(resource)).status()).toBe(404);
    expect((await context.request.get(`${resource}/content`)).status()).toBe(404);
    const database = JSON.parse(
      await readFile("../../.work/worker-integration/database.json", "utf8"),
    ) as { container: string; database: string };
    if (!/^[0-9a-f]{12,64}$/u.test(database.container) || database.database !== "hris_browser")
      throw new Error("Unexpected owned fixture database");
    const sql = (query: string) =>
      execFileSync(
        "docker",
        [
          "exec",
          "-i",
          database.container,
          "psql",
          "-U",
          "fixture_migrator",
          "-d",
          database.database,
          "-v",
          "ON_ERROR_STOP=1",
          "-Atqc",
          query,
        ],
        { encoding: "utf8", timeout: 10000, maxBuffer: 100000 },
      ).trim();
    const part = JSON.parse(
      sql(
        `SELECT json_build_object('upload',p.upload_id,'sha',u.sha256,'bytes',u.byte_count) FROM report_export_parts p JOIN report_export_uploads u ON (u.company_id,u.account_id,u.export_id,u.id)=(p.company_id,p.account_id,p.export_id,p.upload_id) WHERE p.company_id='${company}' AND p.export_id='${id}'`,
      ),
    ) as { upload: string; sha: string; bytes: number };
    expect(part.sha).toBe(createHash("sha256").update(content).digest("hex"));
    expect(part.bytes).toBe(content.length);
    const storage = JSON.parse(
      await readFile("../../.work/worker-integration/storage.json", "utf8"),
    ) as { endpoint: string; bucket: string };
    if (
      !/^http:\/\/127\.0\.0\.1:\d+$/u.test(storage.endpoint) ||
      storage.bucket !== "hris-browser-exports" ||
      !/^[0-9a-f-]{36}$/u.test(part.upload)
    )
      throw new Error("Unexpected storage fixture");
    const anonymous = await context.request.get(
      `${storage.endpoint}/${storage.bucket}/${company}/reports/${id}/${part.upload}`,
    );
    expect([400, 403]).toContain(anonymous.status());
    expect(await anonymous.text()).not.toContain("headcount.v1");
    const revoked = await context.request.put(`${base}/members/${member}`, {
      headers: headers(),
      data: {
        active: true,
        permissions: ["company.read", "reports.read"],
        expectedVersion: membership.version,
        reason: "Revoke export source while retaining owner history",
      },
    });
    expect(revoked.status()).toBe(200);
    expect((await readerContext.request.get(resource)).status()).toBe(200);
    expect(
      (
        await readerContext.request.get(`${resource}/content`, {
          headers: { "If-None-Match": etag },
        })
      ).status(),
    ).toBe(403);
    expect((await readerContext.request.head(`${resource}/content`)).status()).toBe(403);
    await reader.goto(`/reports/exports/${id}?company=${company}`);
    await expect(reader.getByRole("region", { name: "Status", exact: true })).toContainText(
      "Ready",
    );
    await expect(reader.getByRole("button", { name: "Download CSV", exact: true })).toHaveCount(0);
    await reader.screenshot({ path: "../../.work/report-export-real-private.png", fullPage: true });
    await acceptMultipartReportExport(
      context,
      reader,
      company,
      member,
      (await revoked.json()).version,
      headers,
      sql,
    );
  } finally {
    await readerContext.close();
  }
});
