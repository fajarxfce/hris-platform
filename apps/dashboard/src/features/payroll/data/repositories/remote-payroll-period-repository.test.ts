import { assert, describe, expect, it, vi } from "vitest";
import {
  periodCompany,
  periodCreation,
  periodDetails,
  periodEmployee,
  periodId,
  periodRecord,
} from "../../../../../tests/fixtures/payroll-periods";
import type { HttpClient } from "../../../../core/data/http/http-client";
import { HttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { HttpPayrollPeriodDataSource } from "../datasources/http-payroll-period-data-source";
import type { PayrollPeriodDetailsDto } from "../models/payroll-period-dto";
import { RemotePayrollPeriodRepository } from "./remote-payroll-period-repository";

const company = periodCompany as CompanyId,
  signal = new AbortController().signal,
  operation = periodId as OperationId;
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return {
    http,
    repository: new RemotePayrollPeriodRepository(new HttpPayrollPeriodDataSource(http)),
  };
}
describe("period data boundary", () => {
  it("acquires bounded coherent roster/history and freezes account-owned values", async () => {
    const raw = periodDetails(),
      { repository, http } = fixture(raw),
      result = await repository.details(company, periodId, null, null, signal);
    assert(result.ok);
    expect(result.value.period.companyId).toBe(company);
    expect(Object.isFrozen(result.value.members.items[0])).toBe(true);
    assert(raw.members.items[0]);
    raw.members.items[0].name = "Changed source";
    expect(result.value.members.items[0]?.name).not.toBe("Changed source");
    expect(http.request).toHaveBeenCalledWith(
      { path: `/api/v1/companies/${company}/payroll/periods/${periodId}?limit=50&historyLimit=20` },
      signal,
    );
  });
  it.each<[string, (raw: PayrollPeriodDetailsDto) => void]>([
    [
      "foreign period",
      (raw) => {
        raw.period.id = periodEmployee().id;
      },
    ],
    [
      "missing roster",
      (raw) => {
        raw.members.items.pop();
      },
    ],
    [
      "duplicate employee",
      (raw) => {
        assert(raw.members.items[0]);
        raw.members.items[1] = { ...raw.members.items[0] };
      },
    ],
    [
      "mismatched input status",
      (raw) => {
        assert(raw.members.items[0]);
        raw.members.items[0].inputVersion = 1;
      },
    ],
    [
      "history head status",
      (raw) => {
        assert(raw.history.items[0]);
        raw.history.items[0].status = "CANCELLED";
      },
    ],
    [
      "missing history",
      (raw) => {
        raw.history.items = [];
      },
    ],
    [
      "looped cursor",
      (raw) => {
        assert(raw.members.items[0]);
        raw.members.nextCursor = raw.members.items[0].employeeId;
      },
    ],
    [
      "payment month",
      (raw) => {
        raw.period.plannedPaymentMonth = "2026-09";
      },
    ],
    [
      "unsafe date",
      (raw) => {
        raw.period.plannedPaymentDate = "2026-02-30";
      },
    ],
    [
      "unknown timezone",
      (raw) => {
        raw.period.timezone = "invalid/timezone";
      },
    ],
  ])("rejects %s inside the shared failure boundary", async (_, corrupt) => {
    const raw = periodDetails();
    corrupt(raw);
    expect(
      await fixture(raw).repository.details(company, periodId, null, null, signal),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("rejects skipped immutable revisions", async () => {
    const raw = periodDetails({ version: 2 });
    raw.history.items.splice(1, 1);
    expect(
      await fixture(raw).repository.details(company, periodId, null, null, signal),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("keeps literal employee search bounded and strips private extra fields", async () => {
    const { repository, http } = fixture({
        items: [{ ...periodEmployee(), privateProfile: "hidden" }],
        nextCursor: null,
      }),
      result = await repository.employees(company, "%_", null, signal);
    expect(result).toMatchObject({ ok: true });
    if (result.ok) expect(result.value.items[0]).not.toHaveProperty("privateProfile");
    expect(http.request.mock.calls[0]?.[0].path).toContain("query=%25_&limit=50");
    http.request.mockResolvedValue({
      items: [periodEmployee(), periodEmployee()],
      nextCursor: null,
    });
    expect(await repository.employees(company, "", null, signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("rejects list entries outside the requested month or status and validates receipt identities", async () => {
    const { repository, http } = fixture({ items: [periodRecord()], nextCursor: null });
    expect(
      await repository.list(
        company,
        { from: "2026-10", until: "2026-12", status: null, after: null },
        signal,
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    http.request.mockResolvedValue({ id: periodId, version: 0 });
    expect((await repository.create(company, operation, periodCreation(), signal)).ok).toBe(true);
    expect(http.request.mock.calls.at(-1)?.[0]).toMatchObject({
      method: "POST",
      operationId: operation,
      body: periodCreation(),
    });
    http.request.mockResolvedValue({ id: periodId, version: 2 });
    expect(
      await repository.cancel(company, periodId, operation, 0, "Reviewed draft", signal),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("retains stable failures and propagates cancellation during acquisition", async () => {
    const { repository, http } = fixture(null);
    http.request.mockRejectedValue(
      new HttpResponseError(409, {
        code: "stale_version",
        fields: {},
        parameters: {},
        detail: "private",
      }),
    );
    expect(await repository.cancel(company, periodId, operation, 0, "Review", signal)).toEqual({
      ok: false,
      failure: { code: "stale_version", fields: {}, parameters: {} },
    });
    const owner = new AbortController();
    http.request.mockImplementation(async () => {
      owner.abort();
      return periodDetails();
    });
    await expect(
      repository.details(company, periodId, null, null, owner.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
});
