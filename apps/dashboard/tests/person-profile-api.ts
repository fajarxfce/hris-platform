import type { Page } from "@playwright/test";
import { companyIds, installIdentityApi } from "./identity-api";

const companies: readonly string[] = companyIds;

export const profileEmployeeIds = [
  "40000000-0000-4000-8000-000000000001",
  "40000000-0000-4000-8000-000000000002",
] as const;
export const profilePersonIds = [
  "50000000-0000-4000-8000-000000000001",
  "50000000-0000-4000-8000-000000000002",
] as const;
type Profile = {
  personId: string;
  ownerCompanyId: string;
  accountId: string | null;
  legalName: string;
  birthDate: string | null;
  nationality: string;
  email: string | null;
  version: number;
};
type Change = Pick<Profile, "legalName" | "birthDate" | "nationality" | "email"> & {
  expectedVersion: number;
  reason: string;
};
type Revision = Omit<Profile, "personId" | "ownerCompanyId" | "version"> & {
  revision: number;
  actorId: string | null;
  reason: string;
  recordedAt: string;
};
function gate() {
  let enter!: () => void;
  let release!: () => void;
  return {
    entered: new Promise<void>((resolve) => {
      enter = resolve;
    }),
    held: new Promise<void>((resolve) => {
      release = resolve;
    }),
    enter: () => enter(),
    release: () => release(),
  };
}

