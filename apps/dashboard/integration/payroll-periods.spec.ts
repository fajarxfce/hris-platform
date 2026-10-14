import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { companyDate } from "../src/core/presentation/dates/company-date";
import { createAdminCompany } from "./fixtures/admin-company";
import { signInAndEnrollAuthenticator } from "./fixtures/sign-in-and-enroll-authenticator";

test("real period preparation recovers original commands and rechecks competing drafts and current scope", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  test.setTimeout(120_000);
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-payroll-period-admin@example.invalid",
    "PAY_PERIOD",
    "Browser Payroll Period",
  );
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() }),
    account = "a0000000-0000-4000-8000-000000000043",
    base = `/api/v1/companies/${company}`,
    endpoint = `${base}/payroll/periods`;
  expect(
    (
      await context.request.put(`${base}/members/${account}`, {
        headers: headers(),
        data: {
          active: true,
          permissions: ["company.read", "payroll.calculate"],
          reason: "Independent payroll preparer",
        },
      })
    ).status(),
  ).toBe(200);
  for (const [number, name, start] of [
    ["PER-001", "Period Employee One", "2025-01-01"],
    ["PER-002", "Future Period Employee", "2030-01-01"],
  ]) {
    expect(
      (
        await context.request.post(`${base}/employees`, {
          headers: headers(),
          data: {
            id: randomUUID(),
            employeeNumber: number,
            person: { id: randomUUID(), legalName: name, nationality: "ID" },
            terms: {
              effectiveFrom: start,
              startDate: start,
              status: "ACTIVE",
              contract: "PERMANENT",
            },
            reason: "Period preparation fixture",
          },
        })
      ).status(),
    ).toBe(200);
  }
  expect((await context.request.get(`${endpoint}/employees`)).status()).toBe(403);
  if (!baseURL) throw new Error("Integration base URL required");
  const operator = await browser.newContext({ baseURL });
  try {
    const editor = await operator.newPage();
    await signInAndEnrollAuthenticator(editor, "browser-payroll-period-operator@example.invalid");
    await editor.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
    await editor.getByRole("link", { name: "Payroll periods", exact: true }).click();
    await editor.getByRole("button", { name: "Create period", exact: true }).click();
    await expect(editor.getByRole("table", { name: "Employees", exact: true })).toContainText(
      "Future Period Employee",
    );
    const today = companyDate("Asia/Jakarta", new Date()),
      month = today.slice(0, 7);
    expect((await operator.request.get(`${base}/employees?asOf=${today}`)).status()).toBe(403);
    const operatorCsrf = (await (await operator.request.get("/api/v1/auth/csrf")).json()) as {
      headerName: string;
      token: string;
    };
    const operatorHeaders = () => ({
      [operatorCsrf.headerName]: operatorCsrf.token,
      "Idempotency-Key": randomUUID(),
    });
    await editor.getByLabel("Earnings month", { exact: true }).fill(month);
    await editor.getByLabel("Planned payment date", { exact: true }).fill(`${month}-28`);
    await editor.getByLabel("Reason", { exact: true }).fill("Reviewed monthly payroll roster");
    await editor.getByRole("checkbox", { name: "Select this page", exact: true }).check();
    await editor.getByRole("button", { name: "Review period", exact: true }).click();
    const heading = editor.getByRole("heading", { name: "Review period", level: 1, exact: true });
    await expect(heading).toBeFocused();
    await expect(heading).toBeInViewport({ ratio: 1 });
    await editor.screenshot({
      path: "../../.work/dashboard-payroll-period-real-review.png",
      fullPage: true,
      animations: "disabled",
    });
    const commands: { operation: string; body: Record<string, unknown> }[] = [];
    let drop = true;
    await editor.route(`**${endpoint}`, async (route) => {
      if (route.request().method() !== "POST") return route.continue();
      commands.push({
        operation: route.request().headers()["idempotency-key"] ?? "",
        body: route.request().postDataJSON() as Record<string, unknown>,
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      if (drop) {
        drop = false;
        return route.abort("failed");
      }
      return route.fulfill({ response });
    });
    await editor.getByRole("button", { name: "Create draft", exact: true }).click();
    await expect(
      editor.getByRole("status").filter({ hasText: "The result is unconfirmed." }),
    ).toBeVisible();
    await editor.getByRole("button", { name: "Retry request", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Payroll draft created.");
    expect(commands).toHaveLength(2);
    expect(commands[1]).toEqual(commands[0]);
    const original = commands[0];
    if (!original || typeof original.body.id !== "string")
      throw new Error("Original period command required");
    const id = original.body.id;
    await editor.getByRole("link", { name: "Open period", exact: true }).click();
    await expect(editor.getByRole("table", { name: "Employees", exact: true })).toContainText(
      "Period Employee One",
    );
    await expect(editor.getByRole("table", { name: "Employees", exact: true })).toContainText(
      "Future Period Employee",
    );
    await editor.getByRole("button", { name: "Cancel draft", exact: true }).click();
    await editor.getByLabel("Reason", { exact: true }).fill("Reviewed replacement");
    const competing = await operator.request.post(`${endpoint}/${id}/cancel`, {
      headers: operatorHeaders(),
      data: { expectedVersion: 0, reason: "Competing draft cancellation" },
    });
    expect(competing.status()).toBe(200);
    await editor.getByRole("button", { name: "Cancel draft", exact: true }).click();
    await editor.getByRole("button", { name: "Reload review", exact: true }).click();
    await editor
      .getByRole("dialog")
      .getByRole("button", { name: "Leave page", exact: true })
      .click();
    await expect(editor.getByRole("alert")).toContainText(
      "This action requires a draft payroll period.",
    );
    const replay = await operator.request.post(endpoint, {
      headers: {
        [operatorCsrf.headerName]: operatorCsrf.token,
        "Idempotency-Key": original.operation,
      },
      data: original.body,
    });
    expect(replay.status()).toBe(200);
    expect(await replay.json()).toMatchObject({ id, version: 0 });
    const retained = await operator.request.get(`${endpoint}/${id}`);
    expect(await retained.json()).toMatchObject({
      period: { id, status: "CANCELLED", participantCount: 2, version: 1 },
      history: { items: [{ version: 0 }, { version: 1 }] },
    });
    await editor.getByRole("link", { name: "Period details", exact: true }).click();
    await editor.getByRole("tab", { name: "History", exact: true }).click();
    await expect(editor.getByRole("table", { name: "History", exact: true })).toContainText(
      "Competing draft cancellation",
    );
    await editor.screenshot({
      path: "../../.work/dashboard-payroll-period-real-history.png",
      fullPage: false,
      animations: "disabled",
    });
    const replacement = randomUUID();
    const replaced = await operator.request.post(endpoint, {
      headers: operatorHeaders(),
      data: { ...original.body, id: replacement },
    });
    expect(replaced.status()).toBe(200);
    const collision = await operator.request.post(endpoint, {
      headers: operatorHeaders(),
      data: { ...original.body, id: randomUUID() },
    });
    expect(collision.status()).toBe(409);
    expect(await collision.json()).toMatchObject({ code: "payroll_period_active" });
    const reviewOnly = await context.request.put(`${base}/members/${account}`, {
      headers: headers(),
      data: {
        active: true,
        permissions: ["company.read", "payroll.review"],
        expectedVersion: 0,
        reason: "Switch to payroll review scope",
      },
    });
    expect(reviewOnly.status()).toBe(200);
    expect((await operator.request.get(`${endpoint}/${replacement}`)).status()).toBe(200);
    expect(
      (
        await operator.request.post(`${endpoint}/${replacement}/cancel`, {
          headers: operatorHeaders(),
          data: { expectedVersion: 0, reason: "Forbidden reviewer cancellation" },
        })
      ).status(),
    ).toBe(403);
    const revoke = await context.request.put(`${base}/members/${account}`, {
      headers: headers(),
      data: {
        active: true,
        permissions: ["company.read"],
        expectedVersion: 1,
        reason: "Revoke period access",
      },
    });
    expect(revoke.status()).toBe(200);
    await editor.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(editor.getByRole("alert")).toBeVisible();
    await expect(editor.getByRole("table", { name: "History", exact: true })).toHaveCount(0);
    await expect(editor.getByRole("region", { name: "Period details", exact: true })).toHaveCount(
      0,
    );
  } finally {
    await operator.close();
  }
});
