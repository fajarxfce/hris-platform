import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import type { DocumentRetentionHistoryDto } from "../src/features/documents/data/models/document-retention-state-dto";
import type { DocumentRetirementReviewDto } from "../src/features/documents/data/models/document-retirement-dto";
import { signInAndEnrollAuthenticator } from "./fixtures/sign-in-and-enroll-authenticator";

// These references belong only to retirement-archive.sql in the disposable fixture.
const company = "b0000000-0000-4000-8000-000000000023",
  documentId = "e0000000-0000-4000-8000-000000000023",
  historical = "e0000000-0000-4000-8000-000000000031",
  published = "e0000000-0000-4000-8000-000000000032",
  operator = "a0000000-0000-4000-8000-000000000023";
const base = `/api/v1/companies/${company}/documents`,
  detail = `/documents/retention/documents/${documentId}`;

test("real retirement recovers a committed result, preserves newer publication and rechecks a competing hold", async ({
  page,
  context,
}) => {
  test.setTimeout(120_000);
  await signInAndEnrollAuthenticator(page, "browser-retirement-operator@example.invalid");
  const csrf = (await (await context.request.get("/api/v1/auth/csrf")).json()) as {
    headerName: string;
    token: string;
  };
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const review = async (revision: string) => {
    const response = await context.request.get(`${base}/revisions/${revision}/retirement`);
    expect(response.status()).toBe(200);
    return (await response.json()) as DocumentRetirementReviewDto;
  };
  const initial = await review(historical);
  expect(initial).toMatchObject({
    published: false,
    revisionVersion: 3,
    blockingReason: null,
    retention: { version: 0, legalHold: false, archive: { retentionDays: 1 } },
  });
  expect(Date.parse(initial.retention.archive?.eligibleAt ?? "")).toBeLessThan(
    Date.parse(initial.reviewedAt),
  );
  expect((await context.request.get(`${base}/${documentId}`)).status()).toBe(403);
  expect((await context.request.get(`${base}/revisions/${historical}`)).status()).toBe(403);
  expect((await context.request.get(`${base}/revisions/${historical}/content`)).status()).toBe(403);
  const commands: {
    revision: string;
    operation: string;
    body: { expectedRevisionVersion: number; expectedRetentionVersion: number; reason: string };
  }[] = [];
  await page.route(`**${base}/revisions/*/retire`, async (route) => {
    commands.push({
      revision: route.request().url().split("/").at(-2) ?? "",
      operation: route.request().headers()["idempotency-key"] ?? "",
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    if (commands.length === 1) {
      expect(response.status()).toBe(200);
      await response.dispose();
      return route.abort("failed");
    }
    return route.fulfill({ response });
  });
  await page.goto(`${detail}/revisions/${historical}?company=${company}`);
  await page.getByLabel("Reason", { exact: true }).fill("Retire the expired historical revision");
  await page.getByRole("button", { name: "Retire revision", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The response was not confirmed.");
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText(
    "Content retired. File deletion is scheduled.",
  );
  expect(commands).toHaveLength(2);
  expect(commands[0]).toEqual(commands[1]);
  expect(await review(historical)).toMatchObject({
    status: "RETIRED",
    revisionVersion: 4,
    published: false,
    retention: { version: 1 },
  });
  expect(await review(published)).toMatchObject({
    status: "READY",
    published: true,
    blockingReason: null,
  });

  await page.getByRole("link", { name: "View retention", exact: true }).click();
  await page.getByLabel("Revision ID", { exact: true }).fill(published);
  await page.getByRole("button", { name: "Review retirement", exact: true }).click();
  await expect(
    page.getByText("This is the published revision. Retirement also removes its publication.", {
      exact: true,
    }),
  ).toBeVisible();
  expect(
    (
      await context.request.post(`${base}/${documentId}/retention/hold`, {
        headers: headers(),
        data: { expectedVersion: 1, reason: "Hold pending additional review" },
      })
    ).status(),
  ).toBe(200);
  const original = commands[0];
  if (!original) throw new Error("Expected original command");
  expect(
    (
      await context.request.post(`${base}/revisions/${historical}/retire`, {
        headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": original.operation },
        data: original.body,
      })
    ).status(),
  ).toBe(200);
  expect(await review(published)).toMatchObject({
    status: "READY",
    retention: { version: 2, legalHold: true },
  });
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed before the new hold");
  await page.getByRole("button", { name: "Retire revision", exact: true }).click();
  await expect(page.getByRole("button", { name: "Retire revision", exact: true })).toBeDisabled();
  await expect(page.getByRole("alert")).toBeVisible();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("active legal hold");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);

  expect(
    (
      await context.request.post(`${base}/${documentId}/retention/release-hold`, {
        headers: headers(),
        data: { expectedVersion: 2, reason: "Review completed" },
      })
    ).status(),
  ).toBe(200);
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Retire the reviewed published revision");
  await page.getByRole("button", { name: "Retire revision", exact: true }).click();
  await expect(page.getByRole("status")).toContainText(
    "Content retired. File deletion is scheduled.",
  );
  expect(commands).toHaveLength(4);
  expect(commands[2]?.operation).not.toBe(commands[3]?.operation);
  expect(commands[2]?.body.expectedRetentionVersion).toBe(1);
  expect(commands[3]?.body.expectedRetentionVersion).toBe(3);
  expect(await review(published)).toMatchObject({
    status: "RETIRED",
    published: false,
    revisionVersion: 4,
    blockingReason: "document_revision_not_ready",
    retention: { version: 4 },
  });
  const history = (await (
    await context.request.get(`${base}/${documentId}/retention/history?limit=20`)
  ).json()) as DocumentRetentionHistoryDto;
  expect(
    history.items.map((change) => [change.state.version, change.action, change.retiredRevisionId]),
  ).toEqual([
    [0, "ARCHIVE", null],
    [1, "RETIRE", historical],
    [2, "PLACE_HOLD", null],
    [3, "RELEASE_HOLD", null],
    [4, "RETIRE", published],
  ]);
  expect(history.items.slice(1).every((change) => change.actorId === operator)).toBe(true);
  expect(
    history.items.every(
      (change) =>
        JSON.stringify(change.state.archive) === JSON.stringify(initial.retention.archive),
    ),
  ).toBe(true);
  await page.getByRole("link", { name: "View retention", exact: true }).click();
  await page.getByRole("button", { name: "View change: Retention version 4", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Change details", exact: true });
  await expect(panel).toBeInViewport();
  await expect(panel).toContainText(published);
  await expect(panel).toContainText("Retire the reviewed published revision");
  await page.screenshot({
    path: "../../.work/dashboard-document-retirement-history-light.png",
    fullPage: true,
    animations: "disabled",
  });
});
