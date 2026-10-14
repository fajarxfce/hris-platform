import { assert, describe, expect, it, vi } from "vitest";
import { payrollPayable, payrollPayablePage } from "../../../../tests/fixtures/payroll-payables";
import { payrollIdentifier } from "../../../../tests/fixtures/payroll-payment-identifiers";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toPayrollPayablePage } from "../data/mappers/payroll-payable-mapper";
import type { PayrollPaymentDestination } from "../domain/entities/payroll-payment-destination";
import {
  addPayrollPaymentSelection,
  payrollPaymentPreparationWasRejected,
  reviewPayrollPaymentPreparation,
} from "../domain/policies/payroll-payment-preparation-policy";
import { createPayrollFeature } from "./payroll-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId: company, permissions: ["payroll.pay"] },
  operation = payrollIdentifier(999) as OperationId,
  id = payrollIdentifier(998);
const search = { from: "2026-10-01", until: "2026-10-31", after: null };
const destination: PayrollPaymentDestination = {
  bankCode: "014",
  accountNumber: "001234567890",
  accountName: "Payable Employee 1",
};
const signal = () => new AbortController().signal;
function selection() {
  return toPayrollPayablePage(payrollPayablePage(), company, search).items.map((payable) => ({
    payable,
    destination,
  }));
}
function review() {
  const result = reviewPayrollPaymentPreparation(
    access,
    selection(),
    "  September payroll  ",
    "  Bank details verified  ",
  );
  assert(result.ok);
  return result.value;
}
function fixture() {
  const request = vi.fn();
  return {
    request,
    feature: createPayrollFeature({ request }, { downloadBinary: vi.fn(), downloadText: vi.fn() }),
  };
}
describe("payroll preparation boundaries", () => {
  it("loads a bounded payable page with retained payroll dates and immutable domain rows", async () => {
    const f = fixture(),
      raw = payrollPayablePage(20);
    const first = raw.items[0];
    assert(first);
    f.request.mockResolvedValue(raw);
    const result = await f.feature.loadPayables.execute(access, search, signal());
    assert(result.ok);
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${company}/payroll/payments/payables?from=2026-10-01&until=2026-10-31&limit=20`,
    });
    expect(result.value.items[0]).toMatchObject({
      companyId: company,
      taxMonth: "2026-09",
      plannedPaymentDate: "2026-10-13",
    });
    expect(result.value.nextCursor).toBe(raw.items.at(-1)?.assessmentId);
    expect(Object.isFrozen(result.value.items)).toBe(true);
    expect(Object.isFrozen(result.value.items[0])).toBe(true);
  });
  it("rejects unavailable scope, unbounded dates and invalid cursors before I/O", async () => {
    const f = fixture();
    expect(
      await f.feature.loadPayables.execute(
        { ...access, permissions: ["payroll.manage"] },
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
  it.each(["duplicate", "order", "amount", "cursor", "limit", "month", "date", "zero"])(
    "rejects an incoherent payable %s response",
    async (problem) => {
      const f = fixture(),
        raw = payrollPayablePage(),
        first = raw.items[0],
        second = raw.items[1];
      assert(first && second);
      if (problem === "duplicate") second.assessmentId = first.assessmentId;
      if (problem === "order") raw.items.reverse();
      if (problem === "amount") first.amount = "0.00";
      if (problem === "cursor") raw.nextCursor = first.assessmentId;
      if (problem === "limit") raw.items = payrollPayablePage(21).items;
      if (problem === "month") first.taxMonth = "2026-13";
      if (problem === "date") first.plannedPaymentDate = "2026-02-30";
      if (problem === "zero") first.amount = "0.00";

      f.request.mockResolvedValue(raw);
      expect(await f.feature.loadPayables.execute(access, search, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    },
  );
  it("rejects repeated cursors and preserves an finalized assessment's microsecond order", async () => {
    const f = fixture(),
      raw = payrollPayablePage(),
      first = raw.items[0],
      second = raw.items[1];
    assert(first && second);
    first.publishedAt = "2026-10-13T05:00:00.000002Z";
    second.publishedAt = "2026-10-13T05:00:00.000001Z";
    f.request.mockResolvedValue(raw);
    expect((await f.feature.loadPayables.execute(access, search, signal())).ok).toBe(true);
    expect(
      await f.feature.loadPayables.execute(
        access,
        { ...search, after: first.assessmentId },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("checks scope, finance permission, duplicate identity and the 100-assessment selection bound", () => {
    const selected = selection(),
      first = selected[0];
    assert(first);
    expect(addPayrollPaymentSelection({ ...access, permissions: [] }, [], first.payable).ok).toBe(
      false,
    );
    expect(
      addPayrollPaymentSelection(
        { ...access, companyId: payrollIdentifier(77) as CompanyId },
        [],
        first.payable,
      ).ok,
    ).toBe(false);
    expect(
      addPayrollPaymentSelection(access, selected, {
        ...first.payable,
        assessmentId: first.payable.assessmentId.toUpperCase(),
      }),
    ).toMatchObject({ ok: false, failure: { code: "payroll_payment_duplicate_assessment" } });
    const hundred = Array.from({ length: 100 }, (_, index) => ({
      payable: { ...payrollPayable(index), companyId: company },
      destination,
    }));
    expect(
      addPayrollPaymentSelection(access, hundred, { ...payrollPayable(101), companyId: company }),
    ).toMatchObject({ ok: false, failure: { code: "payroll_payment_selection_limit" } });
  });
  it("freezes the human review and sends only original IDs and bank instructions", async () => {
    const f = fixture(),
      current = review(),
      itemIds = [payrollIdentifier(996), payrollIdentifier(997)];
    f.request.mockResolvedValue({ id, version: 0 });
    expect(Object.isFrozen(current.items[0]?.destination)).toBe(true);
    expect(
      await f.feature.preparePayment.execute(access, operation, id, current, itemIds, signal()),
    ).toEqual({ ok: true, value: { id, version: 0 } });
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${company}/payroll/payments/${id}`,
      method: "PUT",
      operationId: operation,
      body: {
        title: "September payroll",
        reason: "Bank details verified",
        items: current.items.map((item, index) => ({
          id: itemIds[index],
          assessmentId: item.payable.assessmentId,
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
        { ...current, companyId: payrollIdentifier(77) as CompanyId },
        [payrollIdentifier(1), payrollIdentifier(2)],
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const itemIds of [
      [],
      [payrollIdentifier(1)],
      [payrollIdentifier(1), payrollIdentifier(1)],
      ["invalid", payrollIdentifier(2)],
    ])
      expect(
        await f.feature.preparePayment.execute(access, operation, id, current, itemIds, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_payroll_payment_batch" } });
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
      reviewPayrollPaymentPreparation(
        access,
        [{ ...first, destination: invalid }],
        "Batch",
        "Reviewed",
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_payroll_payment_destination" } });
  });
  it.each([
    "payroll_assessment_payment_reserved",
    "payroll_assessment_not_payable",
    "access_denied",
    "mfa_required",
  ])("preserves the safe %s failure", async (code) => {
    const f = fixture();
    f.request.mockRejectedValue(new HttpResponseError(409, { code, detail: "PRIVATE BANK DATA" }));
    const result = await f.feature.preparePayment.execute(
      access,
      operation,
      id,
      review(),
      [payrollIdentifier(1), payrollIdentifier(2)],
      signal(),
    );
    expect(result).toEqual({ ok: false, failure: { code, fields: {}, parameters: {} } });
    expect(payrollPaymentPreparationWasRejected({ code, fields: {}, parameters: {} })).toBe(true);
  });
  it.each([
    { id, version: 1 },
    { id: payrollIdentifier(88), version: 0 },
  ])("does not trust an unrelated receipt $id/$version", async (receipt) => {
    const f = fixture();
    f.request.mockResolvedValue(receipt);
    expect(
      await f.feature.preparePayment.execute(
        access,
        operation,
        id,
        review(),
        [payrollIdentifier(1), payrollIdentifier(2)],
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
        [payrollIdentifier(1), payrollIdentifier(2)],
        pending.signal,
      ),
    ).rejects.toThrow();
  });
});
