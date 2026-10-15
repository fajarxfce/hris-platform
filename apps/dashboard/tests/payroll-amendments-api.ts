import { expect, type Page } from "@playwright/test";
import {
  amendmentCalculation,
  amendmentDetails,
  amendmentId,
  amendmentRevision,
  amendmentSummary,
} from "./fixtures/payroll-amendments";
import { calculationCompany } from "./fixtures/payroll-calculations";
import { installIdentityApi } from "./identity-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installPayrollAmendmentsApi(
  page: Page,
  options: {
    permissions?: readonly string[];
    count?: number;
    version?: number;
    failedCalculation?: boolean;
  } = {},
) {
  const permissions = options.permissions ?? ["payroll.amend.review"],
    identity = await installIdentityApi(page, {
      signedIn: true,
      mfa: true,
      mfaConfigured: true,
      mfaVerified: true,
      permissions,
    }),
    reads: URL[] = [],
    version = options.version ?? 1,
    rows = Array.from({ length: options.count ?? 3 }, (_, i) => amendmentSummary(version, i + 1));
  let denied = false,
    failure: { action: string; code: string; status: number } | null = null,
    held: {
      action: string;
      entered: ReturnType<typeof gate>;
      released: ReturnType<typeof gate>;
    } | null = null;
  await page.route("**/api/v1/companies/*/payroll/amendments**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      parts = url.pathname.split("/"),
      id = parts[7],
      action = parts[8] ?? (id ? "get" : "list");
    reads.push(url);
    expect(request.method()).toBe("GET");
    const waiting = held;
    if (waiting?.action === action) {
      held = null;
      waiting.entered.resolve();
      await waiting.released.promise;
    }
    const problem = (code: string, status: number) =>
      route.fulfill({
        status,
        json: { code, fields: {}, parameters: {}, detail: "PRIVATE diagnostic sentinel" },
      });
    if (
      denied ||
      !permissions.some((value) =>
        ["payroll.amend", "payroll.amend.review", "payroll.amend.publish"].includes(value),
      )
    )
      return problem("access_denied", 403);
    if (failure?.action === action) {
      const next = failure;
      failure = null;
      return problem(next.code, next.status);
    }
    const scoped = parts[4] === calculationCompany ? rows : [];
    if (!id) {
      expect(url.searchParams.get("limit")).toBe("50");
      const query = (url.searchParams.get("query") ?? "").toLowerCase(),
        after = url.searchParams.get("after"),
        status = url.searchParams.get("status"),
        year = url.searchParams.get("year"),
        matching = scoped.filter(
          (row) =>
            row.firstMonth.slice(0, 4) === year &&
            (!after || row.id > after) &&
            (!status || row.status === status) &&
            `${row.employee.name} ${row.employee.employeeNumber}`.toLowerCase().includes(query),
        ),
        items = matching.slice(0, 50);
      return route.fulfill({
        json: { items, nextCursor: matching.length > 50 ? (items.at(-1)?.id ?? null) : null },
      });
    }
    const row = scoped.find((row) => row.id === id);
    if (!row) return problem("payroll_amendment_not_found", 404);
    const index = rows.indexOf(row) + 1;
    if (action === "revisions") {
      const selected = Number(parts[9]);
      if (!Number.isInteger(selected) || selected < 0 || selected > version)
        return problem("payroll_amendment_not_found", 404);
      return route.fulfill({ json: amendmentRevision(selected, index) });
    }
    if (action === "calculations")
      return route.fulfill({ json: amendmentCalculation(index, options.failedCalculation) });
    if (action === "history") {
      const after = Number(url.searchParams.get("after")),
        items = Array.from({ length: version + 1 }, (_, i) => amendmentSummary(i, index))
          .filter((row) => row.version > after)
          .slice(0, 25);
      expect(url.searchParams.get("limit")).toBe("25");
      return route.fulfill({
        json: {
          items,
          nextCursor:
            (items.at(-1)?.version ?? version) < version ? String(items.at(-1)?.version) : null,
        },
      });
    }
    const details = amendmentDetails(version, index);
    if (options.failedCalculation) {
      details.amendment.summary.status = "DRAFT";
      details.amendment.summary.action = "CALCULATION_FAILED";
      details.history.items[version] = { ...details.amendment.summary };
      details.latestCalculation = amendmentCalculation(index, true).summary;
      details.availableActions = ["EDIT", "CALCULATE", "CANCEL"];
    }
    return route.fulfill({ json: details });
  });
  return {
    identity,
    reads,
    ownId: amendmentId(),
    deny() {
      denied = true;
      identity.setPermissions(["company.read"]);
    },
    failNext(action: string, code: string, status = 503) {
      failure = { action, code, status };
    },
    holdNext(action: string) {
      const waiting = { action, entered: gate(), released: gate() };
      held = waiting;
      return { entered: waiting.entered.promise, release: waiting.released.resolve };
    },
    count(action: string) {
      return reads.filter(
        (url) =>
          (url.pathname.split("/")[8] ?? (url.pathname.split("/")[7] ? "get" : "list")) === action,
      ).length;
    },
  };
}
