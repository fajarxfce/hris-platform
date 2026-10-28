import { expect, type Page } from "@playwright/test";
import type { ReportExportDto } from "../src/features/reporting/data/models/report-export-dto";
import { companyIds } from "./identity-api";
import { installSavedFiltersApi } from "./saved-report-filters-api";
export const reportExportId = "b1000000-0000-4000-8000-000000000001";
export function reportExport(companyId: string = companyIds[0]): ReportExportDto {
  const createdAt = new Date().toISOString();
  return {
    id: reportExportId,
    companyId,
    selection: {
      kind: "HEADCOUNT",
      definitionVersion: "headcount.v1",
      parameters: {
        asOf: "2026-10-01",
        from: null,
        until: null,
        status: null,
        companies: [companyId],
      },
    },
    dataset: "SUMMARY",
    status: "QUEUED",
    version: 0,
    attempt: 1,
    createdAt,
    updatedAt: createdAt,
    expiresAt: new Date(Date.parse(createdAt) + 7 * 86400000).toISOString(),
    expired: false,
    retiredAt: null,
    completedRows: 0,
    bytes: 0,
    jobId: "b2000000-0000-4000-8000-000000000001",
    jobStatus: "QUEUED",
    cancellationRequested: false,
    failureCode: null,
  };
}
function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installReportExportsApi(
  page: Page,
  options: { empty?: boolean; count?: number; readerOnly?: boolean } = {},
) {
  const baseline = await installSavedFiltersApi(page, { readerOnly: options.readerOnly ?? false });
  const csv = Buffer.from(
    `\uFEFF"company","quantity"\r\n${Array.from({ length: 1201 }, (_, i) => `"North","${i}.50"\r\n`).join("")}`,
    "utf8",
  );
  const rows = new Map<string, ReportExportDto[]>(
    companyIds.map((company, index) => [
      company,
      options.empty
        ? []
        : Array.from({ length: options.count ?? 1 }, (_, i) => ({
            ...reportExport(company),
            id: index
              ? `c1000000-0000-4000-8000-${String(i + 1).padStart(12, "0")}`
              : `b1000000-0000-4000-8000-${String(i + 1).padStart(12, "0")}`,
          })),
    ]),
  );
  const writes: {
      company: string;
      path: string;
      operation: string;
      body: Record<string, unknown>;
    }[] = [],
    reads: URL[] = [];
  const receipts = new Map<string, { id: string; version: number; body: string; path: string }>();
  let drop = false,
    reject: string | null = null,
    readFailure: string | null = null,
    contentFailure: string | null = null;
  let held: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null = null;
  await page.route("**/api/v1/companies/*/reports/exports**", async (route) => {
    const url = new URL(route.request().url()),
      match =
        /^\/api\/v1\/companies\/([^/]+)\/reports\/exports(?:\/([^/]+))?(?:\/(cancel|resume|content))?$/u.exec(
          url.pathname,
        );
    if (!match) return route.fallback();
    const company = match[1] ?? "",
      id = match[2] ?? "",
      action = match[3] ?? "",
      items = rows.get(company) ?? [];
    if (route.request().method() === "GET") {
      reads.push(url);
      const waiting = held;
      held = null;
      if (waiting) {
        waiting.entered.resolve();
        await waiting.released.promise;
      }
      if (readFailure)
        return route.fulfill({ status: 403, json: { code: readFailure, detail: "PRIVATE" } });
      if (action === "content") {
        if (contentFailure)
          return route.fulfill({ status: 403, json: { code: contentFailure, detail: "PRIVATE" } });
        return route.fulfill({
          body: csv,
          headers: {
            "Content-Type": "text/csv; charset=UTF-8",
            "Content-Length": String(csv.length),
            ETag: `"${id}-${"a".repeat(64)}"`,
          },
        });
      }
      if (id) {
        const item = items.find((item) => item.id === id);
        return item
          ? route.fulfill({ json: item })
          : route.fulfill({ status: 404, json: { code: "report_export_not_found" } });
      }
      const sorted = [...items].sort(
          (a, b) => b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id),
        ),
        before = url.searchParams.get("beforeId"),
        start = before ? sorted.findIndex((item) => item.id === before) + 1 : 0,
        selected = sorted.slice(start, start + 20),
        last = selected.at(-1);
      return route.fulfill({
        json: {
          items: selected,
          next:
            start + 20 < sorted.length && last ? { id: last.id, createdAt: last.createdAt } : null,
        },
      });
    }
    const operation = route.request().headers()["idempotency-key"] ?? "",
      body = route.request().postDataJSON() as Record<string, unknown>;
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    writes.push({ company, path: url.pathname, operation, body });
    if (reject) {
      const code = reject;
      reject = null;
      return route.fulfill({ status: 409, json: { code } });
    }
    const key = `${company}:${operation}`,
      old = receipts.get(key);
    if (old) {
      expect(old.body).toBe(JSON.stringify(body));
      expect(old.path).toBe(url.pathname);
      return route.fulfill({ json: { id: old.id, version: old.version } });
    }
    let value: ReportExportDto;
    if (!id) {
      const { dataset, ...selection } = body;
      value = {
        ...reportExport(company),
        id: reportExportId,
        selection: selection as ReportExportDto["selection"],
        dataset: dataset as "SUMMARY" | "DETAIL",
      };
      items.unshift(value);
      rows.set(company, items);
    } else {
      const found = items.find((item) => item.id === id);
      if (!found) return route.fulfill({ status: 404, json: { code: "report_export_not_found" } });
      if (body.expectedVersion !== found.version)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      Object.assign(found, {
        version: found.version + 1,
        status: action === "cancel" ? "STOPPED" : "QUEUED",
        jobStatus: "QUEUED",
        cancellationRequested: action === "cancel",
        failureCode: action === "cancel" ? "report_export_cancelled" : null,
        ...(action === "resume" ? { attempt: found.attempt + 1 } : {}),
      });
      value = found;
    }
    const receipt = { id: value.id, version: value.version };
    receipts.set(key, { ...receipt, body: JSON.stringify(body), path: url.pathname });
    if (drop) {
      drop = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    ...baseline,
    exportReads: reads,
    exportWrites: writes,
    csv,
    patch: (fields: Partial<ReportExportDto>, company = companyIds[0]) => {
      const item = rows.get(company)?.find((item) => item.id === reportExportId);
      if (!item) throw new Error("Missing fixture export");
      Object.assign(item, fields);
    },
    ready: () => {
      const item = rows.get(companyIds[0])?.find((item) => item.id === reportExportId);
      if (!item) throw new Error("Missing fixture export");
      Object.assign(item, {
        status: "READY",
        jobStatus: "SUCCEEDED",
        version: item.version + 1,
        bytes: csv.length,
        completedRows: 1201,
        cancellationRequested: false,
        failureCode: null,
      });
    },
    dropNext: () => {
      drop = true;
    },
    rejectNext: (code: string) => {
      reject = code;
    },
    failRead: (code: string | null) => {
      readFailure = code;
    },
    failContent: (code: string | null) => {
      contentFailure = code;
    },
    holdExportRead: () => {
      const value = { entered: gate(), released: gate() };
      held = value;
      return { entered: value.entered.promise, release: value.released.resolve };
    },
  };
}
