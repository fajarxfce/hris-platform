import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { createPeopleFeature } from "./people-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "a0000000-0000-4000-8000-000000000001";
const operation = "30000000-0000-4000-8000-000000000001" as OperationId;
const permissions = [
  "people.import",
  "people.manage",
  "people.profile.read",
  "people.profile.manage",
];
const access = { companyId: company, permissions };
const change = {
  importId: id.toUpperCase(),
  expectedVersion: 7,
  reason: "  Reviewed intake  ",
  allowPartial: true,
};
const signal = () => new AbortController().signal;
const cases = [
  { action: "apply", usecase: "applyEmployeeImport" },
  { action: "resume", usecase: "resumeEmployeeImport" },
  { action: "cancel", usecase: "cancelEmployeeImport" },
] as const;
describe("employee import command boundary", () => {
  for (const scenario of cases) {
    it(`${scenario.action} requires every grant and a valid immutable command before I/O`, async () => {
      const request = vi.fn<HttpClient["request"]>();
      const usecase = createPeopleFeature({ request })[scenario.usecase];
      for (const missing of permissions)
        expect(
          await usecase.execute(
            { ...access, permissions: permissions.filter((p) => p !== missing) },
            operation,
            change,
            signal(),
          ),
        ).toMatchObject({ ok: false, failure: { code: "employee_import_access_required" } });
      for (const invalid of [
        { importId: "../import" },
        { expectedVersion: -1 },
        { expectedVersion: 0.5 },
        { expectedVersion: Number.MAX_SAFE_INTEGER },
        { reason: " " },
        { reason: "x".repeat(1001) },
      ])
        expect(
          await usecase.execute(access, operation, { ...change, ...invalid }, signal()),
        ).toMatchObject({ ok: false, failure: { code: "invalid_employee_import" } });
      expect(await usecase.execute(access, "bad" as OperationId, change, signal())).toMatchObject({
        ok: false,
      });
      expect(request).not.toHaveBeenCalled();
    });
    it(`${scenario.action} sends only its explicit payload and accepts the exact next-version receipt`, async () => {
      const request = vi
        .fn<HttpClient["request"]>()
        .mockResolvedValue({ id: id.toUpperCase(), version: 8 });
      const result = await createPeopleFeature({ request })[scenario.usecase].execute(
        access,
        operation,
        change,
        signal(),
      );
      expect(result).toEqual({ ok: true, value: { id, version: 8 } });
      expect(request.mock.lastCall?.[0]).toEqual({
        path: `/api/v1/companies/${company}/employee-imports/${id}/${scenario.action}`,
        method: "POST",
        operationId: operation,
        body: {
          expectedVersion: 7,
          reason: "Reviewed intake",
          ...(scenario.action === "apply" ? { allowPartial: true } : {}),
        },
      });
      if (result.ok) expect(Object.isFrozen(result.value)).toBe(true);
    });
    it(`${scenario.action} classifies invalid receipts and preserves server failures and cancellation`, async () => {
      const request = vi.fn<HttpClient["request"]>();
      const usecase = createPeopleFeature({ request })[scenario.usecase];
      for (const receipt of [
        { id: operation, version: 8 },
        { id, version: 7 },
        { id, version: 9 },
        { id, version: Number.MAX_SAFE_INTEGER + 1 },
      ]) {
        request.mockResolvedValueOnce(receipt);
        expect(await usecase.execute(access, operation, change, signal())).toMatchObject({
          ok: false,
          failure: { code: "invalid_response" },
        });
      }
      request.mockRejectedValueOnce(
        new HttpResponseError(409, {
          code: "stale_version",
          detail: "PRIVATE IMPORT",
          fields: {},
          parameters: {},
        }),
      );
      const failure = await usecase.execute(access, operation, change, signal());
      expect(failure).toMatchObject({ ok: false, failure: { code: "stale_version" } });
      expect(JSON.stringify(failure)).not.toContain("PRIVATE");
      const aborted = new AbortController();
      aborted.abort();
      expect(() => usecase.execute(access, operation, change, aborted.signal)).toThrow();
      request.mockRejectedValueOnce(new DOMException("cancel", "AbortError"));
      await expect(usecase.execute(access, operation, change, signal())).rejects.toMatchObject({
        name: "AbortError",
      });
    });
  }
});
