import { expect, type Page } from "@playwright/test";
import type {
  ApprovalDelegationChangeDto,
  ApprovalDelegationDto,
} from "../src/features/approvals/data/models/approval-delegation-dto";
import { companyIds, installIdentityApi } from "./identity-api";

export const delegationActor = "20000000-0000-4000-8000-000000000001";
export const delegationId = (company = 0, index = 1) =>
  `90000000-abcd-4000-8000-${String(company * 100 + index).padStart(12, "0")}`;
export const delegateId = (company = 0, index = 1) =>
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
export async function installApprovalDelegationApi(
  page: Page,
  options: { permissions?: string[]; timezone?: string } = {},
) {
  const permissions = options.permissions ?? [
    "approvals.read",
    "approvals.manage",
    "leave.approve",
    "expenses.approve",
  ];
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
    timezone: options.timezone ?? "Asia/Jakarta",
  });
  const records = new Map<string, ApprovalDelegationDto[]>(
    companyIds.map((company, c) => [
      company,
      Array.from({ length: c === 0 ? 23 : 1 }, (_, index) => ({
        id: delegationId(c, index + 1),
        kind: "LEAVE",
        fromAccount: index === 1 ? delegateId(c) : delegationActor,
        toAccount: index === 1 ? delegationActor : delegateId(c),
        validFrom: index === 22 ? "2020-10-01T00:00:00Z" : "2030-10-01T00:00:00.123456Z",
        validUntil: index === 22 ? "2020-10-10T00:00:00Z" : "2030-10-10T00:00:00.123456Z",
        active: index !== 2 && index !== 22,
        version: 2,
      })),
    ]),
  );
  const reads: URL[] = [];
  const writes: {
    company: string;
    id: string;
    operation: string;
    body: ApprovalDelegationChangeDto;
  }[] = [];
  const receipts = new Map<string, { body: string; id: string; version: number }>();
  let commits = 0;
  let lose = false;
  let failure: { code: string; status: number } | null = null;
  let pending: {
    kind: "detail" | "list" | "assignees" | "save";
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
        ? "save"
        : resource === "assignees"
          ? "assignees"
          : id
            ? "detail"
            : "list";
    if (kind !== "save") reads.push(url);
    const held = pending?.kind === kind ? pending.gate : null;
    if (held) {
      pending = null;
      held.enter();
      await held.held;
    }
    if (resource === "assignees") {
      expect(url.searchParams.get("limit")).toBe("10");
      const c = (companyIds as readonly string[]).indexOf(company);
      const accounts = [
        { id: delegationActor, displayName: "Sample Reviewer" },
        ...Array.from({ length: 12 }, (_, i) => ({
          id: delegateId(c, i + 1),
          displayName: i === 11 ? "Delegate_100%" : `Delegate ${String(i + 1).padStart(2, "0")}`,
        })),
      ].sort((a, b) => a.id.localeCompare(b.id));
      const after = url.searchParams.get("after");
      const query = url.searchParams.get("query")?.toLowerCase() ?? "";
      const matching = accounts.filter(
        (item) => (!after || item.id > after) && item.displayName.toLowerCase().includes(query),
      );
      return route.fulfill({
        json: {
          items: matching.slice(0, 10),
          nextCursor: matching.length > 10 ? matching[9]?.id : null,
        },
      });
    }
    expect(resource).toBe("delegations");
    const all = records.get(company) ?? [];
    if (kind === "list") {
      expect(url.searchParams.get("limit")).toBe("20");
      const after = url.searchParams.get("after");
      const matching = all
        .filter(
          (item) =>
            item.validUntil > new Date().toISOString() &&
            (!after || item.id > after) &&
            (item.fromAccount === delegationActor || item.toAccount === delegationActor),
        )
        .sort((a, b) => a.id.localeCompare(b.id));
      return route.fulfill({
        json: {
          items: matching.slice(0, 20),
          nextCursor: matching.length > 20 ? matching[19]?.id : null,
        },
      });
    }
    const current = all.find((item) => item.id === id);
    if (kind === "detail")
      return current
        ? route.fulfill({ json: current })
        : fail("approval_delegation_not_found", 404);
    expect(request.method()).toBe("PUT");
    if (!id) throw new Error("Missing delegation identifier");
    const body = request.postDataJSON() as ApprovalDelegationChangeDto;
    const operation = request.headers()["idempotency-key"] ?? "";
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    writes.push({ company, id, operation, body });
    if (failure) {
      const rejected = failure;
      failure = null;
      return fail(rejected.code, rejected.status);
    }
    const key = `${company}:${operation}`;
    const serialized = JSON.stringify(body);
    const replay = receipts.get(key);
    if (replay)
      return replay.id === id && replay.body === serialized
        ? route.fulfill({ json: { id, version: replay.version } })
        : fail("operation_payload_mismatch");
    if ((current?.version ?? null) !== body.expectedVersion) return fail("stale_version");
    if (current && current.fromAccount !== body.fromAccount) return fail("delegator_immutable");
    if (body.fromAccount !== delegationActor && !permissions.includes("approvals.manage"))
      return fail("access_denied", 403);
    if (body.fromAccount === body.toAccount) return fail("invalid_delegation", 422);
    const version = (current?.version ?? -1) + 1;
    const next = {
      id,
      version,
      kind: body.kind,
      fromAccount: body.fromAccount,
      toAccount: body.toAccount,
      validFrom: body.validFrom,
      validUntil: body.validUntil,
      active: body.active,
    };
    records.set(company, [...all.filter((item) => item.id !== id), next]);
    receipts.set(key, { id, version, body: serialized });
    commits += 1;
    if (lose) {
      lose = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: { id, version } });
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
    rejectNext: (code: string, status = 409) => {
      failure = { code, status };
    },
    holdNext: (kind: "detail" | "list" | "assignees" | "save") => {
      const held = gate();
      pending = { kind, gate: held };
      return held;
    },
    advance: () => {
      const all = records.get(companyIds[0]) ?? [];
      records.set(
        companyIds[0],
        all.map((item) =>
          item.id === delegationId() ? { ...item, version: item.version + 1, active: false } : item,
        ),
      );
    },
  };
}
