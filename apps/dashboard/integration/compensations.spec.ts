import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { companyDate } from "../src/core/presentation/dates/company-date";
import type { CompensationChangeDto } from "../src/features/payroll/data/models/compensation-change-dto";
import { fillCompensationForm } from "../tests/fixtures/fill-compensation-form";
import { createAdminCompany } from "./fixtures/admin-company";
import { signInAndEnrollAuthenticator } from "./fixtures/sign-in-and-enroll-authenticator";

test("real compensation administration keeps scope, employment observations and original-command recovery", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  test.setTimeout(120_000);
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-compensation-admin@example.invalid",
    "PAY_COMP",
    "Browser Compensation",
  );
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const other = await context.request.post("/api/v1/companies", {
    headers: headers(),
    data: { code: "PAY_COMP_OTHER", name: "Other Compensation Company", timezone: "Asia/Jakarta" },
  });
  expect(other.status()).toBe(200);
  const otherCompany = ((await other.json()) as { id: string }).id;
  const account = "a0000000-0000-4000-8000-000000000041";
  for (const id of [company, otherCompany])
    expect(
      (
        await context.request.put(`/api/v1/companies/${id}/members/${account}`, {
          headers: headers(),
          data: {
            active: true,
            permissions: ["company.read", "payroll.compensation.manage"],
            reason: "Independent compensation operator",
          },
        })
      ).status(),
    ).toBe(200);
  const employee = randomUUID(),
    futureEmployee = randomUUID(),
    base = `/api/v1/companies/${company}`;
  for (const record of [
    {
      id: employee,
      number: "COMP-001",
      name: "Browser Compensation Employee",
      start: "2025-01-01",
    },
    {
      id: futureEmployee,
      number: "COMP-002",
      name: "Future Compensation Employee",
      start: "2030-01-01",
    },
  ])
    expect(
      (
        await context.request.post(`${base}/employees`, {
          headers: headers(),
          data: {
            id: record.id,
            employeeNumber: record.number,
            person: { id: randomUUID(), legalName: record.name, nationality: "ID" },
            terms: {
              effectiveFrom: record.start,
              startDate: record.start,
              status: "ACTIVE",
              contract: "PERMANENT",
            },
            reason: "Independent compensation fixture",
          },
        })
      ).status(),
    ).toBe(200);
  const today = companyDate("Asia/Jakarta", new Date()),
    month = today.slice(0, 7),
    future = `${Number(month.slice(0, 4)) + 1}-01`,
    endpoint = `${base}/payroll/employees/${employee}/compensation`;
  expect((await context.request.get(`${endpoint}/review?asOf=${month}`)).status()).toBe(403);
  if (!baseURL) throw new Error("Integration base URL is required");
  const operator = await browser.newContext({ baseURL });
  try {
    const editor = await operator.newPage();
    await signInAndEnrollAuthenticator(editor, "browser-compensation-operator@example.invalid");
    await editor.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
    await editor.getByRole("link", { name: "Compensation", exact: true }).click();
    await expect(editor.getByRole("table", { name: "Employees", exact: true })).toContainText(
      "Future Compensation Employee",
    );
    expect((await operator.request.get(`${base}/employees?asOf=${today}`)).status()).toBe(403);
    expect(
      (
        await operator.request.get(
          `/api/v1/companies/${otherCompany}/payroll/employees/${employee}/compensation/review?asOf=${month}`,
        )
      ).status(),
    ).toBe(404);
    await editor
      .getByRole("button", {
        name: "View compensation: Browser Compensation Employee",
        exact: true,
      })
      .click();
    await expect(editor.getByRole("status")).toContainText("No compensation configured.");
    await editor.getByRole("link", { name: "Set compensation", exact: true }).click();
    await expect(editor.getByLabel("Basic salary (IDR)", { exact: true })).toHaveValue("");
    await fillCompensationForm(editor, today);
    await editor.evaluate(() => window.scrollTo(0, 0));
    await editor.screenshot({
      path: "../../.work/dashboard-compensation-real-editor.png",
      fullPage: true,
      animations: "disabled",
    });
    await editor.getByRole("button", { name: "Save compensation", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Compensation saved.");
    await editor.getByRole("link", { name: "View compensation", exact: true }).click();
    await expect(
      editor.getByRole("region", { name: "Effective compensation", exact: true }),
    ).toContainText("10,000,000.25");
    const beforeStart = await operator.request.get(`${endpoint}/review?asOf=2024-01`);
    expect(beforeStart.status()).toBe(200);
    expect(await beforeStart.json()).toMatchObject({
      employee: { employmentVersion: 0 },
      current: { version: 0 },
      effective: null,
    });
    await editor.getByRole("link", { name: "New revision", exact: true }).click();
    await editor.getByLabel("Reason", { exact: true }).fill("Review before employment change");
    const changed = await context.request.post(`${base}/employees/${employee}/revisions`, {
      headers: headers(),
      data: {
        version: 0,
        terms: {
          effectiveFrom: today,
          startDate: "2025-01-01",
          status: "ACTIVE",
          contract: "PERMANENT",
        },
        reason: "Independent employment revision",
      },
    });
    expect(changed.status()).toBe(200);
    await editor.getByRole("button", { name: "Save compensation", exact: true }).click();
    await editor.getByRole("button", { name: "Load current compensation", exact: true }).click();
    await editor
      .getByRole("dialog")
      .getByRole("button", { name: "Leave page", exact: true })
      .click();
    await expect(editor.getByLabel("Reason", { exact: true })).toHaveValue("");
    await editor.getByLabel("Effective from", { exact: true }).fill(future);
    await editor.getByLabel("Basic salary (IDR)", { exact: true }).fill("12000000.75");
    await editor.getByLabel("Reason", { exact: true }).fill("Reviewed future compensation");
    const commands: { operation: string; body: CompensationChangeDto }[] = [];
    await editor.route(`**${endpoint}`, async (route) => {
      commands.push({
        operation: route.request().headers()["idempotency-key"] ?? "",
        body: route.request().postDataJSON() as CompensationChangeDto,
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      if (commands.length === 1) return route.abort("failed");
      return route.fulfill({ response });
    });
    await editor.getByRole("button", { name: "Save compensation", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("The save result is unconfirmed");
    await editor.getByRole("button", { name: "Retry save", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Compensation saved.");
    expect(commands).toHaveLength(2);
    expect(commands[0]).toEqual(commands[1]);
    expect(commands[0]?.body).toMatchObject({ expectedVersion: 0, expectedEmploymentVersion: 1 });
    await editor.unroute(`**${endpoint}`);
    const review = await operator.request.get(`${endpoint}/review?asOf=${month}`);
    expect(review.status()).toBe(200);
    expect(await review.json()).toMatchObject({
      employee: { employmentVersion: 1 },
      current: {
        version: 1,
        appliedRevision: 1,
        effectiveFrom: future,
        terms: { basicSalary: "12000000.75" },
      },
      effective: { version: 1, appliedRevision: 0, terms: { basicSalary: "10000000.25" } },
      history: {
        items: [{ compensation: { appliedRevision: 0 } }, { compensation: { appliedRevision: 1 } }],
      },
    });
    await editor.getByRole("link", { name: "View compensation", exact: true }).click();
    await editor.getByRole("button", { name: "View: Revision 0", exact: true }).click();
    await expect(editor.getByRole("dialog")).toContainText("Reviewed employee compensation");
    await editor.screenshot({
      path: "../../.work/dashboard-compensation-real-history.png",
      fullPage: false,
      animations: "disabled",
    });
    await editor.getByRole("button", { name: "Close details", exact: true }).click();
    const linked = await context.request.post(`${base}/employees/${employee}/account-link`, {
      headers: headers(),
      data: { accountId: account, expectedVersion: 0, reason: "Independent identity review" },
    });
    expect(linked.status()).toBe(200);
    await editor.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(
      editor.getByRole("region", { name: "Effective compensation", exact: true }),
    ).toBeVisible();
    await expect(editor.getByRole("link", { name: "New revision", exact: true })).toHaveCount(0);
    const operatorCsrf = (await (await operator.request.get("/api/v1/auth/csrf")).json()) as {
      headerName: string;
      token: string;
    };
    const original = commands[0];
    if (!original) throw new Error("Original compensation command is required");
    const replay = await operator.request.put(endpoint, {
      headers: {
        [operatorCsrf.headerName]: operatorCsrf.token,
        "Idempotency-Key": original.operation,
      },
      data: original.body,
    });
    expect(replay.status()).toBe(403);
    expect(await replay.json()).toMatchObject({ code: "self_compensation_change_denied" });
    const revoked = await context.request.put(`${base}/members/${account}`, {
      headers: headers(),
      data: {
        active: true,
        permissions: ["company.read"],
        expectedVersion: 0,
        reason: "Remove compensation access for isolation acceptance",
      },
    });
    expect(revoked.status()).toBe(200);
    await editor.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(editor.getByRole("alert")).toBeVisible();
    await expect(
      editor.getByRole("table", { name: "Compensation history", exact: true }),
    ).toHaveCount(0);
    await expect(
      editor.getByRole("region", { name: "Effective compensation", exact: true }),
    ).toHaveCount(0);
  } finally {
    await operator.close();
  }
});
