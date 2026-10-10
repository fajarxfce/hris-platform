import { expect, type Page } from "@playwright/test";
import type { ClientPolicyChangeDto } from "../src/features/administration/data/models/client-policy-change-dto";
import type { ClientPolicyRevisionDto } from "../src/features/administration/data/models/client-policy-revision-dto";
import type { ClientPolicySettingsDto } from "../src/features/administration/data/models/client-policy-settings-dto";
import { companyModules } from "../src/features/administration/domain/entities/company-module";
import { companyIds, installIdentityApi } from "./identity-api";

type Write = {
  company: string;
  operation: string;
  csrf: string | undefined;
  body: ClientPolicyChangeDto;
};
function gate() {
  let enter!: () => void;
  let release!: () => void;
  const entered = new Promise<void>((resolve) => {
    enter = resolve;
  });
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  return { entered, held, enter, release };
}
const seed = (version: number): ClientPolicyRevisionDto => ({
  version,
  activateAt: version === 0 ? "2026-10-01T00:00:00Z" : "2026-11-01T00:00:00.123456Z",
  disabledModules: version === 0 ? ["EXPENSES"] : ["PAYROLL"],
  minimumBuilds: { android: version === 0 ? 1 : 42, ios: 0, web: 0 },
  maintenance:
    version === 0
      ? null
      : { startsAt: "2026-11-01T01:00:00.123456Z", endsAt: "2026-11-01T01:30:00.123456Z" },
  recordedAt: "2026-10-01T00:00:00Z",
  actorId: "20000000-0000-4000-8000-000000000001",
  reason: `Existing policy ${version}`,
});

export async function installClientPolicyEditingApi(
  page: Page,
  options: { permissions?: string[]; empty?: boolean } = {},
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions: options.permissions ?? ["company.read", "settings.manage"],
  });
  const records = new Map<string, ClientPolicyRevisionDto[]>(
    companyIds.map((company) => [company, options.empty ? [] : [seed(0), seed(1)]]),
  );
  const reads: URL[] = [];
  const writes: Write[] = [];
  const receipts = new Map<string, { body: string; receipt: { id: string; version: number } }>();
  let now = Date.parse("2026-10-10T00:00:00Z");
  let commits = 0;
  let lose = false;
  let readFailure: string | null = null;
  let nextFailure: { code: string; status: number; fields: Record<string, string> } | null = null;
  let readGate: ReturnType<typeof gate> | null = null;
  let writeGate: ReturnType<typeof gate> | null = null;

  function settings(company: string): ClientPolicySettingsDto {
    const revisions = records.get(company) ?? [];
    const current = revisions.findLast((item) => Date.parse(item.activateAt) <= now) ?? null;
    const maintenance = current?.maintenance ?? null;
    const boundaries = revisions.map((item) => Date.parse(item.activateAt));
    if (maintenance)
      boundaries.push(Date.parse(maintenance.startsAt), Date.parse(maintenance.endsAt));
    return {
      latest: revisions.at(-1) ?? null,
      effective: {
        schemaVersion: 1,
        version: current?.version ?? null,
        enabledModules: companyModules.filter(
          (module) => !current?.disabledModules.includes(module),
        ),
        minimumBuilds: current?.minimumBuilds ?? { android: 0, ios: 0, web: 0 },
        maintenance,
        maintenanceActive:
          maintenance !== null &&
          Date.parse(maintenance.startsAt) <= now &&
          now < Date.parse(maintenance.endsAt),
        serverTime: new Date(now).toISOString(),
        validUntil: new Date(
          Math.min(now + 60_000, ...boundaries.filter((at) => at > now)),
        ).toISOString(),
      },
    };
  }
  await page.route("**/api/v1/companies/*/settings/client-policy{,/**}", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const company = url.pathname.split("/")[4] ?? "";
    expect(records.has(company)).toBe(true);
    expect(request.headers()["x-hris-client-platform"]).toBe("WEB");
    const failure = (code: string, status = 409, fields: Record<string, string> = {}) =>
      route.fulfill({
        status,
        json: { code, fields, parameters: {}, detail: "PRIVATE POLICY DETAILS" },
      });
    if (request.method() === "GET") {
      reads.push(url);
      const version = url.pathname.split("/revisions/")[1];
      const result = structuredClone(
        version === undefined
          ? settings(company)
          : records.get(company)?.find((item) => item.version === Number(version)),
      );
      const pending = readGate;
      readGate = null;
      if (pending) {
        pending.enter();
        await pending.held;
      }
      if (readFailure) return failure(readFailure, 403);
      return result
        ? route.fulfill({ json: result })
        : failure("client_policy_revision_not_found", 404);
    }
    expect(request.method()).toBe("PUT");
    const body = request.postDataJSON() as ClientPolicyChangeDto;
    const operation = request.headers()["idempotency-key"] ?? "";
    const csrf = request.headers()["x-csrf-token"];
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    expect(csrf).toBeTruthy();
    writes.push({ company, operation, csrf, body });
    const pending = writeGate;
    writeGate = null;
    if (pending) {
      pending.enter();
      await pending.held;
    }
    if (nextFailure) {
      const rejected = nextFailure;
      nextFailure = null;
      return failure(rejected.code, rejected.status, rejected.fields);
    }
    const key = `${company}:${operation}`;
    const replay = receipts.get(key);
    if (replay) {
      expect(JSON.stringify(body)).toBe(replay.body);
      return route.fulfill({ json: replay.receipt });
    }
    const revisions = records.get(company) ?? [];
    const head = revisions.at(-1);
    if (body.expectedVersion !== (head?.version ?? null)) return failure("stale_version");
    if (body.activateAt !== null && Date.parse(body.activateAt) < now)
      return failure("client_policy_activation_expired");
    const version = (head?.version ?? -1) + 1;
    const at = new Date(now).toISOString();
    revisions.push({
      version,
      activateAt: body.activateAt ?? at,
      disabledModules: [...body.disabledModules],
      minimumBuilds: body.minimumBuilds,
      maintenance: body.maintenance,
      reason: body.reason,
      recordedAt: at,
      actorId: "20000000-0000-4000-8000-000000000001",
    });
    const receipt = { id: company, version };
    receipts.set(key, { body: JSON.stringify(body), receipt });
    commits++;
    if (lose) {
      lose = false;
      return route.abort("connectionfailed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    identity,
    reads,
    writes,
    get commits() {
      return commits;
    },
    loseNext: () => {
      lose = true;
    },
    failReads: (code: string | null) => {
      readFailure = code;
    },
    rejectNext: (code: string, status = 409, fields: Record<string, string> = {}) => {
      nextFailure = { code, status, fields };
    },
    holdNextRead: () => {
      const pending = gate();
      readGate = pending;
      return pending;
    },
    holdNextWrite: () => {
      const pending = gate();
      writeGate = pending;
      return pending;
    },
    setNow: (value: string) => {
      now = Date.parse(value);
    },
    advanceHead: (version?: number) => {
      const revisions = records.get(companyIds[0] ?? "") ?? [];
      revisions.push({
        ...seed(version ?? (revisions.at(-1)?.version ?? -1) + 1),
        activateAt: new Date(now).toISOString(),
        minimumBuilds: { android: 77, ios: 0, web: 0 },
      });
    },
  };
}
