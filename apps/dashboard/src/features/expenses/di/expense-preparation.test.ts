import { assert, describe, expect, it, vi } from "vitest";
import { expenseIdentifier } from "../../../../tests/fixtures/expense-claims";
import { expensePayable, expensePayablePage } from "../../../../tests/fixtures/expense-payables";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toExpensePayablePage } from "../data/mappers/expense-payable-mapper";
import type { ExpensePaymentDestination } from "../domain/entities/expense-payment-destination";
import {
  addExpensePaymentSelection,
  expensePaymentPreparationWasRejected,
  reviewExpensePaymentPreparation,
} from "../domain/policies/expense-payment-preparation-policy";
import { createExpenseFeature } from "./expense-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId: company, permissions: ["expenses.pay"] },
  operation = expenseIdentifier(999) as OperationId,
  id = expenseIdentifier(998);
const search = { from: "2026-10-01", until: "2026-10-31", after: null };
const destination: ExpensePaymentDestination = {
  bankCode: "014",
  accountNumber: "001234567890",
  accountName: "Payable Employee 1",
};
const signal = () => new AbortController().signal;
function selection() {
  return toExpensePayablePage(expensePayablePage(), company, search).items.map((payable) => ({
    payable,
    destination,
  }));
}
function review() {
  const result = reviewExpensePaymentPreparation(
    access,
    selection(),
    "  October claims  ",
    "  Bank details verified  ",
  );
  assert(result.ok);
  return result.value;
}
function fixture() {
  const request = vi.fn();
  return {
    request,
    feature: createExpenseFeature({ request }, { downloadBinary: vi.fn(), downloadText: vi.fn() }),
  };
}
describe("expense preparation boundaries", () => {
  it("loads a bounded payable page with current availability and immutable domain rows", async () => {
    const f = fixture(),
      raw = expensePayablePage(20);
    const first = raw.items[0];
    assert(first);
    first.availableActions = [];
    f.request.mockResolvedValue(raw);
    const result = await f.feature.loadPayables.execute(access, search, signal());
    assert(result.ok);
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${company}/expenses/payments/payables?from=2026-10-01&until=2026-10-31&limit=20`,
    });
    expect(result.value.items[0]).toMatchObject({ companyId: company, availableActions: [] });
    expect(result.value.nextCursor).toBe(raw.items.at(-1)?.claimId);
    expect(Object.isFrozen(result.value.items)).toBe(true);
    expect(Object.isFrozen(result.value.items[0]?.availableActions)).toBe(true);
  });
  it("rejects unavailable scope, unbounded dates and invalid cursors before I/O", async () => {
    const f = fixture();
    expect(
      await f.feature.loadPayables.execute(
        { ...access, permissions: ["expenses.manage"] },
        search,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const invalid of [
      { ...search, until: "2028-10-31" },
      { ...search, from: "2026-02-30" },
      { ...search, after: "../payables" },
    ])
      expect(await f.feature.loadPayables.execute(access, invalid, signal())).toMatchObject({
        ok: false,
      });
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each(["duplicate", "order", "amount", "cursor", "limit", "capability", "submission"])(
    "rejects an incoherent payable %s response",
    async (problem) => {
      const f = fixture(),
        raw = expensePayablePage(),
        first = raw.items[0],
        second = raw.items[1];
      assert(first && second);
      if (problem === "duplicate") second.claimId = first.claimId;
      if (problem === "order") raw.items.reverse();
      if (problem === "amount") first.amount = "0.00";
      if (problem === "cursor") raw.nextCursor = first.claimId;
      if (problem === "limit") raw.items = expensePayablePage(21).items;
      if (problem === "capability") first.availableActions = ["PREPARE", "PREPARE"];
      if (problem === "submission") second.submissionId = first.submissionId;
      f.request.mockResolvedValue(raw);
      expect(await f.feature.loadPayables.execute(access, search, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    },
  );
  it("rejects repeated cursors and preserves an approved claim's microsecond order", async () => {
    const f = fixture(),
      raw = expensePayablePage(),
      first = raw.items[0],
      second = raw.items[1];
    assert(first && second);
    first.approvedAt = "2026-10-13T05:00:00.000002Z";
    second.approvedAt = "2026-10-13T05:00:00.000001Z";
    f.request.mockResolvedValue(raw);
    expect((await f.feature.loadPayables.execute(access, search, signal())).ok).toBe(true);
    expect(
      await f.feature.loadPayables.execute(access, { ...search, after: first.claimId }, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("checks scope, server capability, duplicate identity and the 100-claim selection bound", () => {
    const selected = selection(),
      first = selected[0];
    assert(first);
    expect(
      addExpensePaymentSelection(access, [], { ...first.payable, availableActions: [] }).ok,
    ).toBe(false);
    expect(
      addExpensePaymentSelection(
        { ...access, companyId: expenseIdentifier(77) as CompanyId },
        [],
        first.payable,
      ).ok,
    ).toBe(false);
    expect(
      addExpensePaymentSelection(access, selected, {
        ...first.payable,
        claimId: first.payable.claimId.toUpperCase(),
      }),
    ).toMatchObject({ ok: false, failure: { code: "expense_payment_duplicate_claim" } });
    const hundred = Array.from({ length: 100 }, (_, index) => ({
      payable: { ...expensePayable(index), companyId: company },
      destination,
    }));
    expect(
      addExpensePaymentSelection(access, hundred, { ...expensePayable(101), companyId: company }),
    ).toMatchObject({ ok: false, failure: { code: "expense_payment_selection_limit" } });
  });
  it("freezes the human review and sends only original IDs and bank instructions", async () => {
    const f = fixture(),
      current = review(),
      itemIds = [expenseIdentifier(996), expenseIdentifier(997)];
    f.request.mockResolvedValue({ id, version: 0 });
    expect(Object.isFrozen(current.items[0]?.destination)).toBe(true);
    expect(
      await f.feature.preparePayment.execute(access, operation, id, current, itemIds, signal()),
    ).toEqual({ ok: true, value: { id, version: 0 } });
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${company}/expenses/payments/${id}`,
      method: "PUT",
      operationId: operation,
      body: {
        title: "October claims",
        reason: "Bank details verified",
        items: current.items.map((item, index) => ({
          id: itemIds[index],
          submissionId: item.payable.submissionId,
          destination,
        })),
      },
    });
  });
  it("rejects foreign review, invalid or duplicate identifiers before sending", async () => {
    const f = fixture(),
      current = review();
    expect(
      await f.feature.preparePayment.execute(
        access,
        operation,
        id,
        { ...current, companyId: expenseIdentifier(77) as CompanyId },
        [expenseIdentifier(1), expenseIdentifier(2)],
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const itemIds of [
      [],
      [expenseIdentifier(1)],
      [expenseIdentifier(1), expenseIdentifier(1)],
      ["invalid", expenseIdentifier(2)],
    ])
      expect(
        await f.feature.preparePayment.execute(access, operation, id, current, itemIds, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_expense_payment_batch" } });
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each([
    { ...destination, bankCode: "lower" },
    { ...destination, accountNumber: "00123x" },
    { ...destination, accountName: "Name\nInjected" },
  ])("does not review an invalid bank destination $bankCode/$accountNumber", (invalid) => {
    const first = selection()[0];
    assert(first);
    expect(
      reviewExpensePaymentPreparation(
        access,
        [{ ...first, destination: invalid }],
        "Batch",
        "Reviewed",
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_expense_payment_destination" } });
  });
  it.each([
    "expense_claim_payment_reserved",
    "expense_payment_requires_approved_claims",
    "independent_expense_payment_required",
    "mfa_required",
  ])("preserves the safe %s failure", async (code) => {
    const f = fixture();
    f.request.mockRejectedValue(new HttpResponseError(409, { code, detail: "PRIVATE BANK DATA" }));
    const result = await f.feature.preparePayment.execute(
      access,
      operation,
      id,
      review(),
      [expenseIdentifier(1), expenseIdentifier(2)],
      signal(),
    );
    expect(result).toEqual({ ok: false, failure: { code, fields: {}, parameters: {} } });
    expect(expensePaymentPreparationWasRejected({ code, fields: {}, parameters: {} })).toBe(true);
  });
  it.each([
    { id, version: 1 },
    { id: expenseIdentifier(88), version: 0 },
  ])("does not trust an unrelated receipt $id/$version", async (receipt) => {
    const f = fixture();
    f.request.mockResolvedValue(receipt);
    expect(
      await f.feature.preparePayment.execute(
        access,
        operation,
        id,
        review(),
        [expenseIdentifier(1), expenseIdentifier(2)],
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("propagates cancellation before acquisition and after a late committed receipt", async () => {
    const f = fixture(),
      aborted = new AbortController();
    aborted.abort();
    expect(() => f.feature.loadPayables.execute(access, search, aborted.signal)).toThrow();
    expect(f.request).not.toHaveBeenCalled();
    const pending = new AbortController();
    f.request.mockImplementation(async () => {
      pending.abort();
      return { id, version: 0 };
    });
    await expect(
      f.feature.preparePayment.execute(
        access,
        operation,
        id,
        review(),
        [expenseIdentifier(1), expenseIdentifier(2)],
        pending.signal,
      ),
    ).rejects.toThrow();
  });
});
