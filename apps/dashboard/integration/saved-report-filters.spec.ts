import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "./fixtures/admin-company";
import { signInAndEnrollAuthenticator } from "./fixtures/sign-in-and-enroll-authenticator";

test("real personal filters recover committed saves and deletes while enforcing owner and revoked source access", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-report-filter-admin@example.invalid",
    "REPORT_FILTER",
    "Report Filter Browser",
  );
  const member = "a0000000-0000-4000-8000-000000000089";
  const base = `/api/v1/companies/${company}`;
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const assignment = await context.request.put(`${base}/members/${member}`, {
    headers: headers(),
    data: {
      active: true,
      permissions: ["company.read", "reports.read", "people.read"],
      reason: "Independent saved-filter acceptance",
    },
  });
  expect(assignment.status()).toBe(200);
  const membership = await assignment.json();
  if (!baseURL) throw new Error("Browser fixture URL required");
  const readerContext = await browser.newContext({ baseURL });
  try {
    const reader = await readerContext.newPage();
    await signInAndEnrollAuthenticator(reader, "browser-report-filter-reader@example.invalid");
    await reader.goto(`/reports/headcount?company=${company}&companies=${company}&asOf=2026-10-01`);
    await reader.getByRole("link", { name: "Save applied filter", exact: true }).click();
    await reader.getByLabel("Filter name", { exact: true }).fill("Independent October review");
    const saves: { id: string; operation: string; body: unknown }[] = [];
    let dropSave = true;
    await reader.route(`**/companies/${company}/reports/saved-filters/*`, async (route) => {
      if (route.request().method() !== "PUT") return route.continue();
      saves.push({
        id: new URL(route.request().url()).pathname.split("/").at(-1) ?? "",
        operation: route.request().headers()["idempotency-key"] ?? "",
        body: route.request().postDataJSON(),
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      if (dropSave) {
        dropSave = false;
        return route.abort("failed");
      }
      return route.fulfill({ response });
    });
    await reader.getByRole("button", { name: "Save filter", exact: true }).click();
    await expect(reader.getByRole("status")).toContainText("The result is unconfirmed.");
    await reader.getByRole("button", { name: "Recover original command", exact: true }).click();
    await expect(reader.getByRole("status")).toContainText("Filter saved.");
    expect(saves).toHaveLength(2);
    expect(saves[1]).toEqual(saves[0]);
    const filterId = saves[0]?.id;
    expect(filterId).toMatch(/^[0-9a-f-]{36}$/u);
    const own = await readerContext.request.get(`${base}/reports/saved-filters`);
    expect(own.status()).toBe(200);
    expect(own.headers()["cache-control"]).toContain("no-store");
    const stored = (await own.json()).items;
    expect(stored).toHaveLength(1);
    expect(stored[0]).toMatchObject({
      id: filterId,
      version: 0,
      name: "Independent October review",
      parameters: { asOf: "2026-10-01", companies: [company] },
    });
    const admin = await context.request.get(`${base}/reports/saved-filters`);
    expect(admin.status()).toBe(200);
    expect((await admin.json()).items).toEqual([]);
    expect((await context.request.get(`${base}/reports/saved-filters/${filterId}`)).status()).toBe(
      404,
    );
    const revoked = await context.request.put(`${base}/members/${member}`, {
      headers: headers(),
      data: {
        active: true,
        permissions: ["company.read", "reports.read"],
        expectedVersion: membership.version,
        reason: "Revoke report source while retaining personal filter cleanup",
      },
    });
    expect(revoked.status()).toBe(200);
    await reader.goto(`/reports/saved-filters/${filterId}?company=${company}`);
    await expect(reader.getByLabel("Filter name", { exact: true })).toHaveValue(
      "Independent October review",
    );
    await expect(reader.getByRole("button", { name: "Save filter", exact: true })).toBeDisabled();
    await expect(
      reader.getByRole("link", { name: "Open saved selection", exact: true }),
    ).toHaveCount(0);
    expect(
      (
        await readerContext.request.get(
          `/api/v1/reports/headcount?companies=${company}&asOf=2026-10-01`,
        )
      ).status(),
    ).toBe(403);
    const deletes: { operation: string; body: unknown }[] = [];
    let dropDelete = true;
    await reader.route(`**/reports/saved-filters/${filterId}/delete`, async (route) => {
      deletes.push({
        operation: route.request().headers()["idempotency-key"] ?? "",
        body: route.request().postDataJSON(),
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      if (dropDelete) {
        dropDelete = false;
        return route.abort("failed");
      }
      return route.fulfill({ response });
    });
    await reader.getByRole("button", { name: "Delete filter", exact: true }).click();
    await reader
      .getByRole("dialog")
      .getByRole("button", { name: "Delete filter", exact: true })
      .click();
    await expect(reader.getByRole("status")).toContainText("The result is unconfirmed.");
    expect(
      (await readerContext.request.get(`${base}/reports/saved-filters/${filterId}`)).status(),
    ).toBe(404);
    await reader.getByRole("button", { name: "Recover original command", exact: true }).click();
    await expect(reader.getByRole("status")).toContainText("Filter deleted.");
    expect(deletes).toHaveLength(2);
    expect(deletes[1]).toEqual(deletes[0]);
    await reader.getByRole("link", { name: "Back to saved filters", exact: true }).click();
    await expect(
      reader.getByText("No saved filters in this company yet.", { exact: true }),
    ).toBeVisible();
    expect(
      (await (await readerContext.request.get(`${base}/reports/saved-filters`)).json()).items,
    ).toEqual([]);
    await reader.screenshot({ path: "../../.work/saved-report-filters-real.png", fullPage: true });
  } finally {
    await readerContext.close();
  }
});
