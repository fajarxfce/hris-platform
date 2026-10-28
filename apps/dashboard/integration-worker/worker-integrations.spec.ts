import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import { signInAndEnrollAuthenticator } from "../integration/fixtures/sign-in-and-enroll-authenticator";
import { workerIntegrationsDto } from "../src/features/administration/data/models/worker-integrations-dto";

test("actual worker reports advance and platform review handles retained stale processes and revocation", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-integration-admin@example.invalid",
    "WORKER_REPORT",
    "Worker Report Acceptance",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const admin = "a0000000-0000-4000-8000-000000000092";
  const member = "a0000000-0000-4000-8000-000000000093";
  const path = "/api/v1/administration/integrations/workers";
  const assigned = await context.request.put(`/api/v1/companies/${company}/members/${member}`, {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
    data: {
      active: true,
      permissions: ["company.read", "settings.manage", "identity.manage"],
      reason: "Independent worker-report company administrator",
    },
  });
  expect(assigned.status()).toBe(200);
  const read = async () => {
    const response = await context.request.get(path);
    expect(response.status()).toBe(200);
    return workerIntegrationsDto.parse(await response.json());
  };
  await expect
    .poll(async () => (await read()).workers.length, { timeout: 30_000, intervals: [500] })
    .toBe(1);
  const initial = (await read()).workers[0];
  if (!initial) throw new Error("Expected a real worker publication");
  expect(initial.status).toBe("RECENT");
  expect(initial.integrations.find((entry) => entry.kind === "MAIL")?.enabled).toBe(true);
  expect(initial.integrations.find((entry) => entry.kind === "PUSH")?.enabled).toBe(false);
  // Observe a later actual publication; the driver never changes the live worker row.
  await expect
    .poll(
      async () =>
        (await read()).workers.find((entry) => entry.instanceId === initial.instanceId)?.lastSeenAt,
      { timeout: 45_000, intervals: [1000] },
    )
    .not.toBe(initial.lastSeenAt);

  // A separate fictional retained process proves the stale-row UI against real SQL.
  // This is not a claim that the actual worker process was stopped.
  const owned = JSON.parse(
    await readFile("../../.work/worker-integration/database.json", "utf8"),
  ) as { container: string; database: string };
  if (!/^[0-9a-f]{12,64}$/u.test(owned.container) || owned.database !== "hris_browser")
    throw new Error("Unexpected owned fixture database");
  const label = execFileSync(
    "docker",
    ["inspect", "--format", '{{index .Config.Labels "dev.fajar.hris.fixture"}}', owned.container],
    { encoding: "utf8", timeout: 10_000, maxBuffer: 4096 },
  ).trim();
  expect(label).toBe("dashboard");
  const stale = randomUUID();
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
      input: `INSERT INTO worker_integration_observations(instance_id,first_seen_at,last_seen_at,mail_enabled,push_enabled,storage_enabled,scanner_enabled)
      VALUES('${stale}',now()-interval '3 minutes',now()-interval '2 minutes',false,false,false,false);`,
      encoding: "utf8",
      timeout: 10_000,
      maxBuffer: 4096,
    },
  );
  await page.goto("/platform/integrations");
  const table = page.getByRole("table", { name: "Worker reports", exact: true });
  await expect(table.getByRole("row")).toHaveCount(3);
  await expect(table.getByRole("row").filter({ hasText: initial.instanceId })).toContainText(
    "Recent report",
  );
  await expect(table.getByRole("row").filter({ hasText: stale })).toContainText("Stale report");
  await page.screenshot({ path: "../../.work/worker-reports-real.png", fullPage: true });

  if (!baseURL) throw new Error("Browser fixture URL required");
  const memberContext = await browser.newContext({ baseURL });
  const operatorContext = await browser.newContext({ baseURL });
  try {
    const memberPage = await memberContext.newPage();
    await signInAndEnrollAuthenticator(
      memberPage,
      "browser-integration-company-admin@example.invalid",
    );
    await memberPage.goto("/platform/integrations");
    await expect(memberPage.getByRole("alert")).toContainText("platform administration");
    await expect(
      memberPage.getByRole("region", { name: "Worker reports", exact: true }),
    ).toHaveCount(0);
    expect((await memberContext.request.get(path)).status()).toBe(403);
    const operator = await operatorContext.newPage();
    await signInAndEnrollAuthenticator(operator, "browser-integration-operator@example.invalid");
    const token = await (await operatorContext.request.get("/api/v1/auth/csrf")).json();
    const account = await operatorContext.request.get(`/api/v1/identity/accounts/${admin}`);
    expect(account.status()).toBe(200);
    const revoked = await operatorContext.request.put(`/api/v1/identity/accounts/${admin}/access`, {
      headers: { [token.headerName]: token.token, "Idempotency-Key": randomUUID() },
      data: {
        expectedVersion: (await account.json()).account.version,
        active: true,
        platformPermissions: ["companies.create"],
        reason: "Independent worker-review revocation",
      },
    });
    expect(revoked.status()).toBe(200);
    expect((await context.request.get(path)).status()).toBe(401);
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(table).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
    expect((await memberContext.request.get(path)).status()).toBe(403);
  } finally {
    await memberContext.close();
    await operatorContext.close();
  }
});
