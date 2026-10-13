import { expect, type Page } from "@playwright/test";
import type { ExpenseDecisionDto } from "../src/features/expenses/data/models/expense-decision-dto";
import { installExpenseClaimsApi } from "./expense-claims-api";
import { expenseIdentifier, expenseSubmissionRecord } from "./fixtures/expense-claims";
import { companyIds } from "./identity-api";

function gate() {
  let enter!: () => void, release!: () => void;
  return {
    started: new Promise<void>((done) => {
      enter = done;
    }),
    held: new Promise<void>((done) => {
      release = done;
    }),
    enter: () => enter(),
    release: () => release(),
  };
}

/** Models transport outcomes only. API integration covers authorization and persisted transitions. */
export async function installExpenseDecisionsApi(
  page: Page,
  permissions = ["expenses.approve", "approvals.read"],
) {
  const base = await installExpenseClaimsApi(page, permissions);
  const record = expenseSubmissionRecord();
  record.canReadClaim = permissions.includes("expenses.read");
  record.availableActions = permissions.includes("expenses.approve") ? ["DECIDE"] : [];
  const reads: URL[] = [],
    writes: { operation: string; body: ExpenseDecisionDto }[] = [];
  const receipts = new Map<string, { payload: string; version: number }>();
  let commits = 0,
    lose = false;
  let rejected: { code: string; status: number } | null = null;
  let heldRead: ReturnType<typeof gate> | null = null;
  let heldWrite: ReturnType<typeof gate> | null = null;
  await page.route(
    `**/api/v1/companies/${companyIds[0]}/expenses/submissions/${record.id}**`,
    async (route) => {
      const request = route.request(),
        url = new URL(request.url());
      if (url.pathname.includes("/receipts/")) return route.fallback();
      if (request.method() === "GET") {
        reads.push(url);
        const snapshot = structuredClone(record);
        if (heldRead) {
          const held = heldRead;
          heldRead = null;
          held.enter();
          await held.held;
        }
        return route.fulfill({ json: snapshot });
      }
      expect(request.method()).toBe("POST");
      expect(url.pathname.endsWith("/decisions")).toBe(true);
      const operation = request.headers()["idempotency-key"] ?? "";
      expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
      const body = request.postDataJSON() as ExpenseDecisionDto;
      writes.push({ operation, body });
      if (heldWrite) {
        const held = heldWrite;
        heldWrite = null;
        held.enter();
        await held.held;
      }
      const failure = (code: string, status = 409) =>
        route.fulfill({
          status,
          json: { code, detail: "PRIVATE TECHNICAL MESSAGE", fields: {}, parameters: {} },
        });
      if (rejected) {
        const rejection = rejected;
        rejected = null;
        return failure(rejection.code, rejection.status);
      }
      const payload = JSON.stringify(body),
        previous = receipts.get(operation);
      if (previous)
        return previous.payload === payload
          ? route.fulfill({ json: { id: record.claimId, version: previous.version } })
          : failure("operation_payload_mismatch");
      if (record.claimVersion !== body.expectedVersion) return failure("stale_version");
      if (record.approval.version !== body.expectedApprovalVersion)
        return failure("approval_changed");
      const step = record.approval.currentStep;
      if (body.decision === "RETURN") {
        record.claimStatus = "RETURNED";
        record.approval.status = "REJECTED";
      } else if (body.decision === "REJECT") {
        record.claimStatus = "REJECTED";
        record.approval.status = "REJECTED";
      } else if (step + 1 < record.approval.stages.length) record.approval.currentStep += 1;
      else {
        record.claimStatus = "APPROVED";
        record.approval.status = "APPROVED";
      }
      record.claimVersion += 1;
      record.approval.version += 1;
      record.availableActions = [];
      record.reviews.push({
        claimVersion: record.claimVersion,
        approvalVersion: record.approval.version,
        step,
        actorId: expenseIdentifier(900),
        decidingFor: expenseIdentifier(900),
        decision: body.decision,
        status: record.claimStatus,
        reason: body.reason,
        duplicateDigests: [...record.duplicateReceiptDigests],
        duplicatesAcknowledged: body.acknowledgeDuplicates,
        decidedAt: "2026-10-13T12:00:00Z",
      });
      commits += 1;
      receipts.set(operation, { payload, version: record.claimVersion });
      if (lose) {
        lose = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: { id: record.claimId, version: record.claimVersion } });
    },
  );
  return {
    ...base,
    record,
    decisionReads: reads,
    writes,
    get commits() {
      return commits;
    },
    loseNext: () => {
      lose = true;
    },
    rejectWrite: (code: string, status = 409) => {
      rejected = { code, status };
    },
    holdDecisionRead: () => {
      const held = gate();
      heldRead = held;
      return held;
    },
    holdWrite: () => {
      const held = gate();
      heldWrite = held;
      return held;
    },
  };
}
