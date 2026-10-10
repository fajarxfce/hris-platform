import { randomUUID } from "node:crypto";
import { type BrowserContext, expect, type Page } from "@playwright/test";
import { companyDate } from "../src/core/presentation/dates/company-date";

export async function reviewLeaveRequests(
  page: Page,
  context: BrowserContext,
  company: string,
  otherCompany: string,
  reviewer: string,
) {
  const csrf = (await (await context.request.get("/api/v1/auth/csrf")).json()) as {
    headerName: string;
    token: string;
  };
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const base = `/api/v1/companies/${company}`;
  const employee = randomUUID();
  const shift = randomUUID();
  const type = randomUUID();
  const request = randomUUID();
  const employeeName = "Browser Leave Employee";
  const created = await context.request.post(`${base}/employees`, {
    headers: headers(),
    data: {
      id: employee,
      employeeNumber: "LEAVE-EMPLOYEE",
      person: { id: randomUUID(), legalName: employeeName, nationality: "ID" },
      terms: {
        effectiveFrom: "2025-01-01",
        startDate: "2025-01-01",
        status: "ACTIVE",
        contract: "PERMANENT",
      },
      reason: "Browser leave setup",
    },
  });
  expect(created.status()).toBe(200);
  const shiftCreated = await context.request.put(`${base}/workforce/shifts/${shift}`, {
    headers: headers(),
    data: {
      code: "BROWSERNIGHT",
      name: "Browser night shift",
      startsAt: "22:00",
      endsAt: "06:00",
      breakMinutes: 30,
      timezone: "Asia/Jakarta",
      mode: "REMOTE",
      reason: "Browser schedule",
    },
  });
  expect(shiftCreated.status()).toBe(200);
  const scheduled = await context.request.put(`${base}/workforce/employees/${employee}/schedule`, {
    headers: headers(),
    data: {
      effectiveFrom: "2025-01-01",
      days: { MONDAY: { id: shift, version: 0 } },
      reason: "Browser schedule",
    },
  });
  expect(scheduled.status()).toBe(200);
  const policy = await context.request.put(`${base}/leave/types/${type}`, {
    headers: headers(),
    data: {
      code: "BROWSERLEAVE",
      name: "Browser annual leave",
      effectiveFrom: "2025-01-01",
      paid: true,
      allowPartialDays: true,
      reason: "Browser leave policy",
    },
  });
  expect(policy.status()).toBe(200);
  const monday = new Date(`${companyDate("Asia/Jakarta", new Date())}T00:00:00Z`);
  monday.setUTCDate(monday.getUTCDate() + ((8 - monday.getUTCDay()) % 7 || 7));
  const date = monday.toISOString().slice(0, 10);
  const balance = await context.request.post(
    `${base}/leave/employees/${employee}/balances/${type}/${date.slice(0, 4)}/adjustments`,
    {
      headers: headers(),
      data: {
        days: "2",
        expectedVersion: 0,
        reason: "Browser opening balance",
      },
    },
  );
  expect(balance.status()).toBe(200);
  const template = await context.request.put(`${base}/approvals/templates/${randomUUID()}`, {
    headers: headers(),
    data: {
      name: "Browser leave review",
      kind: "LEAVE",
      effectiveFrom: "2025-01-01",
      category: "BROWSERLEAVE",
      stages: [{ assignment: "NAMED", accountIds: [reviewer] }],
      reason: "Browser leave approval",
    },
  });
  expect(template.status()).toBe(200);
  const submitted = await context.request.post(`${base}/leave/requests`, {
    headers: headers(),
    data: {
      id: request,
      employeeId: employee,
      typeId: type,
      days: [{ workDate: date, portion: "FIRST_HALF" }],
      reason: "Browser personal leave",
    },
  });
  expect(submitted.status()).toBe(200);
  const source = await context.request.get(`${base}/leave/requests/${request}?historyLimit=20`);
  expect(source.status()).toBe(200);
  expect(await source.json()).toMatchObject({
    id: request,
    version: 0,
    status: "PENDING",
    chargedDays: "0.5",
    history: { items: [{ kind: "SUBMITTED" }] },
  });
  expect(
    (
      await context.request.get(`/api/v1/companies/${otherCompany}/leave/requests/${request}`)
    ).status(),
  ).toBe(404);
  await page.getByRole("link", { name: "Employees", exact: true }).click();
  await page
    .getByRole("table", { name: "Employee directory", exact: true })
    .getByRole("row")
    .filter({ hasText: employeeName })
    .getByRole("button")
    .click();
  await page.getByRole("main").getByRole("link", { name: "Leave requests", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`employee=${employee}$`, "u"));
  const table = page.getByRole("table", { name: "Leave requests", exact: true });
  await expect(table).toContainText(employeeName);
  await table.getByRole("button", { name: new RegExp(`${request}$`, "u") }).click();
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toContainText(
    "Browser personal leave",
  );
  await expect(
    page.getByRole("table", { name: "Submitted schedule (Asia/Jakarta)", exact: true }),
  ).toContainText("First half");
  await expect(page.getByRole("region", { name: "Submitted policy", exact: true })).toContainText(
    "Yes",
  );
  await expect(page.getByRole("table", { name: "Request history", exact: true })).toContainText(
    "Submitted",
  );
  await page.getByRole("button", { name: "View approval: Initial approval", exact: true }).click();
  await page.getByRole("link", { name: "View source request", exact: true }).click();
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toContainText(
    employeeName,
  );
}
