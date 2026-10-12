import { createHash, randomUUID } from "node:crypto";
import { expect, type Page, test } from "@playwright/test";
import type {
  DocumentRetentionDto,
  DocumentRetentionHistoryDto,
} from "../src/features/documents/data/models/document-retention-state-dto";
import { documentBytes } from "../tests/fixtures/documents";
import { createAdminCompany } from "./fixtures/admin-company";
import { signInAndEnrollAuthenticator } from "./fixtures/sign-in-and-enroll-authenticator";

async function confirm(page: Page, title: string, reason: string, saved: string) {
  await page.getByRole("link", { name: title, exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill(reason);
  await page.getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("status")).toContainText(saved);
  await page.getByRole("link", { name: "View retention", exact: true }).click();
  await expect(page.getByRole("region", { name: "Retention status", exact: true })).toBeVisible();
}

test("real archive, restore and legal hold retain policy snapshots and recover one committed transition", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  test.setTimeout(120_000);
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-document-lifecycle-admin@example.invalid",
    "DOCUMENTLIFECYCLE",
    "Document Lifecycle Browser",
  );
  const base = `/api/v1/companies/${company}`,
    employee = randomUUID(),
    documentId = randomUUID(),
    revisionId = randomUUID(),
    policyId = randomUUID();
  const adminHeaders = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  expect(
    (
      await context.request.post(`${base}/employees`, {
        headers: adminHeaders(),
        data: {
          id: employee,
          employeeNumber: "RETENTION-001",
          person: { id: randomUUID(), legalName: "Retention Fixture Employee", nationality: "ID" },
          terms: {
            effectiveFrom: "2026-01-01",
            startDate: "2026-01-01",
            status: "ACTIVE",
            contract: "PERMANENT",
          },
          reason: "Retention lifecycle acceptance",
        },
      })
    ).status(),
  ).toBe(200);
  const bytes = documentBytes();
  expect(
    (
      await context.request.post(`${base}/documents/uploads`, {
        headers: adminHeaders(),
        data: {
          documentId,
          revisionId,
          employmentId: employee,
          title: "Retention fixture",
          classification: "PERSONAL",
          expectedDocumentVersion: 0,
          fileName: "record.pdf",
          mediaType: "application/pdf",
          size: bytes.length,
          sha256: createHash("sha256").update(bytes).digest("hex"),
          reason: "Unfinished upload",
        },
      })
    ).status(),
  ).toBe(200);
  const operator = "a0000000-0000-4000-8000-000000000022",
    retention = `${base}/documents/${documentId}/retention`;
  expect((await context.request.get(retention)).status()).toBe(403);
  expect(
    (
      await context.request.put(`${base}/members/${operator}`, {
        headers: adminHeaders(),
        data: {
          active: true,
          permissions: ["company.read", "documents.retention"],
          reason: "Independent records operator",
        },
      })
    ).status(),
  ).toBe(200);
  if (!baseURL) throw new Error("Integration base URL missing");
  const operatorContext = await browser.newContext({ baseURL });
  try {
    const operatorPage = await operatorContext.newPage();
    await signInAndEnrollAuthenticator(
      operatorPage,
      "browser-document-lifecycle-operator@example.invalid",
    );
    const operatorCsrf = (await (
      await operatorContext.request.get("/api/v1/auth/csrf")
    ).json()) as { headerName: string; token: string };
    const operatorHeaders = () => ({
      [operatorCsrf.headerName]: operatorCsrf.token,
      "Idempotency-Key": randomUUID(),
    });
    expect(
      (
        await operatorContext.request.post(`${base}/documents/retention-policies`, {
          headers: operatorHeaders(),
          data: {
            policyId,
            classification: "PERSONAL",
            expectedVersion: null,
            retentionDays: 365,
            reason: "Initial retention policy",
          },
        })
      ).status(),
    ).toBe(200);
    expect((await operatorContext.request.get(`${base}/documents/${documentId}`)).status()).toBe(
      403,
    );
    await operatorPage
      .getByRole("navigation")
      .getByRole("link", { name: "Document retention", exact: true })
      .click();
    await operatorPage.getByLabel("Document ID", { exact: true }).fill(documentId);
    await operatorPage.getByRole("button", { name: "Review document", exact: true }).click();
    await operatorPage.getByRole("link", { name: "Archive document", exact: true }).click();
    await operatorPage.getByLabel("Reason", { exact: true }).fill("Archive selected record");
    await operatorPage.getByRole("button", { name: "Confirm", exact: true }).click();
    await expect(operatorPage.getByRole("alert")).toContainText("still being processed");
    expect(await (await operatorContext.request.get(retention)).json()).toMatchObject({
      version: null,
      archive: null,
    });
    expect(
      (
        await context.request.post(`${base}/documents/revisions/${revisionId}/cancel`, {
          headers: adminHeaders(),
          data: { expectedVersion: 0, reason: "Cancel unfinished upload before archiving" },
        })
      ).status(),
    ).toBe(200);
    await operatorPage.getByRole("button", { name: "Reload review", exact: true }).click();
    await operatorPage
      .getByRole("dialog")
      .getByRole("button", { name: "Leave page", exact: true })
      .click();
    await operatorPage
      .getByLabel("Reason", { exact: true })
      .fill("Archive after upload cancellation");
    const commands: { operation: string | undefined; body: unknown }[] = [];
    await operatorPage.route(`**${retention}/archive`, async (route) => {
      commands.push({
        operation: route.request().headers()["idempotency-key"],
        body: route.request().postDataJSON(),
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      if (commands.length === 1) {
        await response.dispose();
        return route.abort("failed");
      }
      return route.fulfill({ response });
    });
    await operatorPage.getByRole("button", { name: "Confirm", exact: true }).click();
    await expect(operatorPage.getByRole("status")).toContainText("The response was not confirmed.");
    await operatorPage.getByRole("button", { name: "Retry request", exact: true }).click();
    await expect(operatorPage.getByRole("status")).toContainText("Document archived.");
    expect(commands).toHaveLength(2);
    expect(commands[0]).toEqual(commands[1]);
    await operatorPage.unroute(`**${retention}/archive`);
    await operatorPage.getByRole("link", { name: "View retention", exact: true }).click();
    const archived = (await (
      await operatorContext.request.get(retention)
    ).json()) as DocumentRetentionDto;
    expect(archived).toMatchObject({
      version: 0,
      archive: { policyId, policyVersion: 0, retentionDays: 365 },
      legalHold: false,
    });
    expect(
      (
        await operatorContext.request.post(`${base}/documents/retention-policies`, {
          headers: operatorHeaders(),
          data: {
            policyId,
            classification: "PERSONAL",
            expectedVersion: 0,
            retentionDays: 1,
            reason: "Policy for future archives",
          },
        })
      ).status(),
    ).toBe(200);
    await confirm(operatorPage, "Place legal hold", "Retain for review", "Legal hold recorded.");
    expect(
      ((await (await operatorContext.request.get(retention)).json()) as DocumentRetentionDto)
        .archive,
    ).toEqual(archived.archive);
    await confirm(
      operatorPage,
      "Restore document",
      "Allow additional evidence",
      "Document restored.",
    );
    expect(await (await operatorContext.request.get(retention)).json()).toMatchObject({
      version: 2,
      archive: null,
      legalHold: true,
    });
    await confirm(
      operatorPage,
      "Archive document",
      "Adopt the current archive policy",
      "Document archived.",
    );
    expect(await (await operatorContext.request.get(retention)).json()).toMatchObject({
      version: 3,
      archive: { policyId, policyVersion: 1, retentionDays: 1 },
      legalHold: true,
    });
    await confirm(
      operatorPage,
      "Release legal hold",
      "Independent review completed",
      "Legal hold released.",
    );
    const history = (await (
      await operatorContext.request.get(`${retention}/history?limit=20`)
    ).json()) as DocumentRetentionHistoryDto;
    expect(
      history.items.map((change) => [change.state.version, change.action, change.actorId]),
    ).toEqual([
      [0, "ARCHIVE", operator],
      [1, "PLACE_HOLD", operator],
      [2, "RESTORE", operator],
      [3, "ARCHIVE", operator],
      [4, "RELEASE_HOLD", operator],
    ]);
    expect(history.items[0]?.state.archive).toEqual(archived.archive);
    await operatorPage
      .getByRole("button", { name: "View change: Retention version 0", exact: true })
      .click();
    const panel = operatorPage.getByRole("dialog", { name: "Change details", exact: true });
    await expect(panel).toBeInViewport();
    await expect(panel).toContainText("365 days");
    await operatorPage.screenshot({
      path: "../../.work/dashboard-document-retention-history-light.png",
      fullPage: true,
      animations: "disabled",
    });
    await operatorPage.getByRole("button", { name: "Close details", exact: true }).click();
    expect(
      (
        await context.request.put(`${base}/members/${operator}`, {
          headers: adminHeaders(),
          data: {
            expectedVersion: 0,
            active: true,
            permissions: ["company.read"],
            reason: "End temporary retention access",
          },
        })
      ).status(),
    ).toBe(200);
    await operatorPage.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(
      operatorPage.getByRole("region", { name: "Retention status", exact: true }),
    ).toHaveCount(0);
    await expect(operatorPage.getByRole("alert")).toBeVisible();
  } finally {
    await operatorContext.close();
  }
});
