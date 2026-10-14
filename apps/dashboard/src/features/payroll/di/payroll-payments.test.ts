import { assert, describe, expect, it, vi } from "vitest";
import { payrollIdentifier } from "../../../../tests/fixtures/payroll-payment-identifiers";
import {
  payrollPaymentCsv,
  payrollPaymentList,
  payrollPaymentRecord,
  payrollPaymentReviewPage,
} from "../../../../tests/fixtures/payroll-payments";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { failed, success } from "../../../core/domain/result";
import { toPayrollPaymentReview } from "../data/mappers/payroll-payment-review-mapper";
import type { PayrollPaymentReviewDto } from "../data/models/payroll-payment-dto";
import type { PayrollPaymentBatchId } from "../domain/entities/payroll-payment-batch";
import { createPayrollFeature } from "./payroll-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId: company, permissions: ["payroll.pay"] };
const search = { from: "2026-10-01", until: "2026-10-31", status: null, after: null };
const signal = () => new AbortController().signal;
const review = (raw = payrollPaymentRecord()) =>
  toPayrollPaymentReview(
    payrollPaymentReviewPage(raw),
    company,
    raw.batch.id as PayrollPaymentBatchId,
    null,
  );
function fixture() {
  const request = vi.fn(),
    downloadText = vi.fn().mockResolvedValue(success(undefined));
  return {
    request,
    downloadText,
    feature: createPayrollFeature({ request }, { downloadText, downloadBinary: vi.fn() }),
  };
}
describe("payroll payment boundaries", () => {
  it.each(
    [[], ["payroll.read"], ["payroll.calculate"], ["payroll.finalize"], ["expenses.pay"]].map(
      (permissions) => ({ permissions }),
    ),
  )(
    "requires specific finance authority before acquisition ($permissions)",
    async ({ permissions }) => {
      const f = fixture(),
        actor = { ...access, permissions },
        current = review();
      expect(await f.feature.loadPayments.execute(actor, search, signal())).toMatchObject({
        ok: false,
        failure: { code: "access_denied" },
      });
      expect(
        await f.feature.loadPayment.execute(actor, current.batch.id, null, signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(await f.feature.downloadPayment.execute(actor, current, signal())).toMatchObject({
        ok: false,
        failure: { code: "access_denied" },
      });
      expect(f.request).not.toHaveBeenCalled();
      expect(f.downloadText).not.toHaveBeenCalled();
    },
  );
  it("validates dates, cursors and identities before acquiring bank data", async () => {
    const f = fixture();
    for (const input of [
      { ...search, from: "2026-02-30" },
      { ...search, until: "2028-01-01" },
      { ...search, from: "2026-11-01" },
      { ...search, status: "PENDING" },
      { ...search, after: "../other" },
    ])
      expect(await f.feature.loadPayments.execute(access, input, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_payroll_payment_page" },
      });
    expect(await f.feature.loadPayment.execute(access, "../private", null, signal())).toMatchObject(
      { ok: false, failure: { code: "payroll_payment_batch_not_found" } },
    );
    expect(
      await f.feature.loadPayment.execute(access, payrollIdentifier(80001), "1e3", signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    expect(f.request).not.toHaveBeenCalled();
  });
  it("bounds and scopes the directory and coherent review calls", async () => {
    const f = fixture(),
      raw = payrollPaymentRecord({ count: 25, settled: 23 });
    f.request
      .mockResolvedValueOnce(payrollPaymentList([raw]))
      .mockResolvedValueOnce(payrollPaymentReviewPage(raw, 19));
    const page = await f.feature.loadPayments.execute(
      access,
      { ...search, status: "RELEASED" },
      signal(),
    );
    assert(page.ok);
    expect(page.value.items[0]?.companyId).toBe(company);
    const result = await f.feature.loadPayment.execute(
      access,
      raw.batch.id.toUpperCase(),
      "19",
      signal(),
    );
    assert(result.ok);
    expect(result.value.history.items.map((entry) => entry.version)).toEqual([20, 21, 22, 23, 24]);
    expect(result.value.batch.items).toHaveLength(25);
    expect(result.value.results).toHaveLength(23);
    expect(f.request.mock.calls[0]?.[0].path).toBe(
      `/api/v1/companies/${company}/payroll/payments?from=2026-10-01&until=2026-10-31&limit=20&status=RELEASED`,
    );
    expect(f.request.mock.calls[1]?.[0].path).toBe(
      `/api/v1/companies/${company}/payroll/payments/${raw.batch.id}/review?historyLimit=20&historyAfter=19`,
    );
    expect(Object.isFrozen(result.value.batch.items[0]?.destination)).toBe(true);
    expect(Object.isFrozen(result.value.results[0]?.results)).toBe(true);
  });
  it("keeps exact totals larger than an assessment without widening individual payment amounts", async () => {
    const f = fixture(),
      raw = payrollPaymentRecord({ count: 100 });
    for (const item of raw.batch.items) item.amount = "999999999999.99";
    raw.batch.totalAmount = "99999999999999.00";
    f.request.mockResolvedValue(raw);
    const result = await f.feature.loadPayment.execute(access, raw.batch.id, null, signal());
    assert(result.ok);
    expect(result.value.batch.totalAmount).toBe("99999999999999.00");
    raw.batch.totalAmount = "99999999999999.01";
    expect(await f.feature.loadPayment.execute(access, raw.batch.id, null, signal())).toMatchObject(
      { ok: false, failure: { code: "invalid_response" } },
    );
  });
  it.each<[string, (raw: PayrollPaymentReviewDto) => void]>([
    [
      "different batch",
      (raw) => {
        raw.batch.id = payrollIdentifier(88888);
      },
    ],
    [
      "wrong amount",
      (raw) => {
        raw.batch.totalAmount = "1.00";
      },
    ],
    [
      "wrong item count",
      (raw) => {
        raw.batch.itemCount = 100;
      },
    ],
    [
      "duplicate payment",
      (raw) => {
        const item = raw.batch.items[0];
        assert(item);
        raw.batch.items[1] = { ...item };
      },
    ],
    [
      "unknown destination",
      (raw) => {
        const item = raw.batch.items[0];
        assert(item);
        item.destination.accountNumber = "bad";
      },
    ],
    [
      "oversized item amount",
      (raw) => {
        const item = raw.batch.items[0];
        assert(item);
        item.amount = "1000000000000.00";
      },
    ],
    [
      "missing release",
      (raw) => {
        raw.batch.releasedAt = null;
      },
    ],
    [
      "missing bank result",
      (raw) => {
        raw.results = [];
      },
    ],
    [
      "duplicate result",
      (raw) => {
        const group = raw.results[0];
        assert(group);
        raw.results.push(group);
      },
    ],
    [
      "wrong bank reference",
      (raw) => {
        const result = raw.results[0]?.results[0];
        assert(result);
        result.transactionReference = "OTHER";
      },
    ],
    [
      "incorrect outcome",
      (raw) => {
        const result = raw.results[0]?.results[0];
        assert(result);
        result.status = "FAILED";
      },
    ],
    [
      "outcome one microsecond before release",
      (raw) => {
        const result = raw.results[0]?.results[0],
          item = raw.batch.items[0];
        assert(result && item);
        raw.batch.releasedAt = "2026-10-13T06:00:00.000002Z";
        result.occurredAt = "2026-10-13T06:00:00.000001Z";
        item.occurredAt = result.occurredAt;
      },
    ],
    [
      "outcome one microsecond after its recorded evidence",
      (raw) => {
        const group = raw.results[0],
          result = group?.results[0],
          item = raw.batch.items[0];
        assert(group && result && item);
        group.recordedAt = "2026-10-13T06:01:00.000001Z";
        result.occurredAt = "2026-10-13T06:01:00.000002Z";
        item.occurredAt = result.occurredAt;
      },
    ],
    [
      "future result version",
      (raw) => {
        const group = raw.results[0];
        assert(group);
        group.batchVersion = 101;
      },
    ],
    [
      "missing activity",
      (raw) => {
        raw.history.items.splice(1, 1);
      },
    ],
    [
      "false continuation",
      (raw) => {
        raw.history.nextCursor = "1";
      },
    ],
    [
      "export after partial settlement",
      (raw) => {
        raw.availableActions.push("EXPORT");
      },
    ],
    [
      "release after settlement",
      (raw) => {
        raw.availableActions.push("RELEASE");
      },
    ],
  ])("rejects %s before exposing instructions", async (_, mutate) => {
    const f = fixture(),
      raw = payrollPaymentRecord({ settled: 1 }),
      id = raw.batch.id;
    mutate(raw);
    f.request.mockResolvedValue(raw);
    expect(await f.feature.loadPayment.execute(access, id, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it.each(["PREPARED", "CANCELLED"] as const)(
    "maps a %s batch without bank outcomes",
    async (status) => {
      const f = fixture(),
        raw = payrollPaymentRecord({ status });
      f.request.mockResolvedValue(raw);
      const result = await f.feature.loadPayment.execute(access, raw.batch.id, null, signal());
      assert(result.ok);
      expect(result.value.batch.status).toBe(status);
      expect(result.value.results).toEqual([]);
    },
  );
  it("retains outcomes equal to their release and recorded instants at microsecond precision", async () => {
    const f = fixture(),
      raw = payrollPaymentRecord({ settled: 1 }),
      group = raw.results[0],
      result = group?.results[0],
      item = raw.batch.items[0],
      at = "2026-10-13T06:01:00.123456Z";
    assert(group && result && item);
    raw.batch.releasedAt = at;
    result.occurredAt = at;
    group.recordedAt = at;
    item.occurredAt = at;
    for (const action of raw.history.items.slice(1)) action.recordedAt = at;
    f.request.mockResolvedValue(raw);
    const loaded = await f.feature.loadPayment.execute(access, raw.batch.id, null, signal());
    assert(loaded.ok);
    expect(loaded.value.results[0]?.results[0]?.occurredAt).toBe(at);
  });
  it("preserves both successful and failed outcomes in a closed batch", async () => {
    const f = fixture(),
      raw = payrollPaymentRecord({ settled: 3 });
    f.request.mockResolvedValue(raw);
    const result = await f.feature.loadPayment.execute(access, raw.batch.id, null, signal());
    assert(result.ok);
    expect(result.value.batch.status).toBe("CLOSED");
    expect(result.value.availableActions).toEqual([]);
    expect(result.value.results[1]?.results[0]?.transactionReference).toBeNull();
  });
  it("enforces deterministic microsecond ordering and cursor progress", async () => {
    const f = fixture(),
      first = payrollPaymentRecord({ index: 1 }),
      second = payrollPaymentRecord({ index: 2 }),
      records = [first, second];
    first.batch.createdAt = "2026-10-13T05:00:00.000002Z";
    second.batch.createdAt = "2026-10-13T05:00:00.000001Z";
    f.request.mockResolvedValue(payrollPaymentList(records));
    assert((await f.feature.loadPayments.execute(access, search, signal())).ok);
    f.request.mockResolvedValue(payrollPaymentList([...records].reverse()));
    expect(await f.feature.loadPayments.execute(access, search, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
    const page = payrollPaymentList(records);
    page.nextCursor = second.batch.id;
    f.request.mockResolvedValue(page);
    expect(await f.feature.loadPayments.execute(access, search, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("bounds CSV acquisition, preserves leading zeros and waits for the file repository result", async () => {
    const f = fixture(),
      current = review();
    f.request.mockResolvedValue(payrollPaymentCsv);
    expect(await f.feature.downloadPayment.execute(access, current, signal())).toEqual(
      success(undefined),
    );
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${company}/payroll/payments/${current.batch.id}/export`,
      response: { type: "text", mediaType: "text/csv", maximumBytes: 262144 },
    });
    expect(f.downloadText.mock.calls[0]?.[0]).toEqual({
      name: `payroll-payments-${current.batch.id}.csv`,
      mediaType: "text/csv",
      text: payrollPaymentCsv,
    });
    f.downloadText.mockResolvedValue(failed("file_download_failed"));
    expect(await f.feature.downloadPayment.execute(access, current, signal())).toMatchObject({
      ok: false,
      failure: { code: "file_download_failed" },
    });
  });
  it("does not export foreign or ineligible instructions", async () => {
    const f = fixture();
    expect(
      await f.feature.downloadPayment.execute(
        { ...access, companyId: payrollIdentifier(2) as CompanyId },
        review(),
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await f.feature.downloadPayment.execute(
        access,
        review(payrollPaymentRecord({ settled: 1 })),
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "payroll_payment_instructions_unavailable" } });
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each([
    "access_denied",
    "recent_authentication_required",
    "payroll_payment_instructions_unavailable",
  ])("preserves a server %s without exporting", async (code) => {
    const f = fixture();
    f.request.mockRejectedValue(new HttpResponseError(403, { code, detail: "PRIVATE" }));
    expect(await f.feature.downloadPayment.execute(access, review(), signal())).toMatchObject({
      ok: false,
      failure: { code },
    });
    expect(f.downloadText).not.toHaveBeenCalled();
  });
  it("propagates cancellation before acquisition and after a late worksheet", async () => {
    const f = fixture(),
      abort = new AbortController();
    abort.abort();
    expect(() =>
      f.feature.loadPayment.execute(access, payrollIdentifier(80001), null, abort.signal),
    ).toThrow();
    expect(f.request).not.toHaveBeenCalled();
  });
  it("does not save a worksheet after its owner cancels", async () => {
    const f = fixture(),
      abort = new AbortController();
    let finish!: (text: string) => void;
    f.request.mockReturnValue(
      new Promise<string>((resolve) => {
        finish = resolve;
      }),
    );
    const pending = f.feature.downloadPayment.execute(access, review(), abort.signal);
    abort.abort();
    finish(payrollPaymentCsv);
    await expect(pending).rejects.toThrow();
    expect(f.downloadText).not.toHaveBeenCalled();
  });
});
