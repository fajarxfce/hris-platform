import { createHash } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { type BrowserContext, expect, type Page } from "@playwright/test";
import { createLeaveScenario } from "../../integration/fixtures/leave-scenario";
import { reportExportDto } from "../../src/features/reporting/data/models/report-export-dto";
import { recoverStorageInterruptedExport, setStorageFault } from "./report-export-storage-recovery";

/** One API-created source request plus bounded fictional cancelled history exercise real capture SQL. */
export async function acceptMultipartReportExport(
  context: BrowserContext,
  reader: Page,
  company: string,
  member: string,
  membershipVersion: number,
  headers: () => Record<string, string>,
  sql: (query: string) => string,
) {
  const base = `/api/v1/companies/${company}`;
  const grant = await context.request.put(`${base}/members/${member}`, {
    headers: headers(),
    data: {
      active: true,
      permissions: ["company.read", "reports.read", "people.read", "leave.read", "leave.approve"],
      expectedVersion: membershipVersion,
      reason: "Authorize multipart leave export acceptance",
    },
  });
  expect(grant.status()).toBe(200);
  const source = await createLeaveScenario(context, company, member);
  for (const value of [company, source.request])
    if (!/^[0-9a-f-]{36}$/u.test(value)) throw new Error("Unexpected owned source identity");
  // This is historical fixture provisioning, not a claim that 1,200 leave commands were executed.
  // Each cancelled request gets its own matching cancelled approval; no active allocation is made.
  sql(`BEGIN;
    CREATE TEMP TABLE export_source_ids ON COMMIT DROP AS
      SELECT n, md5('export-request-' || n || '-${company}')::uuid AS request_id,
      md5('export-approval-' || n || '-${company}')::uuid AS approval_id FROM generate_series(1,1200) n;
    INSERT INTO approval_requests(company_id,id,kind,resource_id,author_id,requester_id,template_id,template_revision,stages,current_step,status,version,submitted_at)
      SELECT a.company_id,g.approval_id,a.kind,g.request_id,a.author_id,a.requester_id,a.template_id,a.template_revision,a.stages,a.current_step,'CANCELLED',1,a.submitted_at
      FROM leave_requests r JOIN approval_requests a ON (a.company_id,a.id)=(r.company_id,r.approval_id) CROSS JOIN export_source_ids g
      WHERE r.company_id='${company}' AND r.id='${source.request}';
    INSERT INTO leave_requests(company_id,id,employment_id,employee_number,employee_name,owner_account_id,author_id,submitted_at,type_id,type_revision,type_snapshot,days,starts_on,ends_on,half_days,reason,status,approval_id,version)
      SELECT r.company_id,g.request_id,r.employment_id,r.employee_number,'Archived "quoted", employee ' || g.n,r.owner_account_id,r.author_id,r.submitted_at,r.type_id,r.type_revision,r.type_snapshot,r.days,r.starts_on,r.ends_on,r.half_days,'Fictional cancelled history','CANCELLED',g.approval_id,1
      FROM leave_requests r CROSS JOIN export_source_ids g WHERE r.company_id='${company}' AND r.id='${source.request}';
    INSERT INTO leave_request_changes(company_id,request_id,revision,kind,status,actor_id,recorded_at,reason)
      SELECT r.company_id,r.id,0,'SUBMITTED','PENDING',r.author_id,r.submitted_at,'Fictional submitted history'
      FROM leave_requests r JOIN export_source_ids g ON g.request_id=r.id WHERE r.company_id='${company}';
    INSERT INTO leave_request_changes(company_id,request_id,revision,kind,status,actor_id,recorded_at,reason)
      SELECT r.company_id,r.id,1,'WITHDRAWN','CANCELLED',r.author_id,r.submitted_at,'Fictional withdrawn history'
      FROM leave_requests r JOIN export_source_ids g ON g.request_id=r.id WHERE r.company_id='${company}';
    COMMIT;`);
  const query = new URLSearchParams({ company, from: source.date, until: source.date });
  await reader.goto(`/reports/leave?${query}`);
  const live = await reader.request.get(
    `${base}/reports/leave?from=${source.date}&until=${source.date}`,
  );
  expect(live.status()).toBe(200);
  expect((await live.json()).totals).toEqual({ requests: 1201, employments: 1, days: "600.5" });
  await reader.getByRole("link", { name: "Export applied filters", exact: true }).click();
  await reader.getByLabel("Contents", { exact: true }).selectOption("DETAIL");
  await setStorageFault("drop-second-put");
  await reader.getByRole("button", { name: "Create CSV export", exact: true }).click();
  const link = reader.getByRole("link", { name: "View export", exact: true });
  await expect(link).toBeVisible();
  const target = await link.getAttribute("href");
  const id = target?.match(/\/exports\/([a-f0-9-]{36})\?/u)?.[1];
  if (!id) throw new Error("Missing detail export");
  await link.click();
  const resource = `${base}/reports/exports/${id}`;
  const recovery = await recoverStorageInterruptedExport(reader, resource, company, id, sql);
  await expect
    .poll(
      async () => {
        const response = await reader.request.get(resource);
        expect(response.status()).toBe(200);
        const result = reportExportDto.parse(await response.json());
        if (result.jobStatus === "FAILED")
          throw new Error(`Multipart export failed: ${result.failureCode}`);
        return result.status;
      },
      { timeout: 60000, intervals: [250, 500, 1000] },
    )
    .toBe("READY");
  const status = reportExportDto.parse(await (await reader.request.get(resource)).json());
  expect(status.completedRows).toBe(1201);
  await reader.getByRole("button", { name: "Refresh", exact: true }).click();
  const event = reader.waitForEvent("download");
  await reader.getByRole("button", { name: "Download CSV", exact: true }).click();
  const download = await event,
    content = await readFile(await download.path());
  expect(content.length).toBe(status.bytes);
  const lines = content.toString("utf8").slice(1).trimEnd().split("\r\n");
  expect(lines).toHaveLength(1202);
  expect(lines.filter((line) => line.includes('"Archived ""quoted"", employee '))).toHaveLength(
    1200,
  );
  const ids = lines
    .slice(1)
    .map(
      (line) =>
        [
          ...line.matchAll(/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/gu),
        ][1]?.[0],
    );
  expect(ids.every(Boolean)).toBe(true);
  expect(new Set(ids).size).toBe(1201);
  expect(ids).toContain(source.request);
  const parts = JSON.parse(
    sql(
      `SELECT json_agg(json_build_object('upload',p.upload_id,'offset',u.byte_offset,'bytes',u.byte_count,'rows',u.row_count,'sha',u.sha256) ORDER BY p.part_number) FROM report_export_parts p JOIN report_export_uploads u ON (u.company_id,u.account_id,u.export_id,u.id)=(p.company_id,p.account_id,p.export_id,p.upload_id) WHERE p.company_id='${company}' AND p.export_id='${id}'`,
    ),
  ) as { upload: string; offset: number; bytes: number; rows: number; sha: string }[];
  expect(parts).toHaveLength(3);
  expect(parts[0]?.upload).toBe(recovery.firstPart);
  expect(parts.map((part) => part.rows)).toEqual([500, 500, 201]);
  for (const part of parts)
    expect(
      createHash("sha256")
        .update(content.subarray(part.offset, part.offset + part.bytes))
        .digest("hex"),
    ).toBe(part.sha);
  const boundary = parts[1]?.offset;
  if (!boundary) throw new Error("Missing second part");
  const cross = await reader.request.get(`${resource}/content`, {
    headers: { Range: `bytes=${boundary - 3}-${boundary + 7}` },
  });
  expect(cross.status()).toBe(206);
  expect(await cross.body()).toEqual(content.subarray(boundary - 3, boundary + 8));
  const withdrawal = await context.request.post(
    `${base}/leave/requests/${source.request}/withdraw`,
    {
      headers: headers(),
      data: { version: 0, reason: "Change source after retained export" },
    },
  );
  expect(withdrawal.status()).toBe(200);
  const retained = await reader.request.get(`${resource}/content`);
  expect(retained.status()).toBe(200);
  expect(await retained.body()).toEqual(content);
  await expect(
    reader.getByRole("status").filter({ hasText: "The file has been sent to your browser." }),
  ).toBeVisible();
  await reader.screenshot({ path: "../../.work/report-export-real-multipart.png", fullPage: true });
  await writeFile(
    "../../.work/report-export-storage-recovery.json",
    JSON.stringify({
      droppedProviderResponses: recovery.droppedResponse,
      resumedAttempt: recovery.resumedAttempt,
      retainedFirstPart: true,
      rows: ids.length,
      uniqueRows: new Set(ids).size,
      parts: parts.map(({ rows, bytes, sha }) => ({ rows, bytes, sha })),
      bytes: content.length,
      originalExpiryRetained: true,
      resumeReceiptReplayed: true,
    }),
    { mode: 0o600 },
  );
}
