import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { existsSync, readFileSync, unlinkSync } from "node:fs";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import { createLeaveScenario } from "../integration/fixtures/leave-scenario";
import { pushFixtureJson, pushProxy } from "./fixtures/push-proxy";

const prefix = "../../.work/push-native";

test("actual Android provider registration retains one command across response loss and offline restart", async ({
  page,
  context,
}) => {
  for (const suffix of ["ready.json", "recover", "done"])
    if (existsSync(`${prefix}-${suffix}`)) throw new Error("Previous push fixture requires review");
  const proxy = await pushProxy(prefix);
  try {
    const { company, csrf } = await createAdminCompany(
      page,
      context,
      "browser-native-file-admin@example.invalid",
      "PUSH_RECOVERY",
      "Push Registration Acceptance",
    );
    const member = "a0000000-0000-4000-8000-000000000099";
    const admin = "a0000000-0000-4000-8000-000000000098";
    const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
    expect(
      (
        await context.request.put(`/api/v1/companies/${company}/members/${member}`, {
          headers: headers(),
          data: {
            active: true,
            permissions: ["company.read", "people.self.read", "announcements.read"],
            reason: "Owned native push fixture",
          },
        })
      ).status(),
    ).toBe(200);
    const source = await createLeaveScenario(context, company, admin, { submit: false });
    expect(
      (
        await context.request.post(
          `/api/v1/companies/${company}/employees/${source.employee}/account-link`,
          {
            headers: headers(),
            data: { accountId: member, expectedVersion: 0, reason: "Owned native push fixture" },
          },
        )
      ).status(),
    ).toBe(200);
    const owned = JSON.parse(
      readFileSync("../../.work/worker-integration/database.json", "utf8"),
    ) as { container: string; database: string };
    if (!/^[0-9a-f]{12,64}$/u.test(owned.container) || owned.database !== "hris_browser")
      throw new Error("Unexpected owned database");
    expect(
      execFileSync(
        "docker",
        [
          "inspect",
          "--format",
          '{{index .Config.Labels "dev.fajar.hris.fixture"}}',
          owned.container,
        ],
        { encoding: "utf8", timeout: 10_000, maxBuffer: 4096 },
      ).trim(),
    ).toBe("dashboard");
    const registration = () => {
      const value = execFileSync(
        "docker",
        [
          "exec",
          "-i",
          owned.container,
          "psql",
          "-X",
          "-q",
          "-t",
          "-A",
          "-U",
          "fixture_migrator",
          "-d",
          owned.database,
          "-v",
          "ON_ERROR_STOP=1",
        ],
        {
          input: `SELECT json_build_object('version',version,'enabled',enabled,'platform',platform,'tokenRetained',token_encrypted IS NOT NULL,'tokenHashed',token_hash IS NOT NULL) FROM native_push_registrations WHERE account_id='${member}';`,
          encoding: "utf8",
          timeout: 10_000,
          maxBuffer: 4096,
        },
      ).trim();
      return value ? JSON.parse(value) : null;
    };
    pushFixtureJson(`${prefix}-ready.json`, {
      company,
      account: member,
      email: "browser-native-file-employee@example.invalid",
      proxyPort: 18082,
    });
    console.log("Owned Android push-registration fixture ready");
    await expect
      .poll(() => proxy.snapshot().interrupted, { timeout: 420_000, intervals: [1000] })
      .toBe(true);
    expect(registration()).toEqual({
      version: 0,
      enabled: true,
      platform: "ANDROID",
      tokenRetained: true,
      tokenHashed: true,
    });
    pushFixtureJson(`${prefix}-registered.json`, {
      committedBeforeResponseLoss: true,
      version: 0,
      tokenRetained: true,
    });
    await expect
      .poll(() => existsSync(`${prefix}-done`), { timeout: 420_000, intervals: [1000] })
      .toBe(true);
    const state = proxy.snapshot();
    expect(state.count).toBeLessThanOrEqual(512);
    const saves = state.mutations.filter((mutation) => mutation.method === "PUT");
    const disables = state.mutations.filter((mutation) => mutation.method === "POST");
    expect(saves).toHaveLength(2);
    expect(saves[0]).toEqual(saves[1]);
    expect(saves[0]?.status).toBe(200);
    expect(disables).toHaveLength(1);
    expect(disables[0]?.status).toBe(200);
    expect(registration()).toEqual({
      version: 1,
      enabled: false,
      platform: "ANDROID",
      tokenRetained: false,
      tokenHashed: false,
    });
    expect(
      state.events.filter(
        (event) =>
          event.forwarded && event.method !== "GET" && event.path.startsWith("/api/v1/companies/"),
      ),
    ).toHaveLength(0);
    pushFixtureJson(`${prefix}-server-result.json`, {
      responseDropped: true,
      sameOperationAndInput: true,
      registrations: 1,
      saves: 2,
      disable: 1,
      finalVersion: 1,
      tokenRemoved: true,
      requests: state.count,
      nativeCompanyMutations: 0,
    });
  } finally {
    await proxy.close();
    if (existsSync(`${prefix}-ready.json`)) unlinkSync(`${prefix}-ready.json`);
  }
});
