import { expect, type Page } from "@playwright/test";
import type { LifecycleTemplateChangeDto } from "../src/features/lifecycle/data/models/lifecycle-template-change-dto";
import type { LifecycleTemplateDto } from "../src/features/lifecycle/data/models/lifecycle-template-dto";
import { companyIds, installIdentityApi } from "./identity-api";

export const lifecycleTemplateId = (company = 0, n = 1) =>
  `90000000-abcd-4000-8000-${company + 1}${String(n).padStart(11, "0")}`;
function seed(company: number, n: number): LifecycleTemplateDto {
  return {
    id: lifecycleTemplateId(company, n),
    code: `TEMPLATE_${String(n).padStart(3, "0")}`,
    name: `${company === 0 ? "North" : "South"} checklist ${n}`,
    kind: n % 2 === 0 ? "OFFBOARDING" : "ONBOARDING",
    active: n % 2 !== 0,
    version: 2,
    tasks: [
      { key: "equipment", title: "Review equipment", required: true, dueDays: -2 },
      { key: "welcome", title: "Welcome session", required: false, dueDays: 1 },
    ],
  };
}
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
export async function installLifecycleApi(
  page: Page,
  options: { permissions?: string[]; empty?: boolean } = {},
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions: options.permissions ?? ["people.lifecycle.read", "people.lifecycle.manage"],
  });
  const records = new Map<string, LifecycleTemplateDto[]>(
    companyIds.map((company, index) => [
      company,
      options.empty ? [] : Array.from({ length: 21 }, (_, n) => seed(index, n + 1)),
    ]),
  );
  const reads: URL[] = [];
  const writes: {
    company: string;
    id: string;
    operation: string;
    csrf: string | undefined;
    body: LifecycleTemplateChangeDto;
  }[] = [];
  const receipts = new Map<
    string,
    { body: string; id: string; receipt: { id: string; version: number } }
  >();
  let commits = 0;
  let lose = false;
  let readFailure: string | null = null;
  let nextFailure: { code: string; status: number } | null = null;
  let readGate: ReturnType<typeof gate> | null = null;
  let writeGate: ReturnType<typeof gate> | null = null;
  await page.route("**/api/v1/companies/*/lifecycle/templates{,?*,/**}", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const company = url.pathname.split("/")[4] ?? "";
    const id = url.pathname.split("/")[7];
    expect(records.has(company)).toBe(true);
    expect(request.headers()["x-hris-client-platform"]).toBe("WEB");
    const failure = (code: string, status = 409) =>
      route.fulfill({
        status,
        json: { code, fields: {}, parameters: {}, detail: "PRIVATE LIFECYCLE DETAILS" },
      });
    if (request.method() === "GET") {
      reads.push(url);
      const source = records.get(company) ?? [];
      const after = url.searchParams.get("after");
      const items = source.filter((item) => after === null || item.code > after);
      const value = structuredClone(
        id
          ? source.find((item) => item.id === id)
          : {
              items: items.slice(0, 20),
              nextCursor: items.length > 20 ? items[19]?.code : null,
            },
      );
      if (!id) expect(url.searchParams.get("limit")).toBe("20");
      const pending = readGate;
      readGate = null;
      if (pending) {
        pending.enter();
        await pending.held;
      }
      if (readFailure) return failure(readFailure, 403);
      return value ? route.fulfill({ json: value }) : failure("lifecycle_template_not_found", 404);
    }
    expect(request.method()).toBe("PUT");
    expect(id).toBeTruthy();
    const body = request.postDataJSON() as LifecycleTemplateChangeDto;
    const operation = request.headers()["idempotency-key"] ?? "";
    const csrf = request.headers()["x-csrf-token"];
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    expect(csrf).toBeTruthy();
    writes.push({ company, id: id ?? "", operation, csrf, body });
    const pending = writeGate;
    writeGate = null;
    if (pending) {
      pending.enter();
      await pending.held;
    }
    if (nextFailure) {
      const rejected = nextFailure;
      nextFailure = null;
      return failure(rejected.code, rejected.status);
    }
    const key = `${company}:${operation}`;
    const replay = receipts.get(key);
    if (replay) {
      expect(JSON.stringify(body)).toBe(replay.body);
      expect(id).toBe(replay.id);
      return route.fulfill({ json: replay.receipt });
    }
    const items = records.get(company) ?? [];
    const previous = items.find((item) => item.id === id);
    if (body.expectedVersion !== (previous?.version ?? null)) return failure("stale_version");
    if (previous && (body.code !== previous.code || body.kind !== previous.kind))
      return failure("lifecycle_template_identity_immutable", 422);
    const version = (previous?.version ?? -1) + 1;
    const updated: LifecycleTemplateDto = {
      id: id ?? "",
      code: body.code,
      name: body.name,
      kind: body.kind,
      active: body.active,
      version,
      tasks: body.tasks.map((task) => ({ ...task })),
    };
    records.set(
      company,
      [...items.filter((item) => item.id !== id), updated].sort((a, b) =>
        a.code.localeCompare(b.code),
      ),
    );
    const receipt = { id: id ?? "", version };
    receipts.set(key, { body: JSON.stringify(body), id: id ?? "", receipt });
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
    rejectNext: (code: string, status = 409) => {
      nextFailure = { code, status };
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
    advance: () => {
      const first = records.get(companyIds[0])?.[0];
      if (first) {
        first.version++;
        first.name = "Concurrent template";
      }
    },
  };
}
