import { expect, type Page } from "@playwright/test";
import type { ExpenseClaimActionDto } from "../src/features/expenses/data/models/expense-claim-action-dto";
import { installExpenseDraftsApi } from "./expense-drafts-api";
import { expenseClaimRecord, expenseIdentifier } from "./fixtures/expense-claims";
import { companyIds } from "./identity-api";

function gate() {
  let enter!: () => void, release!: () => void;
  const started = new Promise<void>((done) => {
    enter = done;
  });
  const held = new Promise<void>((done) => {
    release = done;
  });
  return { started, held, enter: () => enter(), release: () => release() };
}

export async function installExpenseClaimActionsApi(
  page: Page,
  options: { pending?: boolean; returned?: boolean; permissions?: string[] } = {},
) {
  const api = await installExpenseDraftsApi(page, options);
  if (options.pending)
    for (const [scope, company] of companyIds.entries())
      api.records.set(company, [expenseClaimRecord(scope, 1, 1)]);
  const actionWrites: {
    company: string;
    path: string;
    operation: string;
    body: ExpenseClaimActionDto & { submissionId?: string };
  }[] = [];
  const receipts = new Map<string, { id: string; version: number; path: string; body: string }>();
  let commits = 0,
    lose = false;
  let rejection: { code: string; status: number } | null = null;
  let pendingWrite: ReturnType<typeof gate> | null = null;
  await page.route("**/api/v1/companies/*/expenses/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname.split("/");
    const kind = path[8];
    if (request.method() !== "POST" || !["submit", "cancel", "withdraw"].includes(kind ?? ""))
      return route.fallback();
    const company = path[4] ?? "",
      id = path[7] ?? "",
      operation = request.headers()["idempotency-key"] ?? "";
    const body = request.postDataJSON() as ExpenseClaimActionDto & { submissionId?: string };
    expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
    actionWrites.push({ company, path: url.pathname, operation, body });
    if (pendingWrite) {
      const pending = pendingWrite;
      pendingWrite = null;
      pending.enter();
      await pending.held;
    }
    if (rejection) {
      const failure = rejection;
      rejection = null;
      return route.fulfill({
        status: failure.status,
        json: { code: failure.code, detail: "PRIVATE EMPLOYEE DATA" },
      });
    }
    const key = `${company}:${operation}`,
      replay = receipts.get(key);
    if (replay) {
      expect(replay.path).toBe(url.pathname);
      expect(replay.body).toBe(JSON.stringify(body));
      return route.fulfill({ json: { id: replay.id, version: replay.version } });
    }
    const record = api.records
      .get(company)
      ?.find((candidate) =>
        kind === "withdraw" ? candidate.claim.latestSubmissionId === id : candidate.claim.id === id,
      );
    if (!record) return route.fulfill({ status: 404, json: { code: "expense_claim_not_found" } });
    const claim = record.claim;
    if (claim.version !== body.expectedVersion)
      return route.fulfill({ status: 409, json: { code: "stale_version" } });
    if (
      (kind === "submit" && claim.status !== "DRAFT") ||
      (kind === "cancel" && !["DRAFT", "RETURNED"].includes(claim.status)) ||
      (kind === "withdraw" && claim.status !== "PENDING")
    )
      return route.fulfill({ status: 409, json: { code: "expense_not_editable" } });
    const version = claim.version + 1;
    const status = kind === "submit" ? "PENDING" : kind === "cancel" ? "CANCELLED" : "DRAFT";
    if (kind === "submit") {
      if (!body.submissionId) throw new Error("Submission identity is required");
      expect(body.submissionId).toMatch(/^[a-f0-9-]{36}$/u);
      const number = claim.submissionCount + 1;
      record.submissions.items.unshift({
        id: body.submissionId,
        number,
        draftRevision: claim.draftRevision,
        approvalId: expenseIdentifier(8000 + number, company === companyIds[0] ? 0 : 1),
        title: claim.draft.title,
        totalAmount: claim.draft.totalAmount,
        currency: "IDR",
        submittedBy: claim.createdBy,
        submittedAt: "2026-10-13T12:00:00Z",
      });
      claim.latestSubmissionId = body.submissionId;
      claim.submissionCount = number;
    }
    claim.version = version;
    claim.status = status;
    record.history.items.unshift({
      version,
      status,
      draftRevision: claim.draftRevision,
      kind: kind === "submit" ? "SUBMITTED" : kind === "cancel" ? "CANCELLED" : "WITHDRAWN",
      actorId: claim.createdBy,
      reason: body.reason,
      recordedAt: "2026-10-13T12:00:00Z",
      submissionId: kind === "cancel" ? null : claim.latestSubmissionId,
    });
    receipts.set(key, { id: claim.id, version, path: url.pathname, body: JSON.stringify(body) });
    commits++;
    if (lose) {
      lose = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: { id: claim.id, version } });
  });
  return {
    ...api,
    actionWrites,
    get actionCommits() {
      return commits;
    },
    loseNextAction: () => {
      lose = true;
    },
    rejectAction: (code: string, status = 409) => {
      rejection = { code, status };
    },
    holdAction: () => {
      const pending = gate();
      pendingWrite = pending;
      return pending;
    },
  };
}
