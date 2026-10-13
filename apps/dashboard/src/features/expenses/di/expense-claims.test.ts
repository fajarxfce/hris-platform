import { assert, describe, expect, it, vi } from "vitest";
import {
  expenseClaimRecord,
  expenseClaimReviewPage,
  expenseIdentifier,
  expenseReceiptBytes,
  expenseSubmissionRecord,
} from "../../../../tests/fixtures/expense-claims";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { success } from "../../../core/domain/result";
import { toExpenseSubmission } from "../data/mappers/expense-submission-mapper";
import type { ExpenseClaimReviewDto } from "../data/models/expense-claim-dto";
import type { ExpenseSubmissionDto } from "../data/models/expense-submission-dto";
import type { ExpenseSubmissionId } from "../domain/entities/expense-submission-summary";
import { createExpenseFeature } from "./expense-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId: company, permissions: ["expenses.read"] };
const search = {
  from: "2026-10-01",
  until: "2026-10-31",
  status: null,
  employmentId: null,
  after: null,
};
const signal = () => new AbortController().signal;
const submission = () =>
  toExpenseSubmission(
    expenseSubmissionRecord(),
    company,
    expenseIdentifier(1001) as ExpenseSubmissionId,
  );
function fixture() {
  const request = vi.fn(),
    downloadBinary = vi.fn().mockResolvedValue(success(undefined));
  return {
    request,
    downloadBinary,
    feature: createExpenseFeature({ request }, { downloadBinary }),
  };
}

