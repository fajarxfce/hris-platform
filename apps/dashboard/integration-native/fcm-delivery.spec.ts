import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { existsSync, readFileSync, unlinkSync } from "node:fs";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import { createLeaveScenario } from "../integration/fixtures/leave-scenario";
import { pushFixtureJson, pushProxy } from "./fixtures/push-proxy";

const prefix = "../../.work/fcm-native";
const member = "a0000000-0000-4000-8000-000000000099";
const admin = "a0000000-0000-4000-8000-000000000098";
const app = "dev.fajar.hris.mobile.dev";
const adb = process.env.HRIS_TEST_ADB ?? "adb";

function device(...arguments_: string[]) {
  return execFileSync(adb, ["-s", "emulator-5560", ...arguments_], {
    encoding: "utf8",
    timeout: 10_000,
    maxBuffer: 2 * 1024 * 1024,
  });
}

function notification(inbox: string) {
  const dump = device("shell", "dumpsys", "notification", "--noredact");
  const active = dump.split("Notification List:")[1]?.split("Enqueued Notification List:")[0] ?? "";
  const records = active.split(/(?=NotificationRecord\()/u);
  const item = records.find((record) => {
    const header = record.split("\n")[0] ?? "";
    return header.includes(`pkg=${app} `) && header.includes(`tag=${inbox} `);
  });
  return {
    present: item !== undefined,
    genericTitle: item?.includes("android.title=String (HRIS)") ?? false,
    genericBody:
      item?.includes("Open your inbox to view an update.") === true ||
      item?.includes("Buka kotak masuk untuk melihat pembaruan.") === true,
  };
}

type Receipt = {
  inbox: string;
  version: number;
  readAt: string | null;
  acknowledgedAt: string | null;
  state: string;
  processed: number;
  accepted: number;
  rejected: number;
  failure: string | null;
};

test("real FCM reaches the owned Android app in foreground and background without implicit Inbox writes", async ({
  page,
  context,
}) => {
  for (const suffix of ["ready.json", "foreground-ready", "background-ready", "opened", "done"])
    if (existsSync(`${prefix}-${suffix}`)) throw new Error("Previous FCM fixture requires review");
  expect(device("emu", "avd", "name").trim().split("\n")[0]?.trim()).toBe("hris-mobile-dev");
  const proxy = await pushProxy(prefix, false);
  try {
    const { company, csrf } = await createAdminCompany(
      page,
      context,
      "browser-native-file-admin@example.invalid",
      "NATIVE_FCM",
      "FCM Delivery Acceptance",
    );
    expect(company).toMatch(/^[a-f0-9-]{36}$/u);
    const base = `/api/v1/companies/${company}`;
    const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
    expect(
      (
        await context.request.put(`${base}/members/${member}`, {
          headers: headers(),
          data: {
            active: true,
            permissions: ["company.read", "people.self.read", "announcements.read"],
            reason: "Owned real FCM acceptance",
          },
        })
      ).status(),
    ).toBe(200);
    const source = await createLeaveScenario(context, company, admin, { submit: false });
    expect(
      (
        await context.request.post(`${base}/employees/${source.employee}/account-link`, {
          headers: headers(),
          data: { accountId: member, expectedVersion: 0, reason: "Owned real FCM acceptance" },
        })
      ).status(),
    ).toBe(200);
    const owned = JSON.parse(
      readFileSync("../../.work/worker-integration/database.json", "utf8"),
    ) as {
      container: string;
      database: string;
    };
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
    const observe = <T>(sql: string): T | null => {
      const result = execFileSync(
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
        { input: sql, encoding: "utf8", timeout: 10_000, maxBuffer: 8192 },
      ).trim();
      return result ? (JSON.parse(result) as T) : null;
    };
    const registration = () =>
      observe<{ enabled: number; tokens: number; hashes: number; owned: number; total: number }>(
        `SELECT json_build_object('enabled',count(*) FILTER (WHERE enabled),'tokens',count(token_encrypted),'hashes',count(token_hash),'owned',count(*) FILTER (WHERE account_id='${member}'),'total',count(*)) FROM native_push_registrations;`,
      );
    pushFixtureJson(`${prefix}-ready.json`, {
      company,
      account: member,
      email: "browser-native-file-employee@example.invalid",
      proxyPort: 18082,
    });
    console.log("Owned Android real-FCM fixture ready");
    await expect
      .poll(() => existsSync(`${prefix}-foreground-ready`), { timeout: 240_000, intervals: [1000] })
      .toBe(true);
    await expect
      .poll(registration, { timeout: 60_000 })
      .toEqual({ enabled: 1, tokens: 1, hashes: 1, owned: 1, total: 1 });

    const receipts: Receipt[] = [];
    const notifications: { present: boolean; genericTitle: boolean; genericBody: boolean }[] = [];
    const announcements: string[] = [];
    const receipt = (id: string) =>
      observe<Receipt>(
        `SELECT json_build_object('inbox',i.id,'version',i.version,'readAt',i.read_at,'acknowledgedAt',i.acknowledged_at,'state',d.state,'processed',d.processed_count,'accepted',d.accepted_count,'rejected',d.rejected_count,'failure',d.failure_code) FROM inbox_items i JOIN inbox_push_dispatches d ON d.company_id=i.company_id AND d.inbox_id=i.id WHERE i.company_id='${company}' AND i.announcement_id='${id}' AND i.owner_account_id='${member}';`,
      );
    for (const mode of ["foreground", "background"] as const) {
      if (mode === "background")
        await expect
          .poll(() => existsSync(`${prefix}-background-ready`), {
            timeout: 180_000,
            intervals: [1000],
          })
          .toBe(true);
      const id = randomUUID();
      announcements.push(id);
      const path = `${base}/announcements/${id}`;
      const title = `FCM ${mode} acceptance`;
      expect(
        (
          await context.request.put(path, {
            headers: headers(),
            data: {
              title,
              body: "Fictional private content must remain inside the authorized Inbox.",
              audience: { kind: "COMPANY", targetIds: [] },
              acknowledgementRequired: true,
              reason: "Owned real provider acceptance",
            },
          })
        ).status(),
      ).toBe(200);
      expect(
        (
          await context.request.post(`${path}/publish`, {
            headers: headers(),
            data: { expectedVersion: 0, reason: "Owned real provider acceptance" },
          })
        ).status(),
      ).toBe(200);
      await expect
        .poll(() => receipt(id)?.state, { timeout: 120_000, intervals: [500, 1000] })
        .toBe("COMPLETE");
      const value = receipt(id);
      if (!value) throw new Error("Missing owned publication receipt");
      expect(value).toMatchObject({
        version: 0,
        readAt: null,
        acknowledgedAt: null,
        processed: 1,
        accepted: 1,
        rejected: 0,
        failure: null,
      });
      expect(value.inbox).toMatch(/^[a-f0-9-]{36}$/u);
      await expect
        // Normal-priority FCM can wait for a connection heartbeat. Cover the
        // real 300-second provider TTL and bounded native processing afterward.
        .poll(() => notification(value.inbox), { timeout: 350_000, intervals: [1000] })
        .toEqual({ present: true, genericTitle: true, genericBody: true });
      receipts.push(value);
      notifications.push(notification(value.inbox));
      pushFixtureJson(`${prefix}-${mode}-notified.json`, {
        company,
        account: member,
        inbox: value.inbox,
        title,
        providerAccepted: true,
        androidNotificationObserved: true,
      });
      console.log(`Actual FCM ${mode} notification observed on owned emulator`);
    }
    await expect
      .poll(() => existsSync(`${prefix}-opened`), { timeout: 180_000, intervals: [1000] })
      .toBe(true);
    for (const id of announcements)
      expect(receipt(id)).toMatchObject({
        version: 0,
        readAt: null,
        acknowledgedAt: null,
        state: "COMPLETE",
        accepted: 1,
      });
    await expect
      .poll(() => existsSync(`${prefix}-done`), { timeout: 180_000, intervals: [1000] })
      .toBe(true);
    await expect
      .poll(registration, { timeout: 30_000 })
      .toEqual({ enabled: 0, tokens: 0, hashes: 0, owned: 1, total: 1 });
    const state = proxy.snapshot();
    expect(state.interrupted).toBe(false);
    expect(state.count).toBeLessThanOrEqual(512);
    const companyWrites = state.events.filter(
      (event) =>
        event.forwarded && event.method !== "GET" && event.path.startsWith("/api/v1/companies/"),
    );
    expect(companyWrites).toEqual([]);
    pushFixtureJson(`${prefix}-server-result.json`, {
      realProvider: "FCM",
      project: process.env.HRIS_TEST_FCM_PROJECT_ID,
      ownedRegistrations: 1,
      providerAcceptedMessages: 2,
      androidNotifications: notifications,
      receipts,
      requests: state.count,
      nativeCompanyMutations: 0,
      retired: true,
    });
  } finally {
    await proxy.close();
    if (existsSync(`${prefix}-ready.json`)) unlinkSync(`${prefix}-ready.json`);
  }
});
