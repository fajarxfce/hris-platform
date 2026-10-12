import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "./fixtures/admin-company";
import { authenticatorCode } from "./fixtures/authenticator";

test("independent retention administration recovers committed saves and rejects concurrent or cross-company review", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  test.setTimeout(120_000);
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-retention-admin@example.invalid",
    "RETENTIONUI",
    "Retention Browser",
  );
  const base = `/api/v1/companies/${company}`,
    policies = `${base}/documents/retention-policies`;
  expect((await context.request.get(policies)).status()).toBe(403);
  const adminHeaders = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const operator = "a0000000-0000-4000-8000-000000000020";
  const granted = await context.request.put(`${base}/members/${operator}`, {
    headers: adminHeaders(),
    data: {
      active: true,
      permissions: ["company.read", "documents.retention"],
      reason: "Independent document retention operator",
    },
  });
  expect(granted.status()).toBe(200);
  if (!baseURL) throw new Error("Integration base URL missing");
  const operatorContext = await browser.newContext({ baseURL });
  try {
    const operatorPage = await operatorContext.newPage();
    await operatorPage.goto("/");
    await operatorPage
      .getByLabel("Email", { exact: true })
      .fill("browser-retention-operator@example.invalid");
    await operatorPage
      .getByLabel("Password", { exact: true })
      .fill("Browser-fixture-password-123!");
    await operatorPage.getByRole("button", { name: "Sign in", exact: true }).click();
    await operatorPage
      .getByRole("button", { name: "Set up an authenticator", exact: true })
      .click();
    const key = operatorPage.locator(".app-enrollment-key code");
    await expect(key).toBeVisible();
    await operatorPage
      .getByLabel("Authenticator code", { exact: true })
      .fill(authenticatorCode((await key.textContent()) ?? ""));
    await operatorPage.getByRole("button", { name: "Verify", exact: true }).click();
    await operatorPage.getByRole("button", { name: "I have saved the codes", exact: true }).click();
    await operatorPage
      .getByRole("navigation")
      .getByRole("link", { name: "Document retention", exact: true })
      .click();
    await expect(
      operatorPage
        .getByRole("table", { name: "Policies", exact: true })
        .getByText("Not configured", { exact: true }),
    ).toHaveCount(3);
    expect((await operatorContext.request.get(`${base}/documents/employees`)).status()).toBe(403);
    await operatorPage.getByRole("button", { name: "View policy: Personal", exact: true }).click();
    await operatorPage.getByRole("link", { name: "Configure policy", exact: true }).click();
    await operatorPage.getByLabel("Retention days", { exact: true }).fill("730");
    await operatorPage
      .getByLabel("Reason", { exact: true })
      .fill("Approved retention for future archives");
    const commands: { operation: string | undefined; body: unknown }[] = [];
    await operatorPage.route(`**${policies}`, async (route) => {
      if (route.request().method() !== "POST") return route.continue();
      commands.push({
        operation: route.request().headers()["idempotency-key"],
        body: route.request().postDataJSON(),
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      if (commands.length === 1) return route.abort("failed");
      return route.fulfill({ response });
    });
    await operatorPage.getByRole("button", { name: "Save policy", exact: true }).click();
    await expect(operatorPage.getByRole("status")).toContainText("The response was not confirmed.");
    await operatorPage.getByRole("button", { name: "Retry request", exact: true }).click();
    await expect(operatorPage.getByRole("status")).toContainText("Retention policy saved.");
    expect(commands).toHaveLength(2);
    expect(commands[0]).toEqual(commands[1]);
    await operatorPage.unroute(`**${policies}`);
    const current = (await (await operatorContext.request.get(policies)).json()) as {
      id: string;
      version: number;
      retentionDays: number;
    }[];
    expect(current).toHaveLength(1);
    expect(current[0]).toMatchObject({ version: 0, retentionDays: 730 });
    const policy = current[0]?.id;
    if (!policy) throw new Error("Created policy missing");
    await operatorPage.getByRole("link", { name: "View policy", exact: true }).click();
    await operatorPage.getByRole("link", { name: "Edit policy", exact: true }).click();
    await expect(operatorPage.getByLabel("Retention days", { exact: true })).toHaveValue("730");
    const operatorCsrf = (await (
      await operatorContext.request.get("/api/v1/auth/csrf")
    ).json()) as { headerName: string; token: string };
    const competing = await operatorContext.request.post(policies, {
      headers: { [operatorCsrf.headerName]: operatorCsrf.token, "Idempotency-Key": randomUUID() },
      data: {
        policyId: policy,
        classification: "PERSONAL",
        expectedVersion: 0,
        retentionDays: 900,
        reason: "Concurrent policy review",
      },
    });
    expect(competing.status()).toBe(200);
    await operatorPage.getByLabel("Reason", { exact: true }).fill("Old review");
    await operatorPage.getByRole("button", { name: "Save policy", exact: true }).click();
    await expect(operatorPage.getByRole("alert")).toContainText("This record has changed");
    await expect(
      operatorPage.getByRole("button", { name: "Save policy", exact: true }),
    ).toBeDisabled();
    await operatorPage.getByRole("button", { name: "Reload review", exact: true }).click();
    await operatorPage
      .getByRole("dialog")
      .getByRole("button", { name: "Leave page", exact: true })
      .click();
    await expect(operatorPage.getByLabel("Retention days", { exact: true })).toHaveValue("900");
    await operatorPage.getByLabel("Retention days", { exact: true }).fill("");
    await operatorPage.getByLabel("Reason", { exact: true }).fill("Indefinite retention approved");
    await operatorPage.getByRole("button", { name: "Save policy", exact: true }).click();
    await expect(operatorPage.getByRole("status")).toContainText("Retention policy saved.");
    await operatorPage.getByRole("link", { name: "View policy", exact: true }).click();
    await expect(
      operatorPage.getByRole("region", { name: "Current policy", exact: true }),
    ).toContainText("Indefinite");
    const history = (await (
      await operatorContext.request.get(`${policies}/${policy}/history?limit=20`)
    ).json()) as {
      items: { version: number; retentionDays: number | null; actorId: string }[];
      nextCursor: string | null;
    };
    expect(history.items.map((item) => [item.version, item.retentionDays, item.actorId])).toEqual([
      [0, 730, operator],
      [1, 900, operator],
      [2, null, operator],
    ]);
    expect(history.nextCursor).toBeNull();
    await operatorPage.screenshot({
      path: "../../.work/dashboard-retention-policy-light.png",
      fullPage: true,
      animations: "disabled",
    });
    const other = await context.request.post("/api/v1/companies", {
      headers: adminHeaders(),
      data: { code: "RETENTIONOTHER", name: "Other Retention", timezone: "Asia/Jakarta" },
    });
    expect(other.status()).toBe(200);
    const otherCompany = ((await other.json()) as { id: string }).id;
    expect(
      (
        await context.request.put(`/api/v1/companies/${otherCompany}/members/${operator}`, {
          headers: adminHeaders(),
          data: {
            active: true,
            permissions: ["company.read", "documents.retention"],
            reason: "Other company retention",
          },
        })
      ).status(),
    ).toBe(200);
    expect(
      (
        await operatorContext.request.get(
          `/api/v1/companies/${otherCompany}/documents/retention-policies/${policy}/history`,
        )
      ).status(),
    ).toBe(404);
    await operatorPage.reload();
    await operatorPage
      .getByRole("combobox", { name: "Company", exact: true })
      .selectOption(otherCompany);
    await expect(operatorPage).toHaveURL(`/documents/retention-policies?company=${otherCompany}`);
    await expect(
      operatorPage
        .getByRole("table", { name: "Policies", exact: true })
        .getByText("Not configured", { exact: true }),
    ).toHaveCount(3);
    expect(
      (
        await context.request.put(`${base}/members/${operator}`, {
          headers: adminHeaders(),
          data: {
            expectedVersion: 0,
            active: true,
            permissions: ["company.read"],
            reason: "Retire temporary grant",
          },
        })
      ).status(),
    ).toBe(200);
    expect((await operatorContext.request.get(policies)).status()).toBe(403);
  } finally {
    await operatorContext.close();
  }
});
