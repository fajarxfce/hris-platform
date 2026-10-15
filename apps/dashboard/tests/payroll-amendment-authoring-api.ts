import { expect, type Page } from "@playwright/test";
import type {
  PayrollAmendmentDto,
  PayrollAmendmentTermsDto,
} from "../src/features/payroll/data/models/payroll-amendment-dto";
import type { PayrollAmendmentSaveDto } from "../src/features/payroll/data/models/payroll-amendment-preparation-dto";
import { amendmentPreparation, amendmentPreview } from "./fixtures/payroll-amendment-authoring";
import { amendmentId, amendmentRevision, amendmentSummary } from "./fixtures/payroll-amendments";
import { calculationCompany, calculationEmployeeId } from "./fixtures/payroll-calculations";
import { payslipId } from "./fixtures/payroll-payslips";
import { installIdentityApi } from "./identity-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installPayrollAmendmentAuthoringApi(
  page: Page,
  options: {
    multiple?: boolean;
    permissions?: string[];
    count?: number;
    publishedRevision?: number;
  } = {},
) {
  const permissions = options.permissions ?? ["payroll.amend"],
    identity = await installIdentityApi(page, {
      signedIn: true,
      mfa: true,
      mfaConfigured: true,
      mfaVerified: true,
      permissions,
    });
  const requests: { action: string; url: URL; input: unknown; operation: string | null }[] = [];
  const drafts = new Map<string, PayrollAmendmentDto[]>([[amendmentId(), [amendmentRevision(0)]]]),
    receipts = new Map<string, { id: string; version: number; input: string }>();
  let publishedRevision = options.publishedRevision ?? 0,
    denied = false,
    loseNextSave = false;
  let fail: { action: string; code: string; status: number } | null = null;
  let held: {
    action: string;
    entered: ReturnType<typeof gate>;
    released: ReturnType<typeof gate>;
  } | null = null;
  await page.route("**/api/v1/companies/*/payroll/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      parts = url.pathname.split("/"),
      id = parts[7];
    const action =
      parts[6] === "amendment-sources"
        ? "sources"
        : parts[8] === "amendment-preparation"
          ? "prepare"
          : parts[8] === "amendment-preview"
            ? "preview"
            : request.method() === "PUT"
              ? "save"
              : (parts[8] ?? (id ? "get" : "directory"));
    const body = request.method() === "GET" ? null : request.postDataJSON(),
      operation = request.headers()["idempotency-key"] ?? null;
    requests.push({ action, url, input: body, operation });
    const waiting = held;
    if (waiting?.action === action) {
      held = null;
      waiting.entered.resolve();
      await waiting.released.promise;
    }
    const problem = (code: string, status: number) =>
      route.fulfill({
        status,
        json: { code, fields: {}, parameters: {}, detail: "PRIVATE authoring diagnostic" },
      });
    if (
      denied ||
      (["sources", "prepare", "preview", "save"].includes(action) &&
        !permissions.includes("payroll.amend"))
    )
      return problem("access_denied", 403);
    if (fail?.action === action) {
      const failed = fail;
      fail = null;
      return problem(failed.code, failed.status);
    }
    if (parts[4] !== calculationCompany)
      return ["sources", "directory"].includes(action)
        ? route.fulfill({ json: { items: [], nextCursor: null } })
        : problem("access_denied", 403);
    if (action === "sources") {
      const count = options.count ?? 1,
        query = (url.searchParams.get("query") ?? "").toLowerCase(),
        after = url.searchParams.get("after");
      const rows = Array.from({ length: count }, (_, index) => ({
        assessmentId: payslipId(index + 1),
        employeeId: calculationEmployeeId(index + 1),
        employeeNumber: `EMP-${String(index + 1).padStart(4, "0")}`,
        employeeName: `Amendment employee ${index + 1}`,
        taxMonth: "2026-09",
        revision: publishedRevision,
        publishedAt: "2026-10-01T01:00:00Z",
      }));
      const matching = rows.filter(
        (row) =>
          row.taxMonth.startsWith(url.searchParams.get("year") ?? "") &&
          (!after || row.assessmentId > after) &&
          `${row.employeeName} ${row.employeeNumber}`.toLowerCase().includes(query),
      );
      const items = matching.slice(0, 50);
      expect(url.searchParams.get("limit")).toBe("50");
      return route.fulfill({
        json: { items, nextCursor: matching.length > 50 ? items.at(-1)?.assessmentId : null },
      });
    }
    if (action === "prepare" || action === "preview") {
      const preparation = amendmentPreparation(options.multiple, publishedRevision);
      if (id !== payslipId()) return problem("payroll_assessment_not_found", 404);
      if (action === "prepare") return route.fulfill({ json: preparation });
      const terms = body as PayrollAmendmentTermsDto;
      if (
        terms.sourceVersions.some((row) => row.revision !== publishedRevision) ||
        terms.sourceVersions.length !== preparation.sourceVersions.length
      )
        return problem("payroll_amendment_sources_changed", 409);
      return route.fulfill({ json: amendmentPreview(preparation, terms) });
    }
    if (action === "save" && id) {
      const input = body as PayrollAmendmentSaveDto;
      expect(operation).toBeTruthy();
      const original = receipts.get(operation ?? "");
      if (original) {
        expect(JSON.stringify(input)).toBe(original.input);
        expect(id).toBe(original.id);
        return route.fulfill({ json: { id, version: original.version } });
      }
      const revisions = drafts.get(id) ?? [],
        current = revisions.at(-1)?.summary.version ?? null;
      if (current !== input.expectedVersion) return problem("stale_version", 409);
      if (input.sourceVersions.some((row) => row.revision !== publishedRevision))
        return problem("payroll_amendment_sources_changed", 409);
      const version = (current ?? -1) + 1;
      const summary = {
        ...amendmentSummary(0),
        id,
        version,
        recordedAt: `2026-10-01T01:00:${String(version).padStart(2, "0")}Z`,
        reason: input.reason,
      };
      revisions.push({
        summary,
        terms: structuredClone({
          sourceVersions: input.sourceVersions,
          corrections: input.corrections,
        }),
      });
      drafts.set(id, revisions);
      receipts.set(operation ?? "", { id, version, input: JSON.stringify(input) });
      if (loseNextSave) {
        loseNextSave = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: { id, version } });
    }
    if (action === "directory")
      return route.fulfill({
        json: { items: [...drafts.values()].map((rows) => rows.at(-1)?.summary), nextCursor: null },
      });
    const revisions = drafts.get(id ?? ""),
      current = revisions?.at(-1);
    if (!revisions || !current) return problem("payroll_amendment_not_found", 404);
    if (action === "revisions") {
      const revision = revisions[Number(parts[9])];
      return revision
        ? route.fulfill({ json: revision })
        : problem("payroll_amendment_not_found", 404);
    }
    if (action === "history")
      return route.fulfill({
        json: {
          items: revisions
            .filter((row) => row.summary.version > Number(url.searchParams.get("after")))
            .map((row) => row.summary),
          nextCursor: null,
        },
      });
    if (action === "get")
      return route.fulfill({
        json: {
          amendment: current,
          history: { items: revisions.map((row) => row.summary), nextCursor: null },
          latestCalculation: null,
          latestReview: null,
          availableActions: ["EDIT", "CALCULATE", "CANCEL"],
        },
      });
    throw new Error(`Unhandled amendment fixture action: ${action}`);
  });
  return {
    identity,
    requests,
    drafts,
    receipts,
    count(action: string) {
      return requests.filter((request) => request.action === action).length;
    },
    setPublishedRevision(value: number) {
      publishedRevision = value;
    },
    loseNextSave() {
      loseNextSave = true;
    },
    failNext(action: string, code: string, status = 503) {
      fail = { action, code, status };
    },
    deny() {
      denied = true;
      identity.setPermissions(["company.read"]);
    },
    holdNext(action: string) {
      const waiting = { action, entered: gate(), released: gate() };
      held = waiting;
      return { entered: waiting.entered.promise, release: waiting.released.resolve };
    },
  };
}
