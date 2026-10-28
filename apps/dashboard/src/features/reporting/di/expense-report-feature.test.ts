import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { expenseClaimStatuses } from "../../expenses/domain/entities/expense-claim";
import { expenseAmountView } from "../../expenses/presentation/models/expense-amount-view";
import type { ExpenseReportQuery } from "../domain/entities/expense-report-claim";
import { expenseReportCursorPrefix } from "../domain/policies/expense-report-policy";
import { createReportingFeature } from "./reporting-feature";

const companyId = "77ed1727-6b89-48f9-ae10-f5f3f87c93f0" as CompanyId;
const access = { companyId, permissions: ["reports.read", "expenses.read"] };
const from = "2024-01-01",
  until = "2024-12-31";
const totals = { claims: 3, employments: 2, lines: 7, amount: "1000000000000.03" };
const metadata = {
  companyId,
  from,
  until,
  evaluatedAt: "2026-10-01T00:00:00Z",
  definitionVersion: "expenses.v1",
  currency: "IDR",
};
const dto = {
  ...metadata,
  totals,
  statuses: expenseClaimStatuses.map((status) => ({
    status,
    counts:
      status === "PENDING"
        ? { claims: 1, employments: 1, lines: 1, amount: "0.01" }
        : status === "APPROVED"
          ? { claims: 2, employments: 2, lines: 6, amount: "1000000000000.02" }
          : { claims: 0, employments: 0, lines: 0, amount: "0.00" },
  })),
};
const query: ExpenseReportQuery = { from, until, status: "PENDING", after: null };
const row = {
  id: "ffffffff-ffff-4fff-8fff-ffffffffffff",
  submissionId: "aaffffff-ffff-4fff-8fff-ffffffffffff",
  submissionNumber: 1,
  employeeId: "eeaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  employeeNumber: "E1",
  employeeName: "Example",
  title: "Travel",
  status: "PENDING",
  submittedAt: "2023-12-01T00:00:00.000001Z",
  version: 1,
  lines: 1,
  amount: "0.01",
  submissionAmount: "123.45",
};
const signal = () => new AbortController().signal;

