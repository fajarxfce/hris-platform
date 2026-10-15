import { describe, expect, it, vi } from "vitest";
import { amendmentId } from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import type { HttpClient } from "../../../../core/data/http/http-client";
import { HttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { HttpPayrollAmendmentDataSource } from "../datasources/http-payroll-amendment-data-source";
import { RemotePayrollAmendmentRepository } from "./remote-payroll-amendment-repository";

const company = calculationCompany as CompanyId,
  id = amendmentId(),
  operation = amendmentId(9) as OperationId,
  signal = new AbortController().signal;
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return {
    http,
    repo: new RemotePayrollAmendmentRepository(new HttpPayrollAmendmentDataSource(http)),
  };
}
describe("retained amendment command transport", () => {
  for (const method of ["calculate", "cancel"] as const) {
    it(`${method} sends only the observed command and maps its exact receipt`, async () => {
      const f = fixture({ id: id.toUpperCase(), version: 5 }),
        input = { expectedVersion: 4, reason: "Reviewed", unexpectedPrivateField: "private" };
      const result = await f.repo[method](company, id, operation, input, signal);
      expect(result).toEqual({ ok: true, value: { id, version: 5 } });
      expect(f.http.request).toHaveBeenCalledExactlyOnceWith(
        {
          path: `/api/v1/companies/${company}/payroll/amendments/${id}/${method}`,
          method: "POST",
          operationId: operation,
          body: { expectedVersion: 4, reason: "Reviewed" },
        },
        signal,
      );
      if (result.ok) expect(Object.isFrozen(result.value)).toBe(true);
    });
    it.each([
      { id: amendmentId(2), version: 5 },
      { id, version: 6 },
      { id, version: 4 },
      { id, version: 1000 },
      { id, version: 5.1 },
    ])(`${method} rejects a substituted receipt: %j`, async (raw) => {
      expect(
        await fixture(raw).repo[method](
          company,
          id,
          operation,
          { expectedVersion: 4, reason: "Reviewed" },
          signal,
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    });
    it(`${method} preserves bounded failures and pending cancellation without retry`, async () => {
      const f = fixture({ id, version: 1 });
      f.http.request.mockRejectedValueOnce(
        new HttpResponseError(409, {
          code: "payroll_amendment_not_editable",
          detail: "PRIVATE diagnostic",
          fields: {},
          parameters: {},
        }),
      );
      const result = await f.repo[method](
        company,
        id,
        operation,
        { expectedVersion: 0, reason: "Reviewed" },
        signal,
      );
      expect(result).toMatchObject({
        ok: false,
        failure: { code: "payroll_amendment_not_editable" },
      });
      expect(JSON.stringify(result)).not.toContain("PRIVATE");
      const owner = new AbortController();
      f.http.request.mockImplementationOnce(async () => {
        owner.abort();
        return { id, version: 1 };
      });
      await expect(
        f.repo[method](
          company,
          id,
          operation,
          { expectedVersion: 0, reason: "Reviewed" },
          owner.signal,
        ),
      ).rejects.toMatchObject({ name: "AbortError" });
      expect(f.http.request).toHaveBeenCalledTimes(2);
    });
  }
});
