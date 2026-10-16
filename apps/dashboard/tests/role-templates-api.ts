import { expect, type Page } from "@playwright/test";
import type { RoleTemplateChangeDto } from "../src/features/identity/data/models/role-template-change-dto";
import type { RoleTemplateReviewDto } from "../src/features/identity/data/models/role-template-dto";
import {
  roleCatalog,
  roleTemplateHistoryPage,
  roleTemplateReview,
} from "./fixtures/role-templates";
import { companyIds, installIdentityApi } from "./identity-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installRoleTemplatesApi(
  page: Page,
  options: { allowed?: boolean; longPage?: boolean } = {},
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions: options.allowed === false ? [] : ["identity.manage"],
  });
  const records = new Map<string, RoleTemplateReviewDto[]>([
    [
      companyIds[0],
      Array.from({ length: options.longPage ? 21 : 1 }, (_, i) =>
        roleTemplateReview(i + 1, i === 0 ? 21 : 1),
      ),
    ],
    [
      companyIds[1],
      [
        {
          ...roleTemplateReview(),
          current: { ...roleTemplateReview().current, name: "Other company role" },
        },
      ],
    ],
  ]);
  const other = records.get(companyIds[1])?.[0];
  if (other) for (const row of other.history.items) row.name = other.current.name;
  const reads: URL[] = [];
  const writes: { company: string; id: string; operation: string; body: RoleTemplateChangeDto }[] =
    [];
  const receipts = new Map<string, { id: string; version: number; payload: string }>();
  let rejected: string | null = null;
  let readFailure: string | null = null;
  let dropped = false;
  let heldRead: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;
  let heldSave: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;
  function saveRecord(company: string, id: string, body: RoleTemplateChangeDto) {
    const existing = records.get(company) ?? [];
    const old = existing.find((record) => record.current.id === id);
    const version = (old?.current.version ?? -1) + 1;
    const { expectedVersion: _, reason, ...fields } = body;
    const record: RoleTemplateReviewDto = {
      current: { ...fields, id, version },
      history: {
        items: [
          {
            ...fields,
            revision: version,
            reason,
            actorId: "20000000-0000-4000-8000-000000000001",
            recordedAt: "2026-10-10T03:00:00Z",
          },
          ...(old?.history.items ?? []),
        ],
        nextCursor: null,
      },
    };
    records.set(
      company,
      [...existing.filter((value) => value.current.id !== id), record].sort((a, b) =>
        a.current.id.localeCompare(b.current.id),
      ),
    );
    return { id, version };
  }
  await page.route("**/api/v1/companies/*/role-templates**", async (route) => {
    const url = new URL(route.request().url());
    const match = /^\/api\/v1\/companies\/([^/]+)\/role-templates(?:\/([^/]+))?$/u.exec(
      url.pathname,
    );
    if (!match) return route.fallback();
    const company = match[1] ?? "";
    const id = match[2];
    if (route.request().method() === "PUT" && id) {
      const operation = route.request().headers()["idempotency-key"] ?? "";
      expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
      const body = route.request().postDataJSON() as RoleTemplateChangeDto;
      writes.push({ company, id, operation, body });
      const waiting = heldSave;
      heldSave = null;
      if (waiting) {
        waiting.entered.resolve();
        await waiting.released.promise;
      }
      if (rejected) {
        const code = rejected;
        rejected = null;
        return route.fulfill({ status: code === "stale_version" ? 409 : 403, json: { code } });
      }
      const previous = receipts.get(`${company}:${operation}`);
      if (previous) {
        expect(JSON.stringify(body)).toBe(previous.payload);
        return route.fulfill({ json: { id: previous.id, version: previous.version } });
      }
      const current = records.get(company)?.find((value) => value.current.id === id);
      if ((current?.current.version ?? null) !== body.expectedVersion)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      const receipt = saveRecord(company, id, body);
      receipts.set(`${company}:${operation}`, { ...receipt, payload: JSON.stringify(body) });
      if (dropped) {
        dropped = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: receipt });
    }
    reads.push(url);
    const waiting = heldRead;
    heldRead = null;
    if (waiting) {
      waiting.entered.resolve();
      await waiting.released.promise;
    }
    if (readFailure)
      return route.fulfill({
        status: 404,
        json: { code: readFailure, detail: "PRIVATE ROLE DIAGNOSTIC" },
      });
    if (id === "permissions") return route.fulfill({ json: roleCatalog });
    const all = records.get(company) ?? [];
    if (id) {
      const current = all.find((record) => record.current.id === id);
      return current
        ? route.fulfill({
            json: roleTemplateHistoryPage(current, url.searchParams.get("historyAfter")),
          })
        : route.fulfill({ status: 404, json: { code: "role_template_not_found" } });
    }
    const after = url.searchParams.get("after");
    const remaining = all.filter((record) => after === null || record.current.id > after);
    const items = remaining.slice(0, 20).map((record) => record.current);
    return route.fulfill({
      json: { items, nextCursor: remaining.length > items.length ? items.at(-1)?.id : null },
    });
  });
  return {
    identity,
    records,
    reads,
    writes,
    dropNext: () => {
      dropped = true;
    },
    rejectNext: (code: string) => {
      rejected = code;
    },
    failReads: (code: string | null) => {
      readFailure = code;
    },
    advance: (company: string, id: string) => {
      const role = records.get(company)?.find((record) => record.current.id === id)?.current;
      if (!role) throw new Error("Missing owned role fixture");
      saveRecord(company, id, {
        ...role,
        permissions: [...role.permissions],
        name: "Other writer role",
        expectedVersion: role.version,
        reason: "Independent review",
      });
    },
    holdRead: () => {
      const entered = gate(),
        released = gate();
      heldRead = { entered, released };
      return { entered: entered.promise, release: released.resolve };
    },
    holdSave: () => {
      const entered = gate(),
        released = gate();
      heldSave = { entered, released };
      return { entered: entered.promise, release: released.resolve };
    },
  };
}
