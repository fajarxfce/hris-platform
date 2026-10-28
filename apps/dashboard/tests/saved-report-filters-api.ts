import { expect, type Page } from "@playwright/test";
import type {
  SavedReportFilterDto,
  SaveReportFilterDto,
} from "../src/features/reporting/data/models/saved-report-filter-dto";
import { companyIds, installIdentityApi } from "./identity-api";
export const savedFilterId = "a1000000-0000-4000-8000-000000000001";
export const unavailableCompany = "a1000000-0000-4000-8000-000000000099";
export function savedFilter(companyId: string = companyIds[0]): SavedReportFilterDto {
  return {
    id: savedFilterId,
    companyId,
    name: "October headcount",
    kind: "HEADCOUNT",
    definitionVersion: "headcount.v1",
    parameters: {
      asOf: "2026-10-01",
      from: null,
      until: null,
      status: null,
      companies: [companyId],
    },
    version: 2,
    createdAt: "2026-10-01T00:00:00Z",
    updatedAt: "2026-10-02T00:00:00Z",
  };
}
function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installSavedFiltersApi(
  page: Page,
  options: {
    empty?: boolean;
    readerOnly?: boolean;
    count?: number;
    unknownSelection?: boolean;
  } = {},
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions: [
      "company.read",
      "reports.read",
      ...(options.readerOnly
        ? []
        : ["people.read", "workforce.read", "leave.read", "expenses.read", "payroll.read"]),
    ],
  });
  const records = new Map<string, SavedReportFilterDto[]>(
    companyIds.map((company, index) => [
      company,
      options.empty
        ? []
        : Array.from({ length: options.count ?? 1 }, (_, row) => ({
            ...savedFilter(company),
            id: `a1000000-0000-4000-8000-${String(row + 1).padStart(12, "0")}`,
            name: index ? "Other company filter" : row ? `Filter ${row + 1}` : "October headcount",
            parameters: {
              ...savedFilter(company).parameters,
              companies: options.unknownSelection ? [unavailableCompany] : [company],
            },
          })),
    ]),
  );
  const writes: {
    company: string;
    id: string;
    operation: string;
    method: string;
    body: SaveReportFilterDto | { expectedVersion: number };
  }[] = [];
  const reads: URL[] = [];
  const reportReads: URL[] = [];
  const receipts = new Map<string, { id: string; version: number; payload: string }>();
  let drop = false,
    reject: string | null = null,
    readFailure: string | null = null;
  let heldRead: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;
  let heldWrite: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;
  await page.route("**/api/v1/reports/headcount?*", async (route) => {
    const url = new URL(route.request().url());
    reportReads.push(url);
    const counts = {
      employments: 0,
      persons: 0,
      active: 0,
      probation: 0,
      suspended: 0,
      permanent: 0,
      fixedTerm: 0,
    };
    return route.fulfill({
      json: {
        asOf: url.searchParams.get("asOf"),
        definitionVersion: "headcount.v1",
        evaluatedAt: "2026-10-01T00:00:00Z",
        totals: counts,
        companies: (url.searchParams.get("companies") ?? "")
          .split(",")
          .map((companyId) => ({ companyId, counts })),
      },
    });
  });
  await page.route("**/api/v1/companies/*/reports/saved-filters**", async (route) => {
    const url = new URL(route.request().url());
    const match =
      /^\/api\/v1\/companies\/([^/]+)\/reports\/saved-filters(?:\/([^/]+))?(\/delete)?$/u.exec(
        url.pathname,
      );
    if (!match) return route.fallback();
    const company = match[1] ?? "",
      id = match[2] ?? "",
      deleting = !!match[3];
    const method = route.request().method();
    if (method === "GET") {
      reads.push(url);
      const waiting = heldRead;
      heldRead = null;
      if (waiting) {
        waiting.entered.resolve();
        await waiting.released.promise;
      }
      if (readFailure)
        return route.fulfill({ status: 403, json: { code: readFailure, detail: "PRIVATE" } });
      const items = records.get(company) ?? [];
      const value = id ? items.find((item) => item.id === id) : { companyId: company, items };
      return value
        ? route.fulfill({ json: value })
        : route.fulfill({ status: 404, json: { code: "report_filter_not_found" } });
    }
    const operation = route.request().headers()["idempotency-key"] ?? "";
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    const body = route.request().postDataJSON() as SaveReportFilterDto;
    writes.push({ company, id, operation, method, body });
    const waiting = heldWrite;
    heldWrite = null;
    if (waiting) {
      waiting.entered.resolve();
      await waiting.released.promise;
    }
    if (reject) {
      const code = reject;
      reject = null;
      return route.fulfill({ status: code === "stale_version" ? 409 : 403, json: { code } });
    }
    const key = `${company}:${operation}`;
    const previous = receipts.get(key);
    if (previous) {
      expect(JSON.stringify(body)).toBe(previous.payload);
      return route.fulfill({ json: { id, version: previous.version } });
    }
    const items = records.get(company) ?? [],
      old = items.find((item) => item.id === id);
    if ((old?.version ?? null) !== body.expectedVersion)
      return route.fulfill({ status: 409, json: { code: "stale_version" } });
    const version = (old?.version ?? -1) + 1;
    records.set(
      company,
      deleting
        ? items.filter((item) => item.id !== id)
        : [
            ...items.filter((item) => item.id !== id),
            {
              id,
              companyId: company,
              name: body.name,
              kind: body.kind,
              definitionVersion: body.definitionVersion,
              parameters: body.parameters,
              version,
              createdAt: old?.createdAt ?? "2026-10-01T00:00:00Z",
              updatedAt: "2026-10-03T00:00:00Z",
            },
          ],
    );
    receipts.set(key, { id, version, payload: JSON.stringify(body) });
    if (drop) {
      drop = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: { id, version } });
  });
  return {
    identity,
    records,
    reads,
    writes,
    reportReads,
    dropNext: () => {
      drop = true;
    },
    rejectNext: (code: string) => {
      reject = code;
    },
    failReads: (code: string | null) => {
      readFailure = code;
    },
    holdRead: () => {
      const entered = gate(),
        released = gate();
      heldRead = { entered, released };
      return { entered: entered.promise, release: released.resolve };
    },
    holdWrite: () => {
      const entered = gate(),
        released = gate();
      heldWrite = { entered, released };
      return { entered: entered.promise, release: released.resolve };
    },
    advance: () => {
      const old = records.get(companyIds[0])?.[0];
      if (old) {
        old.name = "Changed elsewhere";
        old.version += 1;
      }
    },
  };
}