export async function installPersonProfileApi(
  page: Page,
  options: { permissions?: readonly string[]; longHistory?: boolean; foreignOwner?: boolean } = {},
) {
  const permissions = options.permissions ?? [
    "company.read",
    "people.read",
    "people.profile.read",
    "people.profile.manage",
  ];
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const profiles = companyIds.map(
    (company, index): Profile => ({
      personId: profilePersonIds[index] ?? "",
      ownerCompanyId: options.foreignOwner ? companyIds[1] : company,
      accountId: index === 0 ? "20000000-0000-4000-8000-000000000001" : null,
      legalName: index === 0 ? "Alya Pratama" : "Bayu Selatan",
      birthDate: "1995-06-07",
      nationality: "ID",
      email: "profile@example.invalid",
      version: options.longHistory ? 54 : 7,
    }),
  );
  const histories = profiles.map((profile) =>
    Array.from(
      { length: profile.version + 1 },
      (_, revision): Revision => ({
        legalName: revision === profile.version ? profile.legalName : `Previous name ${revision}`,
        birthDate: profile.birthDate,
        nationality: profile.nationality,
        email: profile.email,
        accountId: revision === 0 ? null : profile.accountId,
        actorId: revision === 0 ? null : "20000000-0000-4000-8000-000000000001",
        revision,
        reason: revision === 0 ? "Initial profile" : "Verified profile correction",
        recordedAt: "2026-01-01T00:00:00.000123Z",
      }),
    ),
  );
  const reads: URL[] = [];
  const writes: {
    company: string;
    employee: string;
    operation: string | undefined;
    csrf: string | undefined;
    body: Change;
  }[] = [];
  const receipts = new Map<string, { payload: string; receipt: { id: string; version: number } }>();
  let rejected: { code: string; fields: Record<string, string> } | null = null;
  let lost = false;
  let readFailure: { kind: "profile" | "history"; code: string } | null = null;
  let held: { kind: "profile" | "history" | "save"; gate: ReturnType<typeof gate> } | null = null;
  let commits = 0;
  await page.route("**/api/v1/companies/*/employees**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const match =
      /^\/api\/v1\/companies\/([^/]+)\/employees(?:\/([^/]+))?(?:\/(profile)(?:\/(history))?)?$/u.exec(
        url.pathname,
      );
    if (!match) return route.fallback();
    const company = match[1] ?? "";
    const employee = match[2];
    const index = companies.indexOf(company);
    const profile = profiles[index];
    const history = histories[index];
    if (!profile || !history || (employee && employee !== profileEmployeeIds[index]))
      return route.fulfill({ status: 404, json: { code: "person_profile_not_found" } });
    if (request.method() === "GET") {
      reads.push(url);
      const kind = match[4] ? "history" : match[3] ? "profile" : "public";
      const snapshot = structuredClone(profile);
      const historySnapshot = structuredClone(history);
      const waiting = held?.kind === kind ? held.gate : null;
      if (waiting) {
        held = null;
        waiting.enter();
        await waiting.held;
      }
      if (readFailure?.kind === kind)
        return route.fulfill({
          status: 403,
          json: { code: readFailure.code, detail: "PRIVATE TECHNICAL ERROR" },
        });
      if (kind === "profile") return route.fulfill({ json: snapshot });
      if (kind === "history") {
        const after = url.searchParams.has("after") ? Number(url.searchParams.get("after")) : -1;
        const remaining = historySnapshot.filter((item) => item.revision > after);
        const items = remaining.slice(0, 50);
        return route.fulfill({
          json: {
            items,
            nextCursor: remaining.length > 50 ? String(items.at(-1)?.revision) : null,
          },
        });
      }
      const publicRecord = {
        id: profileEmployeeIds[index],
        companyId: company,
        employeeNumber: index === 0 ? "EMP-001" : "EMP-002",
        person: { legalName: profile.legalName, email: profile.email },
        version: 3,
        appliedRevision: 0,
        terms: {
          effectiveFrom: "2026-01-01",
          startDate: "2026-01-01",
          endDate: null,
          contract: "PERMANENT",
          status: "ACTIVE",
        },
      };
      return route.fulfill({
        json: employee ? publicRecord : { items: [publicRecord], nextCursor: null },
      });
    }
    if (request.method() !== "PUT" || !match[3] || !employee) return route.fallback();
    const body = request.postDataJSON() as Change;
    const operation = request.headers()["idempotency-key"];
    writes.push({ company, employee, operation, csrf: request.headers()["x-csrf-token"], body });
    if (rejected) {
      const failure = rejected;
      rejected = null;
      return route.fulfill({
        status: 409,
        json: { ...failure, detail: "PRIVATE TECHNICAL ERROR" },
      });
    }
    const key = `${company}:${operation}`;
    const payload = JSON.stringify({ employee, body });
    let recorded = receipts.get(key);
    if (recorded && recorded.payload !== payload)
      return route.fulfill({ status: 409, json: { code: "operation_payload_mismatch" } });
    if (!recorded) {
      if (body.expectedVersion !== profile.version)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      const updated = {
        ...profile,
        legalName: body.legalName,
        birthDate: body.birthDate,
        nationality: body.nationality,
        email: body.email,
        version: profile.version + 1,
      };
      profiles[index] = updated;
      history.push({
        legalName: updated.legalName,
        birthDate: updated.birthDate,
        nationality: updated.nationality,
        email: updated.email,
        accountId: updated.accountId,
        revision: updated.version,
        actorId: "20000000-0000-4000-8000-000000000001",
        reason: body.reason,
        recordedAt: "2026-10-01T00:00:00.000123Z",
      });
      recorded = { payload, receipt: { id: updated.personId, version: updated.version } };
      receipts.set(key, recorded);
      commits += 1;
    }
    const waiting = held?.kind === "save" ? held.gate : null;
    if (waiting) {
      held = null;
      waiting.enter();
      await waiting.held;
    }
    if (lost) {
      lost = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: recorded.receipt });
  });
  return {
    identity,
    reads,
    writes,
    get commits() {
      return commits;
    },
    loseNextSave: () => {
      lost = true;
    },
    rejectNextSave: (code: string, fields: Record<string, string> = {}) => {
      rejected = { code, fields };
    },
    failReads: (kind: "profile" | "history", code: string | null) => {
      readFailure = code ? { kind, code } : null;
    },
    holdNext: (kind: "profile" | "history" | "save") => {
      const waiting = gate();
      held = { kind, gate: waiting };
      return waiting;
    },
    replaceProfile: (values: Partial<Profile>) => {
      const existing = profiles[0];
      if (existing) profiles[0] = { ...existing, ...values };
    },
  };
}
