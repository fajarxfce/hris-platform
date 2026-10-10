import type { Page } from "@playwright/test";
import { companyIds, installIdentityApi } from "./identity-api";

export const officeId = "80000000-0000-4000-8000-000000000001";
export const departmentId = "80000000-0000-4000-8000-000000000002";
type Unit = {
  id: string;
  code: string;
  name: string;
  kind: string;
  parentId: string | null;
  timezone: string | null;
  active: boolean;
  version: number;
};
type Change = Omit<Unit, "id" | "version"> & { expectedVersion: number | null };
function gate() {
  let release!: () => void;
  let entered!: () => void;
  return {
    held: new Promise<void>((done) => {
      release = done;
    }),
    started: new Promise<void>((done) => {
      entered = done;
    }),
    release: () => release(),
    enter: () => entered(),
  };
}
export async function installOrganizationEditorApi(
  page: Page,
  options: { permissions?: readonly string[]; manyParents?: boolean } = {},
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions: options.permissions ?? ["company.read", "company.manage"],
  });
  const records = new Map<string, Unit>();
  for (const company of companyIds) {
    records.set(`${company}:${officeId}`, {
      id: officeId,
      code: "HQ",
      name: company === companyIds[0] ? "North office" : "South office",
      kind: "BRANCH",
      parentId: null,
      timezone: "Asia/Jakarta",
      active: true,
      version: 4,
    });
    records.set(`${company}:${departmentId}`, {
      id: departmentId,
      code: "SALES",
      name: "Sales",
      kind: "DEPARTMENT",
      parentId: officeId,
      timezone: null,
      active: true,
      version: 7,
    });
    if (options.manyParents)
      for (let index = 0; index < 55; index += 1) {
        const id = `81000000-0000-4000-8000-${String(index).padStart(12, "0")}`;
        records.set(`${company}:${id}`, {
          id,
          code: `P${String(index).padStart(3, "0")}`,
          name: `Parent ${index}`,
          kind: "BRANCH",
          parentId: null,
          timezone: "Asia/Jakarta",
          active: true,
          version: 0,
        });
      }
  }
  const writes: {
    company: string;
    id: string;
    operation: string | undefined;
    csrf: string | undefined;
    body: Change;
  }[] = [];
  const reads: URL[] = [];
  const receipts = new Map<string, { payload: string; receipt: { id: string; version: number } }>();
  let rejected: string | null = null;
  let lost = false;
  let held: ReturnType<typeof gate> | null = null;
  let detailFailure: string | null = null;
  let commits = 0;
  await page.route("**/api/v1/companies/*/organization-units**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const company = url.pathname.split("/")[4] ?? "";
    const id = url.pathname.split("/")[6];
    if (request.method() === "PUT" && id) {
      const body = request.postDataJSON() as Change;
      const operation = request.headers()["idempotency-key"];
      writes.push({ company, id, operation, csrf: request.headers()["x-csrf-token"], body });
      const waiting = held;
      held = null;
      if (waiting) {
        waiting.enter();
        await waiting.held;
      }
      if (rejected) {
        const code = rejected;
        rejected = null;
        return route.fulfill({ status: 409, json: { code, detail: "PRIVATE TECHNICAL ERROR" } });
      }
      const key = `${company}:${operation}`;
      const payload = JSON.stringify({ id, body });
      const previous = receipts.get(key);
      if (previous)
        return route.fulfill(
          previous.payload === payload
            ? { json: previous.receipt }
            : { status: 409, json: { code: "operation_payload_mismatch" } },
        );
      const current = records.get(`${company}:${id}`);
      if ((current?.version ?? null) !== body.expectedVersion)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      const { expectedVersion, ...fields } = body;
      const version = expectedVersion === null ? 0 : expectedVersion + 1;
      records.set(`${company}:${id}`, { ...fields, id, version });
      const receipt = { id, version };
      receipts.set(key, { payload, receipt });
      commits += 1;
      if (lost) {
        lost = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: receipt });
    }
    reads.push(url);
    if (id) {
      if (detailFailure) {
        const code = detailFailure;
        return route.fulfill({ status: 503, json: { code } });
      }
      const unit = records.get(`${company}:${id}`);
      return unit
        ? route.fulfill({
            json: {
              companyId: company,
              unit,
              parent: unit.parentId ? (records.get(`${company}:${unit.parentId}`) ?? null) : null,
            },
          })
        : route.fulfill({ status: 404, json: { code: "organization_unit_not_found" } });
    }
    const query = url.searchParams;
    const key = (unit: Unit) => `${unit.kind}:${unit.code}`;
    const units = [...records.entries()]
      .filter(([scope]) => scope.startsWith(`${company}:`))
      .map(([, unit]) => unit)
      .filter(
        (unit) =>
          (!query.has("kind") || unit.kind === query.get("kind")) &&
          (!query.has("active") || String(unit.active) === query.get("active")) &&
          `${unit.name} ${unit.code}`
            .toLowerCase()
            .includes((query.get("query") ?? "").toLowerCase()) &&
          (!query.has("after") || key(unit) > (query.get("after") ?? "")),
      )
      .sort((a, b) => (key(a) < key(b) ? -1 : 1));
    const items = units.slice(0, 50);
    return route.fulfill({
      json: { items, nextCursor: units.length > 50 ? key(items[49] as Unit) : null },
    });
  });
  return {
    identity,
    writes,
    reads,
    get commits() {
      return commits;
    },
    rejectNextSave: (code: string) => {
      rejected = code;
    },
    loseNextSave: () => {
      lost = true;
    },
    failDetails: (code: string | null) => {
      detailFailure = code;
    },
    holdSave: () => {
      const waiting = gate();
      held = waiting;
      return waiting;
    },
    replaceDepartment: (name: string, version: number) => {
      const current = records.get(`${companyIds[0]}:${departmentId}`);
      if (current) records.set(`${companyIds[0]}:${departmentId}`, { ...current, name, version });
    },
  };
}
