import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { signInAndEnrollAuthenticator } from "../integration/fixtures/sign-in-and-enroll-authenticator";

test("another actor's saved account access stays historical and requires both grants through actual RLS", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  await signInAndEnrollAuthenticator(page, "browser-audit-detail-operator@example.invalid");
  const csrf = await (await context.request.get("/api/v1/auth/csrf")).json();
  const operator = "a0000000-0000-4000-8000-000000000100";
  const reviewer = "a0000000-0000-4000-8000-000000000101";
  const target = "a0000000-0000-4000-8000-000000000102";
  const base = "/api/v1/administration/audit-events";
  const reason = "Approved access for the fictional audit reviewer";
  const save = async (id: string, active: boolean, platformPermissions: string[], why: string) => {
    const review = await context.request.get(`/api/v1/identity/accounts/${id}`);
    expect(review.status()).toBe(200);
    const result = await context.request.put(`/api/v1/identity/accounts/${id}/access`, {
      headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
      data: {
        expectedVersion: (await review.json()).account.version,
        active,
        platformPermissions,
        reason: why,
      },
    });
    expect(result.status()).toBe(200);
  };
  await save(reviewer, true, ["audit.read", "identity.manage"], "Independent audit reader");
  await save(target, true, ["audit.read"], reason);
  if (!baseURL) throw new Error("Owned browser URL required");
  const readerContext = await browser.newContext({ baseURL });
  const limitedContext = await browser.newContext({ baseURL });
  try {
    const reader = await readerContext.newPage();
    const limited = await limitedContext.newPage();
    await signInAndEnrollAuthenticator(reader, "browser-audit-detail-reviewer@example.invalid");
    await signInAndEnrollAuthenticator(limited, "browser-audit-detail-metadata@example.invalid");
    const history = await readerContext.request.get(
      `${base}?resourceId=${target}&action=identity.account_access_saved`,
    );
    expect(history.status()).toBe(200);
    const journal = await history.json();
    expect(journal.items).toHaveLength(1);
    const original = journal.items[0];
    expect(original.actorId).toBe(operator);
    expect(JSON.stringify(journal)).not.toContain(reason);
    const detailPath = `${base}/${original.id}/account-access`;
    const detail = await readerContext.request.get(detailPath);
    expect(detail.status()).toBe(200);
    const saved = await detail.json();
    expect(saved).toEqual({
      id: original.id,
      accountId: target,
      active: true,
      permissions: ["audit.read"],
      reason,
    });
    expect((await limitedContext.request.get(detailPath)).status()).toBe(403);
    await limited.goto(`/platform/audit?resourceId=${target}&action=identity.account_access_saved`);
    await limited.getByRole("button", { name: /^View details:/u }).click();
    await expect(
      limited.getByRole("heading", { name: "Event metadata", exact: true }),
    ).toBeVisible();
    await expect(limited.getByText(reason)).toHaveCount(0);
    await expect(
      limited.getByRole("heading", { name: "Saved account access", exact: true }),
    ).toHaveCount(0);
    await reader.goto(`/platform/audit?resourceId=${target}&action=identity.account_access_saved`);
    await reader.getByRole("button", { name: /^View details:/u }).click();
    await expect(reader.getByText(reason, { exact: true })).toBeVisible();
    await save(target, false, [], "Later access removed");
    const retained = await readerContext.request.get(detailPath);
    expect(retained.status()).toBe(200);
    expect(await retained.json()).toEqual(saved);
    await expect(reader.getByText(reason, { exact: true })).toBeVisible();
    const latest = await (
      await readerContext.request.get(
        `${base}?resourceId=${target}&action=identity.account_access_saved`,
      )
    ).json();
    expect(latest.items).toHaveLength(2);
    const current = await (
      await readerContext.request.get(`${base}/${latest.items[0].id}/account-access`)
    ).json();
    expect(current).toMatchObject({
      active: false,
      permissions: [],
      reason: "Later access removed",
    });
    await reader.screenshot({ path: "../../.work/account-audit-real.png", fullPage: true });
    await save(reviewer, true, ["audit.read"], "Detailed audit grant removed");
    expect((await readerContext.request.get(detailPath)).status()).toBe(401);
    // Refresh sits outside the open Fluent drawer. Close returns focus without rereading.
    await reader.getByRole("button", { name: "Close", exact: true }).click();
    await reader.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(reader.getByText(reason)).toHaveCount(0);
    await expect(reader.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
  } finally {
    await readerContext.close();
    await limitedContext.close();
  }
});
