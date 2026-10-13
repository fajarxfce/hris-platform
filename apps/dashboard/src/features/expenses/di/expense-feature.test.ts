import { describe, expect, it, vi } from "vitest";
import {
  expensePolicyId,
  expensePolicyRecord,
  expensePolicyReviewPage,
} from "../../../../tests/fixtures/expense-policies";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { ExpensePolicyReviewDto } from "../data/models/expense-policy-dto";
import { expenseAmountView } from "../presentation/models/expense-policy-view";
import { createExpenseFeature } from "./expense-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["expenses.policy.manage"] };
const query = { active: null, after: null };
const signal = () => new AbortController().signal;
const operation = "ba000000-0000-4000-8000-000000000001" as OperationId;
const change = { ...expensePolicyRecord().current, expectedVersion: 2, reason: "Reviewed limits" };

describe("expense policy boundary", () => {
  it("rejects readers, malformed identifiers and unbounded cursors before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      feature = createExpenseFeature({ request });
    for (const permissions of [
      [],
      ["expenses.read"],
      ["expenses.self.manage"],
      ["expenses.manage"],
    ]) {
      const actor = { ...access, permissions };
      expect(await feature.loadPolicies.execute(actor, query, signal())).toMatchObject({
        ok: false,
        failure: { code: "access_denied" },
      });
      expect(await feature.loadPolicy.execute(actor, change.id, null, signal())).toMatchObject({
        ok: false,
        failure: { code: "access_denied" },
      });
      expect(await feature.savePolicy.execute(actor, operation, change, signal())).toMatchObject({
        ok: false,
        failure: { code: "access_denied" },
      });
    }
    for (const after of ["", "A", "lower", "A-B", "../foreign", "A".repeat(33)])
      expect(
        await feature.loadPolicies.execute(access, { active: null, after }, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    for (const after of ["-1", "01", "1000", "1.5", "Infinity", "", "../other"])
      expect(await feature.loadPolicy.execute(access, change.id, after, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_page" },
      });
    expect(await feature.loadPolicy.execute(access, "../other", null, signal())).toMatchObject({
      ok: false,
      failure: { code: "expense_category_not_found" },
    });
    expect(request).not.toHaveBeenCalled();
  });

  it("reads bounded saved heads and ascending history without replacing the current terms", async () => {
    const raw = expensePolicyRecord(0, 1, 23),
      request = vi.fn<HttpClient["request"]>();
    const feature = createExpenseFeature({ request });
    request.mockResolvedValueOnce({ items: [raw.current], nextCursor: null });
    expect(
      await feature.loadPolicies.execute(access, { active: "false", after: "EARLIER" }, signal()),
    ).toMatchObject({ ok: true, value: { items: [{ version: 23, companyId, active: false }] } });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/expenses/policies?limit=20&active=false&after=EARLIER`,
    );
    request.mockResolvedValueOnce(expensePolicyReviewPage(raw));
    const first = await feature.loadPolicy.execute(access, change.id.toUpperCase(), null, signal());
    expect(first).toMatchObject({
      ok: true,
      value: { current: { version: 23 }, history: { nextCursor: "19" } },
    });
    request.mockResolvedValueOnce(expensePolicyReviewPage(raw, 19));
    const last = await feature.loadPolicy.execute(access, change.id, "19", signal());
    expect(request.mock.lastCall?.[0].path).toContain("historyLimit=20&historyAfter=19");
    if (!first.ok || !last.ok) throw new Error("Expected policy pages");
    expect(last.value.current).toEqual(first.value.current);
    expect(last.value.history.items.map((row) => row.revision)).toEqual([20, 21, 22, 23]);
    raw.current.allowedContracts.splice(0);
    raw.history.items[0]?.category.allowedContracts.splice(0);
    expect(first.value.current.allowedContracts).toHaveLength(2);
    expect(first.value.history.items[0]?.allowedContracts).toHaveLength(2);
    expect(Object.isFrozen(first.value.history.items[0])).toBe(true);
  });

  it.each<[string, (raw: ExpensePolicyReviewDto) => void]>([
    [
      "foreign head",
      (raw) => {
        raw.current.id = expensePolicyId(1);
      },
    ],
    [
      "effective snapshot",
      (raw) => {
        raw.current.appliedRevision = 0;
      },
    ],
    [
      "invalid date",
      (raw) => {
        raw.current.effectiveFrom = "2026-02-30";
      },
    ],
    [
      "duplicate contracts",
      (raw) => {
        raw.current.allowedContracts = ["PERMANENT", "PERMANENT"];
      },
    ],
    [
      "excess decimal places",
      (raw) => {
        raw.current.maximumLineAmount = "1.001";
      },
    ],
    [
      "zero amount",
      (raw) => {
        raw.current.maximumLineAmount = "0.00";
      },
    ],
    [
      "claim below line",
      (raw) => {
        raw.current.maximumClaimAmount = "1.00";
      },
    ],
    [
      "history gap",
      (raw) => {
        raw.history.items.splice(1, 1);
      },
    ],
    [
      "truncated history",
      (raw) => {
        raw.history.items.pop();
      },
    ],
    [
      "foreign revision",
      (raw) => {
        const row = raw.history.items[0];
        if (row) row.category.id = expensePolicyId(1);
      },
    ],
    [
      "mixed head versions",
      (raw) => {
        const row = raw.history.items[0];
        if (row) row.category.version = 1;
      },
    ],
    [
      "invalid recorded instant",
      (raw) => {
        const row = raw.history.items[0];
        if (row) row.recordedAt = "not-an-instant";
      },
    ],
    [
      "false continuation",
      (raw) => {
        raw.history.nextCursor = "2";
      },
    ],
  ])("rejects a malformed %s", async (_, mutate) => {
    const raw = expensePolicyRecord();
    mutate(raw);
    const feature = createExpenseFeature({ request: vi.fn().mockResolvedValue(raw) });
    expect(await feature.loadPolicy.execute(access, change.id, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });

  it("rejects repeated and backwards catalog cursors", async () => {
    const first = expensePolicyRecord().current,
      second = expensePolicyRecord(0, 2).current;
    const request = vi.fn<HttpClient["request"]>(),
      feature = createExpenseFeature({ request });
    for (const raw of [
      { items: [second, first], nextCursor: null },
      { items: [first, first], nextCursor: null },
      { items: [first], nextCursor: first.code },
    ]) {
      request.mockResolvedValueOnce(raw);
      expect(await feature.loadPolicies.execute(access, query, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    request.mockResolvedValueOnce({ items: [first], nextCursor: null });
    expect(
      await feature.loadPolicies.execute(access, { ...query, after: first.code }, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("keeps exact decimal values through validation, HTTP and localized formatting", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({ id: change.id, version: 3 });
    const feature = createExpenseFeature({ request });
    const result = await feature.savePolicy.execute(
      access,
      operation,
      {
        ...change,
        code: " travel_ui ",
        maximumLineAmount: "999999999999.98",
        maximumClaimAmount: "999999999999.99",
        reason: " Reviewed maximum ",
      },
      signal(),
    );
    expect(result).toMatchObject({ ok: true, value: { version: 3 } });
    expect(request.mock.lastCall?.[0]).toMatchObject({
      method: "PUT",
      operationId: operation,
      path: `/api/v1/companies/${companyId}/expenses/categories/${change.id}`,
      body: {
        maximumLineAmount: "999999999999.98",
        maximumClaimAmount: "999999999999.99",
        code: "TRAVEL_UI",
        reason: "Reviewed maximum",
        expectedVersion: 2,
      },
    });
    expect(expenseAmountView("999999999999.99", "en")).toContain("999,999,999,999.99");
    expect(expenseAmountView("999999999999.99", "id")).toContain("999.999.999.999,99");
    expect(expenseAmountView("0.01", "en")).toContain("0.01");
  });

  it.each(["0", "-1", "1000000000000", "2.001", "1e3", "1,000.00", "NaN", "", "01.00"])(
    "rejects invalid IDR amount %s before HTTP",
    async (maximumLineAmount) => {
      const request = vi.fn<HttpClient["request"]>(),
        feature = createExpenseFeature({ request });
      expect(
        await feature.savePolicy.execute(
          access,
          operation,
          { ...change, maximumLineAmount },
          signal(),
        ),
      ).toMatchObject({
        ok: false,
        failure: {
          code: "invalid_expense_policy",
          fields: { maximumLineAmount: "invalid_amount" },
        },
      });
      expect(request).not.toHaveBeenCalled();
    },
  );

  it("preserves API failure codes and discards data after cancellation", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValueOnce(
        new HttpResponseError(403, { code: "mfa_required", detail: "Private diagnostic" }),
      );
    const feature = createExpenseFeature({ request });
    expect(await feature.loadPolicies.execute(access, query, signal())).toEqual({
      ok: false,
      failure: { code: "mfa_required", fields: {}, parameters: {} },
    });
    const pending = new AbortController();
    request.mockImplementationOnce(async () => {
      pending.abort();
      return { items: [expensePolicyRecord().current], nextCursor: null };
    });
    await expect(
      feature.loadPolicies.execute(access, query, pending.signal),
    ).rejects.toHaveProperty("name", "AbortError");
    request.mockResolvedValueOnce({ id: expensePolicyId(1), version: 3 });
    expect(await feature.savePolicy.execute(access, operation, change, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
});
