import { describe, expect, it, vi } from "vitest";
import {
  calculationCompany,
  calculationDetails,
  calculationId,
  calculationRun,
} from "../../../../../tests/fixtures/payroll-calculations";
import { comparisonBaseline } from "../../../../../tests/fixtures/payroll-comparison";
import type { HttpRequest } from "../../../../core/data/http/http-client";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { createPayrollFeature } from "../../di/payroll-feature";

const access = { companyId: calculationCompany as CompanyId, permissions: ["payroll.read"] };
const files = { downloadBinary: vi.fn(), downloadText: vi.fn() };
const signal = () => new AbortController().signal;
describe("payroll comparison reads", () => {
  it("loads only one result to identify the run and searches the earlier month without comparing automatically", async () => {
    const request = vi.fn(async (input: HttpRequest) =>
      input.path.includes("/runs/")
        ? calculationDetails(calculationRun(), 0, 1)
        : { items: [], nextCursor: null },
    );
    const result = await createPayrollFeature({ request }, files).loadComparisonContext.execute(
      access,
      calculationId,
      null,
      null,
      signal(),
    );
    expect(result).toMatchObject({ ok: true, value: { month: "2026-08", periods: { items: [] } } });
    expect(request).toHaveBeenCalledTimes(2);
    expect(request.mock.calls[0]?.[0].path).toContain("limit=1");
    expect(request.mock.calls[1]?.[0].path).toContain("from=2026-08&until=2026-08");
  });
  it("rejects absent grants and malformed selections before I/O", async () => {
    const request = vi.fn(),
      feature = createPayrollFeature({ request }, files),
      denied = { ...access, permissions: [] };
    expect(
      await feature.loadComparisonContext.execute(denied, calculationId, null, null, signal()),
    ).toMatchObject({ ok: false });
    expect(
      await feature.loadComparisonContext.execute(access, calculationId, "invalid", null, signal()),
    ).toMatchObject({ ok: false });
    expect(
      await feature.loadComparison.execute(
        denied,
        calculationId,
        comparisonBaseline,
        null,
        false,
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(
      await feature.loadComparison.execute(
        access,
        calculationId,
        calculationId,
        null,
        false,
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(
      await feature.loadComparison.execute(
        access,
        calculationId,
        comparisonBaseline,
        "bad",
        false,
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
  });
  it("does not discover periods for incomplete runs or a month that is not earlier", async () => {
    const request = vi.fn().mockResolvedValue(calculationDetails(calculationRun()));
    const load = createPayrollFeature({ request }, files).loadComparisonContext;
    expect(await load.execute(access, calculationId, "2026-09", null, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_payroll_comparison" },
    });
    expect(request).toHaveBeenCalledTimes(1);
    request.mockResolvedValue(calculationDetails(calculationRun({ succeeded: 0, failed: 1 })));
    expect(await load.execute(access, calculationId, null, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "payroll_comparison_unavailable" },
    });
    expect(request).toHaveBeenCalledTimes(2);
  });
  it("cancels between the run and the period search", async () => {
    const pending = new AbortController();
    const request = vi.fn(async () => {
      pending.abort();
      return calculationDetails(calculationRun());
    });
    await expect(
      createPayrollFeature({ request }, files).loadComparisonContext.execute(
        access,
        calculationId,
        null,
        null,
        pending.signal,
      ),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(request).toHaveBeenCalledTimes(1);
  });
});
