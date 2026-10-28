import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "./fixtures/admin-company";
import { signInAndEnrollAuthenticator } from "./fixtures/sign-in-and-enroll-authenticator";

test("real catalogue shows only independent reader grants and rechecks report access after revocation", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-report-catalogue-admin@example.invalid",
    "REPORT_CATALOGUE",
    "Report Catalogue Browser",
  );
  const member = "a0000000-0000-4000-8000-000000000087";
  const base = `/api/v1/companies/${company}`;
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const create = await context.request.put(`${base}/members/${member}`, {
    headers: headers(),
    data: {
      active: true,
      permissions: ["company.read", "reports.read", "people.read"],
      reason: "Independent report discovery",
    },
  });
  expect(create.status()).toBe(200);
  const membership = await create.json();
  if (!baseURL) throw new Error("Browser API fixture URL is required");
  const readerContext = await browser.newContext({ baseURL });
  try {
    const reader = await readerContext.newPage();
    await signInAndEnrollAuthenticator(reader, "browser-report-catalogue-reader@example.invalid");
    await expect(reader.getByRole("heading", { name: "Overview", exact: true })).toBeVisible();
    await reader.getByRole("link", { name: "Report catalogue", exact: true }).click();
    const table = reader.getByRole("table", { name: "Available reports", exact: true });
    await expect(table.getByRole("row")).toHaveCount(2);
    await table.getByRole("button", { name: "Open report: Headcount", exact: true }).click();
    await expect(
      reader.getByRole("region", { name: "Employment records", exact: true }),
    ).toContainText("0");
    await reader.goBack();
    await expect(table).toBeVisible();
    const revoke = await context.request.put(`${base}/members/${member}`, {
      headers: headers(),
      data: {
        active: true,
        permissions: ["company.read", "reports.read"],
        expectedVersion: membership.version,
        reason: "Remove source grant",
      },
    });
    expect(revoke.status()).toBe(200);
    await reader.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(table).toHaveCount(0);
    await expect(
      reader.getByText("No reports are available with your current company permissions.", {
        exact: true,
      }),
    ).toBeVisible();
    const report = await readerContext.request.get(
      `/api/v1/reports/headcount?companies=${company}&asOf=2026-10-01`,
    );
    expect(report.status()).toBe(403);
    const discovery = await readerContext.request.get(`${base}/reports/catalogue`);
    expect(discovery.status()).toBe(200);
    expect((await discovery.json()).reports).toEqual([]);
    expect(discovery.headers()["cache-control"]).toContain("no-store");
    await reader.screenshot({ path: "../../.work/report-catalogue-real.png", fullPage: true });
  } finally {
    await readerContext.close();
  }
});
