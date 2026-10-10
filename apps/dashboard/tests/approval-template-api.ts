import { expect, type Page } from "@playwright/test";
import type {
  ApprovalTemplateChangeDto,
  ApprovalTemplateDto,
} from "../src/features/approvals/data/models/approval-template-dto";
import { companyIds, installIdentityApi } from "./identity-api";

export const templateId = (company = 0, index = 1) =>
  `50000000-abcd-4000-8000-${String(company * 100 + index).padStart(12, "0")}`;
export const approverId = (company = 0, index = 1) =>
  `20000000-abcd-4000-8000-${String(company * 100 + index).padStart(12, "0")}`;
function gate() {
  let enter!: () => void;
  let release!: () => void;
  const entered = new Promise<void>((done) => {
    enter = done;
  });
  const held = new Promise<void>((done) => {
    release = done;
  });
  return { entered, held, enter, release };
}
function seed(company: number, index: number, revision: number): ApprovalTemplateDto {
  return {
    id: templateId(company, index),
    name: `${company === 0 ? "North" : "South"} approval ${index}`,
    kind: "EXPENSE",
    active: true,
    version: 2,
    appliedRevision: revision,
    effectiveFrom: `2026-0${revision + 1}-01`,
    category: "TRAVEL",
    minimumAmount: revision === 2 ? "1234567890123456.78" : "0.00",
    stages: [
      { assignment: "NAMED", accountIds: [approverId(company)], permission: null },
      { assignment: "PERMISSION", accountIds: [], permission: "expenses.approve" },
    ],
  };
}
export async function installApprovalTemplateApi(page: Page, permissions = ["approvals.manage"]) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const records = new Map<string, ApprovalTemplateDto[]>(
    companyIds.map((company, index) => [
      company,
      Array.from({ length: index === 0 ? 22 : 1 }, (_, n) => seed(index, n + 1, 2)),
    ]),
  );
  const history = new Map<string, ApprovalTemplateDto[]>(
    companyIds.flatMap((company, c) =>
      (records.get(company) ?? []).map(
        (item, i) =>
          [`${company}:${item.id}`, [seed(c, i + 1, 0), seed(c, i + 1, 1), item]] as const,
      ),
    ),
  );
  const reads: URL[] = [];
  const writes: {
    company: string;
    id: string;
    operation: string;
    body: ApprovalTemplateChangeDto;
  }[] = [];
  const receipts = new Map<
    string,
    { id: string; body: string; receipt: { id: string; version: number } }
  >();
  let commits = 0;
  let lose = false;
  let nextFailure: { code: string; status: number } | null = null;
  let readFailure: string | null = null;
  let pending: {
    kind: "template" | "templates" | "assignees" | "write";
    gate: ReturnType<typeof gate>;
  } | null = null;
  await page.route("**/api/v1/companies/*/approvals/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const parts = url.pathname.split("/");
    const company = parts[4] ?? "";
    const resource = parts[6];
    const id = parts[7];
    expect(records.has(company)).toBe(true);
    const fail = (code: string, status = 409) =>
      route.fulfill({
        status,
        json: { code, fields: {}, parameters: {}, detail: "PRIVATE APPROVAL DATA" },
      });
    const kind =
      request.method() !== "GET"
        ? "write"
        : resource === "assignees"
          ? "assignees"
          : id
            ? "template"
            : "templates";
    if (request.method() === "GET") reads.push(url);
    const hold = pending?.kind === kind ? pending.gate : null;
    if (hold) {
      pending = null;
      hold.enter();
      await hold.held;
    }
    if (request.method() === "GET") {
      if (readFailure) return fail(readFailure, 403);
      if (resource === "assignees") {
        expect(url.searchParams.get("limit")).toBe("10");
        const companyIndex = (companyIds as readonly string[]).indexOf(company);
        const all = Array.from({ length: 12 }, (_, index) => ({
          id: approverId(companyIndex, index + 1),
          displayName:
            index === 11 ? "Scope_100%" : `Approver ${String(index + 1).padStart(2, "0")}`,
        }));
        const after = url.searchParams.get("after");
        const query = url.searchParams.get("query")?.toLowerCase() ?? "";
        const matching = all.filter(
          (item) => (!after || item.id > after) && item.displayName.toLowerCase().includes(query),
        );
        return route.fulfill({
          json: {
            items: matching.slice(0, 10),
            nextCursor: matching.length > 10 ? matching[9]?.id : null,
          },
        });
      }
      expect(resource).toBe("templates");
      const all = records.get(company) ?? [];
      if (id) {
        const current = all.find((item) => item.id === id);
        const revision = url.searchParams.get("revision");
        const rules =
          revision === null
            ? current
            : history
                .get(`${company}:${id}`)
                ?.find((item) => item.appliedRevision === Number(revision));
        return current && rules
          ? route.fulfill({
              json: {
                ...rules,
                name: current.name,
                active: current.active,
                version: current.version,
              },
            })
          : fail("approval_template_not_found", 404);
      }
      expect(url.searchParams.get("limit")).toBe("20");
      const after = url.searchParams.get("after");
      const asOf = url.searchParams.get("asOf") ?? "";
      const matching = all
        .filter((item) => item.kind === url.searchParams.get("kind") && (!after || item.id > after))
        .flatMap((item) => {
          const rules = history
            .get(`${company}:${item.id}`)
            ?.filter((entry) => entry.effectiveFrom <= asOf)
            .at(-1);
          return rules
            ? [{ ...rules, name: item.name, active: item.active, version: item.version }]
            : [];
        });
      return route.fulfill({
        json: {
          items: matching.slice(0, 20),
          nextCursor: matching.length > 20 ? matching[19]?.id : null,
        },
      });
    }
    expect(request.method()).toBe("PUT");
    expect(resource).toBe("templates");
    expect(id).toBeTruthy();
    const body = request.postDataJSON() as ApprovalTemplateChangeDto;
    const operation = request.headers()["idempotency-key"] ?? "";
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    expect(request.headers()["x-csrf-token"]).toBeTruthy();
    writes.push({ company, id: id ?? "", operation, body });
    if (nextFailure) {
      const value = nextFailure;
      nextFailure = null;
      return fail(value.code, value.status);
    }
    const key = `${company}:${operation}`;
    const replay = receipts.get(key);
    if (replay) {
      expect(id).toBe(replay.id);
      expect(JSON.stringify(body)).toBe(replay.body);
      return route.fulfill({ json: replay.receipt });
    }
    const all = records.get(company) ?? [];
    const previous = all.find((item) => item.id === id);
    if (body.expectedVersion !== (previous?.version ?? null)) return fail("stale_version");
    if (previous && body.kind !== previous.kind) return fail("approval_kind_immutable", 422);
    const version = (previous?.version ?? -1) + 1;
    const { reason: _reason, expectedVersion: _expected, ...fields } = body;
    const updated: ApprovalTemplateDto = {
      ...fields,
      id: id ?? "",
      version,
      appliedRevision: version,
    };
    records.set(
      company,
      [...all.filter((item) => item.id !== id), updated].sort((a, b) => a.id.localeCompare(b.id)),
    );
    history.set(`${company}:${id}`, [...(history.get(`${company}:${id}`) ?? []), updated]);
    const receipt = { id: id ?? "", version };
    receipts.set(key, { id: id ?? "", body: JSON.stringify(body), receipt });
    commits++;
    if (lose) {
      lose = false;
      return route.abort("connectionfailed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    identity,
    records,
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
    hold: (kind: "template" | "templates" | "assignees" | "write") => {
      const value = gate();
      pending = { kind, gate: value };
      return value;
    },
    advance: () => {
      const current = records.get(companyIds[0])?.[0];
      if (current) {
        const updated = {
          ...current,
          version: current.version + 1,
          appliedRevision: current.appliedRevision + 1,
          name: "Concurrent approval",
        };
        records.set(companyIds[0], [updated, ...(records.get(companyIds[0]) ?? []).slice(1)]);
        history.get(`${companyIds[0]}:${current.id}`)?.push(structuredClone(updated));
      }
    },
  };
}
