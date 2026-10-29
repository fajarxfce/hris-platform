import { describe, expect, it, vi } from "vitest";
import {
  calculationCompany,
  calculationId,
} from "../../../../../tests/fixtures/payroll-calculations";
import {
  comparisonBaseline,
  comparisonFixture,
} from "../../../../../tests/fixtures/payroll-comparison";
import { HttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { HttpPayrollComparisonDataSource } from "../datasources/http-payroll-comparison-data-source";
import { RemotePayrollComparisonRepository } from "./remote-payroll-comparison-repository";

const company = calculationCompany as CompanyId,
  signal = () => new AbortController().signal;
const fixture = (value: unknown) =>
  new RemotePayrollComparisonRepository(
    new HttpPayrollComparisonDataSource({ request: async () => value }),
  );
function first(value: ReturnType<typeof comparisonFixture>) {
  const row = value.employees.items[0];
  if (!row) throw new Error("Fixture row required");
  return row;
}
describe("payroll comparison transport", () => {
  it("keeps cent changes above JavaScript's safe integer range and accepts the final run version", async () => {
    const raw = comparisonFixture();
    raw.baseline.employees = raw.current.employees = 5000;
    raw.current.status = "FINALIZED";
    raw.current.version = 25;
    raw.summary = {
      baselineEmployees: 5000,
      currentEmployees: 5000,
      matched: 5000,
      added: 0,
      removed: 0,
      changed: 1,
      unchanged: 4999,
      baseline: {
        taxableGross: "250000000000000.00",
        withheld: "-3000000000000000.00",
        takeHome: "3250000000000000.00",
      },
      current: {
        taxableGross: "250000000000000.00",
        withheld: "-2999999999999999.99",
        takeHome: "3249999999999999.99",
      },
      delta: { taxableGross: "0.00", withheld: "0.01", takeHome: "-0.01" },
    };
    raw.employees = { items: [], nextCursor: null };
    const result = await fixture(raw).compare(
      company,
      calculationId,
      comparisonBaseline,
      "ffffffff-ffff-4fff-bfff-ffffffffffff",
      false,
      signal(),
    );
    expect(result).toEqual({ ok: true, value: raw });
    raw.summary.delta.takeHome = "0.00";
    expect(
      await fixture(raw).compare(
        company,
        calculationId,
        comparisonBaseline,
        "ffffffff-ffff-4fff-bfff-ffffffffffff",
        false,
        signal(),
      ),
    ).toMatchObject({ ok: false });
  });
  it("preserves cent differences, negative tax and distinct added/removed employee identities", async () => {
    const raw = comparisonFixture();
    const result = await fixture(raw).compare(
      company,
      calculationId,
      comparisonBaseline,
      null,
      false,
      signal(),
    );
    expect(result).toEqual({ ok: true, value: raw });
    if (!result.ok) throw new Error("Missing comparison");
    expect(Object.isFrozen(result.value.employees.items[0]?.baseline?.amounts)).toBe(true);
    expect(result.value.employees.items[0]?.delta.takeHome).toBe("-0.01");
  });
  it.each([
    (v: ReturnType<typeof comparisonFixture>) => {
      v.current.id = comparisonBaseline;
    },
    (v: ReturnType<typeof comparisonFixture>) => {
      v.baseline.earningsMonth = "2026-09";
    },
    (v: ReturnType<typeof comparisonFixture>) => {
      v.summary.matched = 3;
    },
    (v: ReturnType<typeof comparisonFixture>) => {
      v.summary.delta.takeHome = "0.00";
    },
    (v: ReturnType<typeof comparisonFixture>) => {
      v.summary.baseline.withheld = "-1800000000000.01";
    },
    (v: ReturnType<typeof comparisonFixture>) => {
      v.employees.items.reverse();
    },
    (v: ReturnType<typeof comparisonFixture>) => {
      v.employees.items.push(first(v));
    },
    (v: ReturnType<typeof comparisonFixture>) => {
      v.employees.nextCursor = v.employees.items[3]?.employmentId ?? null;
    },
    (v: ReturnType<typeof comparisonFixture>) => {
      first(v).change = "UNCHANGED";
    },
    (v: ReturnType<typeof comparisonFixture>) => {
      first(v).baseline = null;
    },
    (v: ReturnType<typeof comparisonFixture>) => {
      first(v).delta.withheld = "0.01";
    },
  ])(
    "rejects incompatible scope, arithmetic, ordering, pagination or partial outcomes",
    async (change) => {
      const raw = comparisonFixture();
      change(raw);
      expect(
        await fixture(raw).compare(
          company,
          calculationId,
          comparisonBaseline,
          null,
          false,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    },
  );
  it("rejects expanded schemas and filters which disagree with their request", async () => {
    for (const raw of [
      { ...comparisonFixture(), accountId: "private" },
      { ...comparisonFixture(), changedOnly: true },
    ])
      expect(
        await fixture(raw).compare(
          company,
          calculationId,
          comparisonBaseline,
          null,
          false,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    expect(
      await fixture(comparisonFixture()).compare(
        company,
        calculationId,
        comparisonBaseline,
        "ffffffff-ffff-4fff-bfff-ffffffffffff",
        false,
        signal(),
      ),
    ).toMatchObject({ ok: false });
  });
  it("retains whole-run totals when only changed employee rows are selected", async () => {
    const raw = comparisonFixture();
    raw.changedOnly = true;
    raw.employees.items = raw.employees.items.filter((row) => row.change !== "UNCHANGED");
    expect(
      await fixture(raw).compare(company, calculationId, comparisonBaseline, null, true, signal()),
    ).toEqual({ ok: true, value: raw });
  });
  it("preserves cancellation and revocation without an automatic retry", async () => {
    const cancelled = new DOMException("Aborted", "AbortError"),
      request = vi
        .fn()
        .mockRejectedValueOnce(cancelled)
        .mockRejectedValueOnce(new HttpResponseError(403, { code: "access_denied" }));
    const repo = new RemotePayrollComparisonRepository(
      new HttpPayrollComparisonDataSource({ request }),
    );
    await expect(
      repo.compare(company, calculationId, comparisonBaseline, null, false, signal()),
    ).rejects.toBe(cancelled);
    expect(
      await repo.compare(company, calculationId, comparisonBaseline, null, false, signal()),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(request).toHaveBeenCalledTimes(2);
  });
});