describe("expense claim boundaries", () => {
  it("accepts the server's full employee and cost-center name lengths", async () => {
    const f = fixture(),
      claim = expenseClaimReviewPage(expenseClaimRecord()),
      submitted = expenseSubmissionRecord();
    claim.claim.draft.employeeName = "N".repeat(200);
    submitted.employeeName = "N".repeat(200);
    const costCenter = submitted.lines[0]?.costCenter;
    if (!costCenter) throw new Error("Missing cost center fixture");
    costCenter.name = "C".repeat(200);
    f.request.mockResolvedValueOnce(claim).mockResolvedValueOnce(submitted);
    expect(await f.feature.loadClaim.execute(access, claim.claim.id, null, signal())).toMatchObject(
      { ok: true, value: { claim: { draft: { employeeName: "N".repeat(200) } } } },
    );
    const result = await f.feature.loadSubmission.execute(access, submitted.id, signal());
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.value.lines[0]?.costCenter?.name).toBe("C".repeat(200));
  });

  it("requires expense authority, valid resource ids and a bounded selected scope before HTTP", async () => {
    const f = fixture();
    for (const permissions of [
      [],
      ["expenses.pay"],
      ["expenses.policy.manage"],
      ["approvals.read"],
    ]) {
      expect(
        await f.feature.loadClaims.execute({ companyId: company, permissions }, search, signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(
        await f.feature.loadClaim.execute(
          { companyId: company, permissions },
          expenseIdentifier(1),
          null,
          signal(),
        ),
      ).toMatchObject({ ok: false });
    }
    expect(
      await f.feature.loadClaims.execute(
        { ...access, permissions: ["expenses.team.read"] },
        search,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "employee_scope_required" } });
    for (const changes of [
      { until: "2027-10-02" },
      { from: "2026-02-30" },
      { status: "UNKNOWN" },
      { after: "../private" },
      { employmentId: "bad" },
    ])
      expect(
        await f.feature.loadClaims.execute(access, { ...search, ...changes }, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_expense_page" } });
    expect(await f.feature.loadClaim.execute(access, "../other", null, signal())).toMatchObject({
      ok: false,
    });
    expect(
      await f.feature.loadClaim.execute(access, expenseIdentifier(1), "9007199254740992", signal()),
    ).toMatchObject({ ok: false });
    expect(await f.feature.loadSubmission.execute(access, "../private", signal())).toMatchObject({
      ok: false,
    });
    expect(f.request).not.toHaveBeenCalled();
  });
  it("reads the current draft coherently while history advances with its own cursor", async () => {
    const f = fixture(),
      record = expenseClaimRecord();
    f.request
      .mockResolvedValueOnce(expenseClaimReviewPage(record))
      .mockResolvedValueOnce(expenseClaimReviewPage(record, 4));
    const first = await f.feature.loadClaim.execute(access, record.claim.id, null, signal());
    expect(first.ok).toBe(true);
    if (!first.ok) return;
    expect(first.value.history.items.map((item) => item.version)).toEqual(
      Array.from({ length: 20 }, (_, i) => 23 - i),
    );
    expect(first.value.history.nextCursor).toBe("4");
    expect(first.value.claim.draft.totalAmount).toBe("150000.75");
    const last = await f.feature.loadClaim.execute(access, record.claim.id, "4", signal());
    expect(last.ok).toBe(true);
    if (!last.ok) return;
    expect(last.value.claim).toEqual(first.value.claim);
    expect(last.value.history.items.map((item) => item.version)).toEqual([3, 2, 1, 0]);
    expect(last.value.history.nextCursor).toBeNull();
    expect(f.request.mock.calls[1]?.[0].path).toBe(
      `/api/v1/companies/${company}/expenses/claims/${record.claim.id}/review?historyLimit=20&submissionsLimit=20&historyAfter=4`,
    );
    expect(Object.isFrozen(first.value.claim.draft.lines[0]?.receiptRevisionIds)).toBe(true);
    expect(Object.isFrozen(first.value.history.items)).toBe(true);
    record.claim.draft.title = "Mutable transport";
    expect(first.value.claim.draft.title).not.toBe("Mutable transport");
  });
  it.each([
    [
      "wrong claim",
      (r: ExpenseClaimReviewDto) => {
        r.claim.id = expenseIdentifier(2);
      },
    ],
    [
      "wrong current draft",
      (r: ExpenseClaimReviewDto) => {
        r.claim.draft.revision++;
      },
    ],
    [
      "inconsistent total",
      (r: ExpenseClaimReviewDto) => {
        r.claim.draft.totalAmount = "150000.74";
      },
    ],
    [
      "duplicate items",
      (r: ExpenseClaimReviewDto) => {
        const line = r.claim.draft.lines[0];
        if (line) r.claim.draft.lines.push(line);
      },
    ],
    [
      "history gap",
      (r: ExpenseClaimReviewDto) => {
        r.history.items.splice(2, 1);
      },
    ],
    [
      "head history mismatch",
      (r: ExpenseClaimReviewDto) => {
        const row = r.history.items[0];
        if (row) row.status = "APPROVED";
      },
    ],
    [
      "cursor loop",
      (r: ExpenseClaimReviewDto) => {
        r.history.nextCursor = "23";
      },
    ],
    [
      "missing earlier history",
      (r: ExpenseClaimReviewDto) => {
        r.history.nextCursor = null;
      },
    ],
    [
      "foreign submission",
      (r: ExpenseClaimReviewDto) => {
        const row = r.submissions.items[0];
        if (row) row.id = expenseIdentifier(9);
      },
    ],
    [
      "incomplete submissions",
      (r: ExpenseClaimReviewDto) => {
        r.submissions.items = [];
      },
    ],
    [
      "payment without release",
      (r: ExpenseClaimReviewDto) => {
        r.payments.push({
          batchId: expenseIdentifier(80),
          itemId: expenseIdentifier(81),
          status: "PENDING",
          createdAt: "2026-10-13T01:00:00Z",
          releasedAt: null,
          settledAt: null,
        });
      },
    ],
  ] as const)("rejects %s instead of exposing a partial review", async (_name, mutate) => {
    const f = fixture(),
      raw = expenseClaimReviewPage(expenseClaimRecord());
    mutate(raw);
    f.request.mockResolvedValue(raw);
    expect(
      await f.feature.loadClaim.execute(access, expenseIdentifier(1), null, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("keeps collection filtering and pagination bound to the request", async () => {
    const f = fixture(),
      record = expenseClaimRecord();
    const row = {
      id: record.claim.id,
      employmentId: record.claim.employmentId,
      employeeNumber: record.claim.draft.employeeNumber,
      employeeName: record.claim.draft.employeeName,
      title: record.claim.draft.title,
      totalAmount: "150000.75",
      currency: "IDR",
      createdAt: record.claim.createdAt,
      status: "PENDING",
      version: record.claim.version,
    };
    f.request.mockResolvedValue({ items: [row], nextCursor: null });
    expect(
      await f.feature.loadClaims.execute(
        access,
        { ...search, status: "PENDING", employmentId: row.employmentId },
        signal(),
      ),
    ).toMatchObject({ ok: true });
    expect(f.request.mock.calls[0]?.[0].path).toBe(
      `/api/v1/companies/${company}/expenses/claims?from=2026-10-01&until=2026-10-31&limit=20&status=PENDING&employmentId=${row.employmentId}`,
    );
    for (const body of [
      { items: [row, row], nextCursor: null },
      { items: [row], nextCursor: row.id },
      { items: [{ ...row, status: "APPROVED" }], nextCursor: null },
      { items: [{ ...row, employmentId: expenseIdentifier(777) }], nextCursor: null },
    ]) {
      f.request.mockResolvedValue(body);
      expect(
        await f.feature.loadClaims.execute(
          access,
          { ...search, status: "PENDING", employmentId: row.employmentId },
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });
  it("accepts immutable category revisions and keeps submission evidence frozen", async () => {
    const f = fixture(),
      raw = expenseSubmissionRecord();
    f.request.mockResolvedValue(raw);
    const result = await f.feature.loadSubmission.execute(
      { ...access, permissions: ["expenses.approve"] },
      raw.id,
      signal(),
    );
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.lines[0]?.category.revision).toBe(1);
    expect(result.value.lines[0]?.category.maximumLineAmount).toBe("500000.25");
    expect(Object.isFrozen(result.value.approval.stages[0])).toBe(true);
    expect(Object.isFrozen(result.value.lines[0]?.receipts)).toBe(true);
    assert(raw.lines[0]);
    raw.lines[0].category.name = "Changed policy";
    expect(result.value.lines[0]?.category.name).toBe("Business travel");
    raw.current = true;
    raw.claimStatus = "DRAFT";
    raw.approval.status = "CANCELLED";
    raw.availableActions = [];
    const withdrawn = await f.feature.loadSubmission.execute(access, raw.id, signal());
    expect(withdrawn).toMatchObject({
      ok: true,
      value: { current: true, claimStatus: "DRAFT", approval: { status: "CANCELLED" } },
    });
  });
  it.each([
    [
      "mismatched identity",
      (r: ExpenseSubmissionDto) => {
        r.id = expenseIdentifier(2);
      },
    ],
    [
      "wrong sum",
      (r: ExpenseSubmissionDto) => {
        r.totalAmount = "1.00";
      },
    ],
    [
      "duplicate line",
      (r: ExpenseSubmissionDto) => {
        assert(r.lines[0]);
        r.lines.push(r.lines[0]);
      },
    ],
    [
      "duplicate assignment",
      (r: ExpenseSubmissionDto) => {
        const stage = r.approval.stages[0];
        assert(stage?.[0]);
        stage.push(stage[0]);
      },
    ],
    [
      "invalid approval step",
      (r: ExpenseSubmissionDto) => {
        r.approval.currentStep = 1;
      },
    ],
    [
      "foreign digest",
      (r: ExpenseSubmissionDto) => {
        r.duplicateReceiptDigests = ["b".repeat(64)];
      },
    ],
    [
      "unsafe receipt path",
      (r: ExpenseSubmissionDto) => {
        const receipt = r.lines[0]?.receipts[0];
        assert(receipt);
        receipt.fileName = "../private.pdf";
      },
    ],
    [
      "unsupported receipt",
      (r: ExpenseSubmissionDto) => {
        const receipt = r.lines[0]?.receipts[0];
        assert(receipt);
        receipt.mediaType = "application/octet-stream" as "application/pdf";
      },
    ],
  ] as const)("rejects submission %s", async (_name, mutate) => {
    const f = fixture(),
      raw = expenseSubmissionRecord();
    mutate(raw);
    f.request.mockResolvedValue(raw);
    expect(
      await f.feature.loadSubmission.execute(access, expenseIdentifier(1001), signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("downloads only evidence belonging to this company and immutable submission", async () => {
    const f = fixture(),
      current = submission(),
      receipt = current.lines[0]?.receipts[0];
    assert(receipt);
    const content = {
      parts: [new Uint8Array([...expenseReceiptBytes]).buffer],
      byteLength: receipt.size,
      mediaType: receipt.mediaType,
      etag: `"${receipt.revisionId}-${receipt.sha256}"`,
    };
    f.request.mockResolvedValue(content);
    expect(
      await f.feature.downloadReceipt.execute(
        { ...access, companyId: expenseIdentifier(2) as CompanyId },
        current,
        receipt.revisionId,
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(
      await f.feature.downloadReceipt.execute(access, current, expenseIdentifier(999), signal()),
    ).toMatchObject({ ok: false });
    expect(f.request).not.toHaveBeenCalled();
    expect(
      await f.feature.downloadReceipt.execute(access, current, receipt.revisionId, signal()),
    ).toEqual(success(undefined));
    expect(f.request.mock.calls[0]?.[0]).toMatchObject({
      path: `/api/v1/companies/${company}/expenses/submissions/${current.id}/receipts/${receipt.revisionId}/content`,
      timeoutMilliseconds: 60_000,
      response: { type: "binary", byteLength: receipt.size, etag: content.etag },
    });
    expect(f.downloadBinary.mock.calls[0]?.[0]).toMatchObject({
      name: "train-receipt.pdf",
      byteLength: expenseReceiptBytes.byteLength,
      mediaType: "application/pdf",
    });
  });
  it("does not hand mismatched bytes to browser storage and preserves safe server failure codes", async () => {
    const f = fixture(),
      current = submission(),
      receipt = current.lines[0]?.receipts[0];
    assert(receipt);
    const content = {
      parts: [new Uint8Array([...expenseReceiptBytes]).buffer],
      byteLength: receipt.size,
      mediaType: receipt.mediaType,
      etag: `"${receipt.revisionId}-${receipt.sha256}"`,
    };
    for (const changed of [
      { byteLength: receipt.size - 1 },
      { etag: '"changed"' },
      { mediaType: "image/png" },
      { parts: [new ArrayBuffer(1)] },
    ]) {
      f.request.mockResolvedValue({ ...content, ...changed });
      expect(
        await f.feature.downloadReceipt.execute(access, current, receipt.revisionId, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    f.request.mockRejectedValue(
      new HttpResponseError(404, {
        code: "expense_receipt_not_found",
        detail: "PRIVATE TECHNICAL TEXT",
      }),
    );
    const denied = await f.feature.downloadReceipt.execute(
      access,
      current,
      receipt.revisionId,
      signal(),
    );
    expect(denied).toMatchObject({ ok: false, failure: { code: "expense_receipt_not_found" } });
    expect(JSON.stringify(denied)).not.toContain("PRIVATE");
    expect(f.downloadBinary).not.toHaveBeenCalled();
  });
  it("propagates cancellation after acquisition before a native download is started", async () => {
    const f = fixture(),
      current = submission(),
      receipt = current.lines[0]?.receipts[0],
      abort = new AbortController();
    assert(receipt);
    f.request.mockImplementation(async () => {
      abort.abort();
      return {
        parts: [new Uint8Array([...expenseReceiptBytes]).buffer],
        byteLength: receipt.size,
        mediaType: receipt.mediaType,
        etag: `"${receipt.revisionId}-${receipt.sha256}"`,
      };
    });
    await expect(
      f.feature.downloadReceipt.execute(access, current, receipt.revisionId, abort.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(f.downloadBinary).not.toHaveBeenCalled();
  });
});
