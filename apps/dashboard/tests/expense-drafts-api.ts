import { expect, type Page } from "@playwright/test";
import type { ExpenseDraftChangeDto } from "../src/features/expenses/data/models/expense-draft-change-dto";
import { expenseAmountMinor } from "../src/features/expenses/domain/policies/expense-amount";
import { installExpenseClaimsApi } from "./expense-claims-api";
import {
  expenseDraftCategories,
  expenseDraftCostCenters,
  expenseDraftEmployees,
  expenseDraftReceipts,
  expenseDraftRecord,
  expenseDraftReferences,
} from "./fixtures/expense-drafts";
import { companyIds } from "./identity-api";

function gate() {
  let enter!: () => void, release!: () => void;
  const started = new Promise<void>((done) => {
      enter = done;
    }),
    held = new Promise<void>((done) => {
      release = done;
    });
  return { started, held, enter: () => enter(), release: () => release() };
}
type Kind = "employees" | "receipts" | "references" | "categories" | "cost-centers";
export async function installExpenseDraftsApi(
  page: Page,
  options: { permissions?: string[]; returned?: boolean } = {},
) {
  const api = await installExpenseClaimsApi(page, options.permissions ?? ["expenses.manage"]);
  for (const [scope, company] of companyIds.entries())
    api.records.set(company, [expenseDraftRecord(scope, options.returned)]);
  const reads: { kind: Kind; company: string; url: URL }[] = [],
    writes: { company: string; id: string; operation: string; body: ExpenseDraftChangeDto }[] = [];
  const receipts = new Map<string, { id: string; version: number; body: string }>();
  let commits = 0,
    lose = false,
    readFailure: { kind: Kind; code: string } | null = null,
    writeFailure: { code: string; status: number } | null = null,
    readGate: { kind: Kind; gate: ReturnType<typeof gate> } | null = null,
    writeGate: ReturnType<typeof gate> | null = null;
  await page.route("**/api/v1/companies/*/expenses/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname.split("/"),
      company = path[4] ?? "",
      scope = company === companyIds[0] ? 0 : 1;
    const kind: Kind | null =
      path[6] === "drafting"
        ? path[9] === "receipts"
          ? "receipts"
          : path[9] === "references"
            ? "references"
            : "employees"
        : path[6] === "categories"
          ? "categories"
          : path[6] === "cost-centers"
            ? "cost-centers"
            : null;
    if (kind && request.method() === "GET") {
      reads.push({ kind, company, url });
      const failure = readFailure?.kind === kind ? readFailure.code : null;
      if (failure) readFailure = null;
      if (readGate?.kind === kind) {
        const pending = readGate.gate;
        readGate = null;
        pending.enter();
        await pending.held;
      }
      if (failure)
        return route.fulfill({
          status: 403,
          json: { code: failure, detail: "PRIVATE EMPLOYEE DATA" },
        });
      if (kind !== "references") expect(url.searchParams.get("limit")).toBe("20");
      if (kind === "employees") {
        const all = expenseDraftEmployees(scope, 23),
          query = (url.searchParams.get("query") ?? "").toLowerCase(),
          after = url.searchParams.get("after");
        const available = all.employees.items.filter(
          (item) =>
            (!after || item.employeeNumber > after) &&
            (!query || `${item.legalName} ${item.employeeNumber}`.toLowerCase().includes(query)),
        );
        const items = available.slice(0, 20);
        return route.fulfill({
          json: {
            ...all,
            employees: {
              items,
              nextCursor: available.length > 20 ? items.at(-1)?.employeeNumber : null,
            },
          },
        });
      }
      if (kind === "categories") return route.fulfill({ json: expenseDraftCategories(scope) });
      if (kind === "cost-centers") return route.fulfill({ json: expenseDraftCostCenters(scope) });
      if (kind === "receipts") return route.fulfill({ json: expenseDraftReceipts(scope, 4) });
      const references = expenseDraftReferences(scope),
        categoryIds = url.searchParams.getAll("categoryIds"),
        costCenterIds = url.searchParams.getAll("costCenterIds"),
        revisionIds = url.searchParams.getAll("receiptRevisionIds");
      return route.fulfill({
        json: {
          employmentId: path[8],
          categories: categoryIds.map(
            (id) =>
              references.categories.find((row) => row.id === id) ?? {
                id,
                name: null,
                code: null,
                active: null,
              },
          ),
          costCenters: costCenterIds.map(
            (id) =>
              references.costCenters.find((row) => row.id === id) ?? {
                id,
                name: null,
                code: null,
                active: null,
              },
          ),
          receipts: revisionIds.map(
            (revisionId) =>
              references.receipts.find((row) => row.revisionId === revisionId) ?? {
                revisionId,
                documentId: null,
                fileName: null,
                mediaType: null,
                size: null,
                status: null,
              },
          ),
        },
      });
    }
    if (request.method() !== "PUT" || path[6] !== "claims" || path[8] !== "draft")
      return route.fallback();
    const id = path[7] ?? "",
      operation = request.headers()["idempotency-key"] ?? "",
      body = request.postDataJSON() as ExpenseDraftChangeDto;
    expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
    writes.push({ company, id, operation, body });
    if (writeGate) {
      const pending = writeGate;
      writeGate = null;
      pending.enter();
      await pending.held;
    }
    if (writeFailure) {
      const failure = writeFailure;
      writeFailure = null;
      return route.fulfill({
        status: failure.status,
        json: { code: failure.code, detail: "PRIVATE EMPLOYEE DATA" },
      });
    }
    const key = `${company}:${operation}`,
      previous = receipts.get(key);
    if (previous) {
      expect(previous.id).toBe(id);
      expect(previous.body).toBe(JSON.stringify(body));
      return route.fulfill({ json: { id, version: previous.version } });
    }
    const records = api.records.get(company) ?? [],
      current = records.find((record) => record.claim.id === id);
    if ((current?.claim.version ?? null) !== body.expectedVersion)
      return route.fulfill({ status: 409, json: { code: "stale_version" } });
    const record = current ?? expenseDraftRecord(scope),
      employee = expenseDraftEmployees(scope, 23).employees.items.find(
        (row) => row.id === body.employmentId,
      );
    if (!employee) throw new Error("Fixture employee is missing");
    const version = (current?.claim.version ?? -1) + 1,
      revision = (current?.claim.draftRevision ?? -1) + 1;
    const total = body.lines.reduce(
      (sum, line) => sum + (expenseAmountMinor(line.amount) ?? 0n),
      0n,
    );
    record.claim = {
      ...record.claim,
      id,
      employmentId: body.employmentId,
      version,
      draftRevision: revision,
      status: "DRAFT",
      createdAt: current ? record.claim.createdAt : "2026-10-13T12:00:00Z",
      draft: {
        ...record.claim.draft,
        revision,
        employeeNumber: employee.employeeNumber,
        employeeName: employee.legalName,
        title: body.title,
        description: body.description,
        reason: body.reason,
        totalAmount: `${total / 100n}.${String(total % 100n).padStart(2, "0")}`,
        lines: body.lines.map((line) => ({
          ...line,
          receiptRevisionIds: [...line.receiptRevisionIds],
        })),
      },
    };
    if (!current) record.history.items = [];
    record.history.items.unshift({
      version,
      draftRevision: revision,
      status: "DRAFT",
      kind: "DRAFT_SAVED",
      actorId: record.claim.draft.actorId,
      reason: body.reason,
      recordedAt: "2026-10-13T12:00:00Z",
      submissionId: null,
    });
    if (!current) records.unshift(record);
    api.records.set(company, records);
    receipts.set(key, { id, version, body: JSON.stringify(body) });
    commits++;
    if (lose) {
      lose = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: { id, version } });
  });
  return {
    ...api,
    draftingReads: reads,
    writes,
    get commits() {
      return commits;
    },
    loseNext: () => {
      lose = true;
    },
    rejectWrite: (code: string, status = 409) => {
      writeFailure = { code, status };
    },
    rejectDraftRead: (kind: Kind, code: string) => {
      readFailure = { kind, code };
    },
    holdDraftRead: (kind: Kind) => {
      const pending = gate();
      readGate = { kind, gate: pending };
      return pending;
    },
    holdWrite: () => {
      const pending = gate();
      writeGate = pending;
      return pending;
    },
  };
}
