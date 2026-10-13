import { expect, type Page } from "@playwright/test";
import {
  expenseClaimRecord,
  expenseClaimReviewPage,
  expenseIdentifier,
  expenseReceiptBytes,
  expenseSubmissionRecord,
} from "./fixtures/expense-claims";
import { companyIds, installIdentityApi } from "./identity-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
type Kind = "list" | "review" | "submission" | "receipt";
export async function installExpenseClaimsApi(
  page: Page,
  permissions = ["expenses.read", "approvals.read"],
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const records = new Map(
    companyIds.map((company, index) => [
      company as string,
      Array.from({ length: index === 0 ? 23 : 1 }, (_, row) =>
        expenseClaimRecord(index, row + 1, row === 0 ? 23 : 2),
      ),
    ]),
  );
  const reads: { kind: Kind; url: URL }[] = [];
  let rejected: { kind: Kind; code: string } | null = null;
  let malformed = false;
  let held: {
    kind: Kind;
    entered: ReturnType<typeof gate>;
    released: ReturnType<typeof gate>;
  } | null = null;
  await page.route("**/api/v1/companies/*/expenses/**", async (route) => {
    expect(route.request().method()).toBe("GET");
    const url = new URL(route.request().url()),
      path = url.pathname.split("/"),
      company = path[4] ?? "",
      id = path[7];
    const kind: Kind =
      path[8] === "receipts"
        ? "receipt"
        : path[6] === "submissions"
          ? "submission"
          : id
            ? "review"
            : "list";
    reads.push({ kind, url });
    const rejection = rejected?.kind === kind ? rejected.code : null;
    if (rejection) rejected = null;
    const wait = held;
    if (wait?.kind === kind) {
      held = null;
      wait.entered.resolve();
      await wait.released.promise;
    }
    if (rejection) {
      return route.fulfill({
        status: 404,
        json: { code: rejection, detail: "PRIVATE TECHNICAL MESSAGE" },
      });
    }
    const available = records.get(company) ?? [];
    if (kind === "list") {
      expect(url.searchParams.get("limit")).toBe("20");
      const status = url.searchParams.get("status"),
        employment = url.searchParams.get("employmentId");
      const filtered = available.filter(
        (record) =>
          (status === null || record.claim.status === status) &&
          (employment === null || record.claim.employmentId === employment),
      );
      const after = url.searchParams.get("after"),
        start = after === null ? 0 : filtered.findIndex((record) => record.claim.id === after) + 1;
      const selected = filtered.slice(start, start + 21),
        items = selected.slice(0, 20).map(({ claim }) => ({
          id: claim.id,
          employmentId: claim.employmentId,
          employeeNumber: claim.draft.employeeNumber,
          employeeName: claim.draft.employeeName,
          title: claim.draft.title,
          totalAmount: claim.draft.totalAmount,
          currency: "IDR",
          createdAt: claim.createdAt,
          status: claim.status,
          version: claim.version,
        }));
      return route.fulfill({
        json: { items, nextCursor: selected.length > 20 ? (items.at(-1)?.id ?? null) : null },
      });
    }
    if (kind === "review") {
      expect(url.searchParams.get("historyLimit")).toBe("20");
      expect(url.searchParams.get("submissionsLimit")).toBe("20");
      const record = available.find((row) => row.claim.id === id);
      if (!record) return route.fulfill({ status: 404, json: { code: "expense_claim_not_found" } });
      const cursor = url.searchParams.get("historyAfter"),
        reply = expenseClaimReviewPage(record, cursor === null ? null : Number(cursor));
      if (malformed) {
        malformed = false;
        return route.fulfill({
          json: {
            ...reply,
            claim: { ...reply.claim, draft: { ...reply.claim.draft, totalAmount: "1.00" } },
          },
        });
      }
      return route.fulfill({ json: reply });
    }
    const index = available.findIndex((row) => row.claim.latestSubmissionId === id);
    if (index < 0)
      return route.fulfill({ status: 404, json: { code: "expense_submission_not_found" } });
    const raw = expenseSubmissionRecord(
      company === companyIds[0] ? 0 : 1,
      index + 1,
      index === 0 ? 23 : 2,
    );
    raw.duplicateReceiptDigests = ["a".repeat(64)];
    raw.canReadClaim = permissions.some((permission) =>
      ["expenses.read", "expenses.manage", "expenses.team.read", "expenses.self.manage"].includes(
        permission,
      ),
    );
    raw.availableActions = permissions.some((permission) =>
      ["expenses.approve", "expenses.team.approve"].includes(permission),
    )
      ? ["DECIDE"]
      : [];
    if (kind === "submission") return route.fulfill({ json: raw });
    const receipt = raw.lines
      .flatMap((line) => line.receipts)
      .find((row) => row.revisionId === path[9]);
    if (!receipt)
      return route.fulfill({ status: 404, json: { code: "expense_receipt_not_found" } });
    return route.fulfill({
      status: 200,
      headers: {
        "Content-Type": receipt.mediaType,
        "Content-Length": String(receipt.size),
        ETag: `"${receipt.revisionId}-${receipt.sha256}"`,
      },
      body: Buffer.from(expenseReceiptBytes),
    });
  });
  await page.route("**/api/v1/companies/*/approvals/**", async (route) => {
    const path = new URL(route.request().url()).pathname.split("/"),
      company = path[4],
      index = company === companyIds[0] ? 0 : 1;
    const submission = expenseSubmissionRecord(index),
      approval = submission.approval;
    if (path[6] !== approval.id)
      return route.fulfill({ status: 404, json: { code: "approval_not_found" } });
    return route.fulfill({
      json: {
        ...approval,
        kind: "EXPENSE",
        resourceId: submission.id,
        authorId: submission.submittedBy,
        requesterId: submission.requesterId,
        submittedAt: submission.submittedAt,
        stages: approval.stages.map((assignees) => ({ assignees })),
        excludedAccountIds: [expenseIdentifier(200, index)],
      },
    });
  });
  return {
    identity,
    records,
    reads,
    rejectNext: (kind: Kind, code: string) => {
      rejected = { kind, code };
    },
    malformedReview: () => {
      malformed = true;
    },
    holdNext: (kind: Kind) => {
      const entered = gate(),
        released = gate();
      held = { kind, entered, released };
      return { entered: entered.promise, release: released.resolve };
    },
  };
}