describe("expense report boundaries", () => {
  it("admits only company source/report authority and bounded transaction dates before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createReportingFeature(
      { request },
      {
        downloadBinary: async () => {
          throw new Error("Unexpected file download");
        },
      },
    );
    for (const permissions of [
      [],
      ["reports.read"],
      ["expenses.read"],
      [
        "reports.read",
        "expenses.team.read",
        "expenses.self.manage",
        "expenses.approve",
        "expenses.pay",
      ],
    ]) {
      expect(
        await feature.loadExpenses.execute({ companyId, permissions }, from, until, signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(
        await feature.loadExpenseClaims.execute({ companyId, permissions }, query, signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    }
    for (const [start, end] of [
      [from, "2025-01-01"],
      ["2026-02-29", "2026-03-01"],
      [until, from],
      ["1899-12-31", "1900-01-01"],
      ["2200-12-31", "2201-01-01"],
      ["", until],
    ]) {
      expect(
        await feature.loadExpenses.execute(access, start ?? "", end ?? "", signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_report_range" } });
      expect(
        await feature.loadExpenseClaims.execute(
          access,
          { ...query, from: start ?? "", until: end ?? "" },
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_report_range" } });
    }
    expect(
      await feature.loadExpenseClaims.execute(access, { ...query, after: "unscoped" }, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    expect(request).not.toHaveBeenCalled();
    for (const source of ["expenses.read", "expenses.manage"]) {
      request.mockResolvedValueOnce(dto);
      expect(
        (
          await feature.loadExpenses.execute(
            { companyId, permissions: ["reports.read", source] },
            from,
            until,
            signal(),
          )
        ).ok,
      ).toBe(true);
    }
  });

  it("retains exact aggregate cents above the individual claim limit and rejects inconsistent reports", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const load = createReportingFeature(
      { request },
      {
        downloadBinary: async () => {
          throw new Error("Unexpected file download");
        },
      },
    ).loadExpenses;
    const result = await load.execute(access, from, until, signal());
    expect(result).toEqual({ ok: true, value: dto });
    if (!result.ok) throw new Error("Invalid report fixture");
    expect(Object.isFrozen(result.value.statuses[0]?.counts)).toBe(true);
    for (const changes of [
      { companyId: row.employeeId },
      { from: "2024-01-02" },
      { currency: "USD" },
      { definitionVersion: "future" },
      { statuses: dto.statuses.slice(1) },
      { statuses: dto.statuses.toReversed() },
      { statuses: [dto.statuses[0], dto.statuses[0], ...dto.statuses.slice(2)] },
      ...[
        { claims: 4 },
        { employments: 1 },
        { lines: 8 },
        { lines: 61 },
        { amount: "1000000000000.031" },
        { amount: "1000000000000.04" },
        { amount: "-0.01" },
        { amount: "1e12" },
        { amount: "3000000000000.00" },
        { claims: Number.MAX_SAFE_INTEGER + 1 },
      ].map((change) => ({ totals: { ...totals, ...change } })),
    ]) {
      request.mockResolvedValueOnce({ ...dto, ...changes });
      expect(await load.execute(access, from, until, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    expect(expenseAmountView("9007199254740993.01", "en")).toContain("9,007,199,254,740,993.01");
    expect(expenseAmountView("9007199254740993.01", "id")).toContain("9.007.199.254.740.993,01");
  });

  it("validates frozen submission references, clipped amounts, exact ordering and bounded continuation progress", async () => {
    const page = {
      ...metadata,
      status: "PENDING",
      items: [
        row,
        {
          ...row,
          id: "11111111-1111-4111-8111-111111111111",
          submissionId: "22111111-1111-4111-8111-111111111111",
          submittedAt: "2023-12-01T00:00:00Z",
        },
      ],
      nextCursor: null,
    };
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(page);
    const load = createReportingFeature(
      { request },
      {
        downloadBinary: async () => {
          throw new Error("Unexpected file download");
        },
      },
    ).loadExpenseClaims;
    expect(await load.execute(access, query, signal())).toEqual({ ok: true, value: page });
    expect(request.mock.lastCall?.[0].path).toContain(
      "/reports/expenses/claims?from=2024-01-01&until=2024-12-31&limit=50&status=PENDING",
    );
    for (const changes of [
      { companyId: row.employeeId },
      { currency: "USD" },
      { status: null },
      { items: page.items.toReversed() },
      { items: [row, row] },
      { items: [row, { ...page.items[1], submissionId: row.submissionId }] },
      ...[
        { amount: "123.46" },
        { amount: "0" },
        { lines: 2 },
        { lines: 21 },
        { submissionAmount: "1000000000000.00" },
        { submissionNumber: 0 },
        { submissionNumber: 21 },
        { status: "APPROVED" },
        { title: " " },
      ].map((change) => ({ items: [{ ...row, ...change }] })),
      { nextCursor: "wrong" },
      { items: Array.from({ length: 51 }, () => row) },
    ]) {
      request.mockResolvedValueOnce({ ...page, ...changes });
      expect(await load.execute(access, query, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    const items = Array.from({ length: 50 }, (_, index) => ({
      ...row,
      id: `00000000-0000-4000-8000-${String(1000 - index).padStart(12, "0")}`,
      submissionId: `10000000-0000-4000-8000-${String(1000 - index).padStart(12, "0")}`,
    }));
    const last = items.at(-1);
    const nextCursor = `${expenseReportCursorPrefix(companyId, query)}${last?.submittedAt}|${last?.id}`;
    request.mockResolvedValueOnce({ ...page, items, nextCursor });
    expect(await load.execute(access, query, signal())).toMatchObject({
      ok: true,
      value: { nextCursor },
    });
    request.mockResolvedValueOnce({
      ...page,
      items: [{ ...row, id: "00000000-0000-4000-8000-000000000950" }],
      nextCursor: null,
    });
    expect(await load.execute(access, { ...query, after: nextCursor }, signal())).toMatchObject({
      ok: true,
    });
    request.mockResolvedValueOnce({ ...page, items: [last], nextCursor: null });
    expect(await load.execute(access, { ...query, after: nextCursor }, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
    request.mockClear();
    for (const after of [
      nextCursor.replace("expenses.v1|", "1|"),
      nextCursor.replace(companyId, row.employeeId),
      nextCursor.replace("PENDING", "APPROVED"),
    ])
      expect(await load.execute(access, { ...query, after }, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_page" },
      });
    expect(request).not.toHaveBeenCalled();
  });

  it("sanitizes failures and preserves cancellation without hidden retries on either projection", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(403, {
        code: "company_module_disabled",
        parameters: { module: "REPORTING" },
        detail: "Private report diagnostic",
      }),
    );
    const feature = createReportingFeature(
      { request },
      {
        downloadBinary: async () => {
          throw new Error("Unexpected file download");
        },
      },
    );
    const failed = await feature.loadExpenses.execute(access, from, until, signal());
    expect(failed).toMatchObject({ ok: false, failure: { code: "company_module_disabled" } });
    expect(JSON.stringify(failed)).not.toContain("Private report diagnostic");
    expect(request).toHaveBeenCalledTimes(1);
    const owner = new AbortController();
    request.mockImplementationOnce(async () => {
      owner.abort();
      return { ...metadata, status: query.status, items: [row], nextCursor: null };
    });
    await expect(
      feature.loadExpenseClaims.execute(access, query, owner.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(() => feature.loadExpenses.execute(access, from, until, owner.signal)).toThrow();
  });
});
