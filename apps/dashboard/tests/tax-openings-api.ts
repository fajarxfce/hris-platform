import { expect, type Page } from "@playwright/test";
import type {
  TaxOpeningChangeDto,
  TaxOpeningDto,
} from "../src/features/payroll/data/models/tax-opening-dto";
import {
  openingActor,
  openingEmployee,
  openingId,
  openingRecord,
  openingReference,
  openingReview,
  openingReviewer,
  openingYear,
} from "./fixtures/tax-openings";
import { companyIds, installIdentityApi } from "./identity-api";

export async function installTaxOpeningsApi(
  page: Page,
  permissions = ["payroll.calculate"],
  empty = false,
) {
  await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const years = new Map<string, TaxOpeningDto[]>([
    [openingYear, empty ? [] : Array.from({ length: 3 }, (_, i) => openingRecord(i))],
  ]);
  const reads: URL[] = [],
    writes: { operation: string; method: string; body: Record<string, unknown> }[] = [];
  const receipts = new Map<
    string,
    { id: string; version: number; payload: string; path: string }
  >();
  let failure: string | null = null,
    drop = false,
    employmentVersion = 7;
  await page.route("**/api/v1/companies/*/payroll/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      company = url.pathname.split("/")[4],
      employee = url.pathname.split("/")[7],
      year = url.searchParams.get("year") ?? url.pathname.split("/")[9] ?? openingYear,
      revisions = years.get(year) ?? [],
      current = revisions.at(-1) ?? null;
    const problem = (code: string, status = 409) =>
      route.fulfill({ status, json: { code, fields: {}, parameters: {} } });
    expect(request.headers()["x-hris-client-platform"]).toBe("WEB");
    if (request.method() === "GET") {
      reads.push(url);
      if (failure) {
        const code = failure;
        failure = null;
        return problem(code, 403);
      }
      if (url.pathname.endsWith("/tax-openings/employees")) {
        const query = url.searchParams.get("query")?.toLowerCase() ?? "",
          status = url.searchParams.get("status");
        const rows =
          company === companyIds[0]
            ? [
                {
                  ...openingReference(),
                  employmentVersion,
                  openingVersion: current?.version ?? null,
                  openingStatus: current?.status ?? null,
                },
              ]
            : [
                {
                  ...openingReference(),
                  id: "bc100000-0000-4000-8000-000000000002",
                  name: "South Tax Employee",
                  openingVersion: null,
                  openingStatus: null,
                },
              ];
        return route.fulfill({
          json: {
            items: rows.filter(
              (r) =>
                (r.name.toLowerCase().includes(query) ||
                  r.employeeNumber.toLowerCase().includes(query)) &&
                (!status || (r.openingStatus ?? "UNPREPARED") === status),
            ),
            nextCursor: null,
          },
        });
      }
      if (employee !== openingEmployee || company !== companyIds[0])
        return problem("employee_not_found", 404);
      if (url.pathname.endsWith("/review")) {
        const after = url.searchParams.get("historyAfter"),
          raw = openingReview(current, after),
          remaining = revisions.filter((r) => after === null || r.version > Number(after)),
          items = remaining.slice(0, 20).map(({ terms: _terms, ...summary }) => summary);
        raw.year = Number(year);
        raw.employee.employmentVersion = employmentVersion;
        raw.history = {
          items,
          nextCursor: remaining.length > 20 ? String(items.at(-1)?.version) : null,
        };
        raw.availableActions = permissions.includes("payroll.calculate")
          ? ["PREPARE"]
          : permissions.includes("payroll.review") && current?.status === "DRAFT"
            ? ["VERIFY"]
            : [];
        return route.fulfill({ json: raw });
      }
      const revision = revisions.find(
        (r) => r.version === Number(url.searchParams.get("revision")),
      );
      return revision
        ? route.fulfill({ json: revision })
        : problem("payroll_tax_opening_not_found", 404);
    }
    if (employee !== openingEmployee || company !== companyIds[0])
      return problem("employee_not_found", 404);
    const body = request.postDataJSON() as Record<string, unknown>,
      operation = request.headers()["idempotency-key"] ?? "";
    writes.push({ operation, method: request.method(), body });
    if (failure) {
      const code = failure;
      failure = null;
      return problem(code);
    }
    const replay = receipts.get(operation);
    if (replay) {
      expect(replay.payload).toBe(JSON.stringify(body));
      expect(replay.path).toBe(url.pathname);
      return route.fulfill({ json: { id: replay.id, version: replay.version } });
    }
    if (body.expectedVersion !== (current?.version ?? null)) return problem("stale_version");
    let next: TaxOpeningDto;
    if (request.method() === "PUT") {
      const command = body as unknown as TaxOpeningChangeDto;
      if (command.expectedEmploymentVersion !== employmentVersion)
        return problem("stale_employment_version");
      next = {
        ...openingRecord((current?.version ?? -1) + 1),
        year: Number(year),
        terms: command.terms,
        reason: command.reason,
        preparedBy: openingActor,
      };
    } else {
      if (!current) return problem("payroll_tax_opening_not_found", 404);
      next = {
        ...current,
        version: current.version + 1,
        status: "VERIFIED",
        verifiedBy: openingReviewer,
        reason: String(body.reason),
      };
    }
    years.set(year, [...revisions, next]);
    const receipt = { id: openingId, version: next.version };
    receipts.set(operation, { ...receipt, payload: JSON.stringify(body), path: url.pathname });
    if (drop) {
      drop = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    reads,
    writes,
    dropNext: () => {
      drop = true;
    },
    failNext: (code: string) => {
      failure = code;
    },
    advanceEmployment: () => {
      employmentVersion++;
    },
  };
}
