import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import { signInAndEnrollAuthenticator } from "../integration/fixtures/sign-in-and-enroll-authenticator";

test("independent global audit grant, metadata pagination and revocation use real API and RLS", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-audit-operator@example.invalid",
    "PLATFORM_AUDIT",
    "Platform Audit Acceptance",
  );
  const auditor = "a0000000-0000-4000-8000-000000000095";
  const operator = "a0000000-0000-4000-8000-000000000096";
  const member = "a0000000-0000-4000-8000-000000000097";
  const path = "/api/v1/administration/audit-events";
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const grant = async (permissions: string[]) => {
    const review = await context.request.get(`/api/v1/identity/accounts/${auditor}`);
    expect(review.status()).toBe(200);
    const data = await review.json();
    expect(data.assignablePermissions).toContain("audit.read");
    const result = await context.request.put(`/api/v1/identity/accounts/${auditor}/access`, {
      headers: headers(),
      data: {
        expectedVersion: data.account.version,
        active: true,
        platformPermissions: permissions,
        reason: "PRIVATE_AUDIT_ACCEPTANCE_REASON",
      },
    });
    expect(result.status()).toBe(200);
  };
  await grant(["audit.read"]);
  const membership = await context.request.put(`/api/v1/companies/${company}/members/${member}`, {
    headers: headers(),
    data: {
      active: true,
      permissions: ["company.read", "audit.read"],
      reason: "Company auditor fixture",
    },
  });
  expect(membership.status()).toBe(200);
  const owned = JSON.parse(
    await readFile("../../.work/worker-integration/database.json", "utf8"),
  ) as { container: string; database: string };
  if (!/^[0-9a-f]{12,64}$/u.test(owned.container) || owned.database !== "hris_browser")
    throw new Error("Unexpected owned database");
  expect(
    execFileSync(
      "docker",
      ["inspect", "--format", '{{index .Config.Labels "dev.fajar.hris.fixture"}}', owned.container],
      { encoding: "utf8", timeout: 10_000, maxBuffer: 4096 },
    ).trim(),
  ).toBe("dashboard");
  // Fictional bounded journal corpus; normal capture triggers remain active.
  execFileSync(
    "docker",
    [
      "exec",
      "-i",
      owned.container,
      "psql",
      "-X",
      "-q",
      "-U",
      "fixture_migrator",
      "-d",
      owned.database,
      "-v",
      "ON_ERROR_STOP=1",
    ],
    {
      input: `INSERT INTO audit_entries(id,actor_id,resource_type,resource_id,action,reason,details,correlation_id,created_at)
      SELECT gen_random_uuid(),'${operator}','account',gen_random_uuid(),'fixture.platform_audit','PRIVATE_AUDIT_ACCEPTANCE_REASON','{"private":"PRIVATE_AUDIT_PAYLOAD"}',gen_random_uuid(),now()-interval '1 minute' FROM generate_series(1,52);
      INSERT INTO audit_entries(id,company_id,actor_id,resource_type,resource_id,action,correlation_id)
      VALUES(gen_random_uuid(),'${company}','${operator}','account',gen_random_uuid(),'fixture.platform_audit',gen_random_uuid());`,
      encoding: "utf8",
      timeout: 10_000,
      maxBuffer: 4096,
    },
  );
  if (!baseURL) throw new Error("Owned browser URL required");
  const auditorContext = await browser.newContext({ baseURL });
  const memberContext = await browser.newContext({ baseURL });
  try {
    const auditorPage = await auditorContext.newPage();
    await signInAndEnrollAuthenticator(auditorPage, "browser-platform-auditor@example.invalid");
    const recorded = await auditorContext.request.get(
      `${path}?resourceId=${auditor}&action=identity.account_access_saved`,
    );
    expect(recorded.status()).toBe(200);
    const journal = await recorded.json();
    expect(journal.items).toHaveLength(1);
    expect(journal.items[0].actorId).toBe(operator);
    expect(JSON.stringify(journal)).not.toContain("PRIVATE_");
    await auditorPage.goto("/platform/audit?action=fixture.platform_audit");
    const table = auditorPage.getByRole("table", { name: "Events", exact: true });
    await expect(table.getByRole("row")).toHaveCount(51);
    await table
      .getByRole("button", { name: /^View details:/u })
      .first()
      .click();
    await expect(auditorPage.getByText(operator, { exact: true })).toBeVisible();
    await expect(auditorPage.getByText("Company ID", { exact: true })).toHaveCount(0);
    await auditorPage.getByRole("button", { name: "Close", exact: true }).click();
    await auditorPage.getByRole("button", { name: "Older events", exact: true }).click();
    await expect(table.getByRole("row")).toHaveCount(3);
    await auditorPage.screenshot({ path: "../../.work/platform-audit-real.png", fullPage: true });
    const companyPage = await memberContext.newPage();
    await signInAndEnrollAuthenticator(companyPage, "browser-company-auditor@example.invalid");
    await companyPage.goto("/platform/audit");
    await expect(companyPage.getByRole("alert")).toBeVisible();
    expect((await memberContext.request.get(path)).status()).toBe(403);
    expect(
      (await memberContext.request.get(`/api/v1/companies/${company}/audit-events`)).status(),
    ).toBe(200);
    await grant([]);
    expect((await auditorContext.request.get(path)).status()).toBe(401);
    await auditorPage.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(table).toHaveCount(0);
    await expect(auditorPage.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
    expect((await memberContext.request.get(path)).status()).toBe(403);
  } finally {
    await auditorContext.close();
    await memberContext.close();
  }
});
