import { execFileSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import { type BrowserContext, expect, type Page } from "@playwright/test";

/** Real publication and read path; terminal-state/retention faults are owned SQL fixtures. */
export async function acceptAnnouncementDelivery(
  page: Page,
  context: BrowserContext,
  company: string,
  announcement: string,
) {
  for (const id of [company, announcement])
    if (!/^[0-9a-f-]{36}$/u.test(id)) throw new Error("Unexpected owned fixture ID");
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
  const resource = `/api/v1/companies/${company}/announcements/${announcement}/delivery`;
  const inboxQuery = `SELECT row_to_json(i)::text FROM inbox_items i WHERE company_id='${company}' AND announcement_id='${announcement}'`;
  await expect
    .poll(
      async () => {
        const result = await context.request.get(resource);
        expect(result.status()).toBe(200);
        return (await result.json()).email?.accepted;
      },
      { timeout: 25_000, intervals: [500] },
    )
    .toBe(1);
  const original = sql(inboxQuery);
  const item = JSON.parse(original) as { id: string; owner_account_id: string };
  const captured = JSON.parse(
    await readFile("../../.work/worker-integration/mail.json", "utf8"),
  ) as { recipient: string; subject: string; text: string }[];
  const email = captured.filter(
    (message) =>
      message.subject === "New HRIS announcement" && message.text.includes(`/inbox/${item.id}`),
  );
  expect(email).toHaveLength(1);
  expect(email[0]?.recipient).toBe("browser-binding-employee@example.invalid");
  expect(email[0]?.text).toContain(
    `http://127.0.0.1:4175/app/prod/accounts/${item.owner_account_id}/companies/${company}/inbox/${item.id}`,
  );
  expect(email[0]?.text).not.toContain("Office closure");
  expect(email[0]?.text).not.toContain("Friday");
  const auditQuery = `SELECT count(*) FROM audit_entries WHERE company_id='${company}'`;
  const audits = sql(auditQuery);
  await page.getByRole("link", { name: "Notification delivery", exact: true }).click();
  const recipients = page.getByRole("region", { name: "Push recipients", exact: true });
  await expect(recipients).toContainText("Queued1");
  await expect(page.getByRole("region", { name: "Email reminders", exact: true })).toContainText(
    "Accepted by mail server1",
  );
  expect(await (await context.request.get(resource)).json()).toMatchObject({
    announcementId: announcement,
    recipientCount: 1,
    pending: 1,
    failed: 0,
    completed: 0,
    acceptedDevices: 0,
    unavailableRecipients: 0,
    email: {
      pending: 0,
      processing: 0,
      accepted: 1,
      failed: 0,
      superseded: 0,
      unavailableRecipients: 0,
    },
  });
  sql(
    `UPDATE inbox_push_dispatches SET state='FAILED',failure_code='push_delivery_expired',finished_at=clock_timestamp() WHERE company_id='${company}' AND inbox_id IN (SELECT id FROM inbox_items WHERE company_id='${company}' AND announcement_id='${announcement}')`,
  );
  await expect(recipients).toContainText("Queued1");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(recipients).toContainText("Failed1");
  sql(
    `DELETE FROM inbox_push_dispatches WHERE company_id='${company}' AND inbox_id IN (SELECT id FROM inbox_items WHERE company_id='${company}' AND announcement_id='${announcement}')`,
  );
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(recipients).toContainText("History unavailable1");
  const retained = await context.request.get(resource);
  expect(retained.status()).toBe(200);
  expect(await retained.json()).toMatchObject({
    recipientCount: 1,
    pending: 0,
    failed: 0,
    completed: 0,
    unavailableRecipients: 1,
    processedDevices: 0,
  });
  expect(sql(inboxQuery)).toBe(original);
  expect(sql(auditQuery)).toBe(audits);
  await page.getByRole("link", { name: "Back to announcement", exact: true }).click();
}
