import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { EmploymentRevisionDetailsDto } from "../data/models/employment-revision-details-dto";
import type { EmployeeId } from "../domain/entities/employee";
import type { EmploymentCancellation } from "../domain/entities/employment-cancellation";
import { createPeopleFeature } from "./people-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const employeeId = "40000000-abcd-4000-8000-000000000001" as EmployeeId;
const operation = "60000000-0000-4000-8000-000000000001" as OperationId;
const access = { companyId, permissions: ["people.read", "people.manage"] };
const signal = () => new AbortController().signal;
const input: EmploymentCancellation = {
  employeeId,
  expectedVersion: 9,
  revision: 2,
  reason: " Cancel scheduled change ",
};
const details = (): EmploymentRevisionDetailsDto => ({
  employeeId,
  version: 9,
  companyDate: "2026-10-01",
  canCancel: true,
  revision: {
    revision: 2,
    reason: "Scheduled change",
    recordedAt: "2026-09-01T00:00:00.000123Z",
    cancellation: null,
    terms: {
      effectiveFrom: "2027-01-01",
      startDate: "2026-01-01",
      endDate: null,
      status: "ACTIVE",
      contract: "PERMANENT",
    },
  },
});

describe("employment cancellation contracts", () => {
  it("keeps direct reads and cancellation permissions independent and rejects invalid revision paths before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createPeopleFeature({ request });
    for (const permissions of [[], ["people.self.read"], ["people.team.read"], ["people.manage"]]) {
      expect(
        await feature.loadEmploymentRevision.execute(
          { companyId, permissions },
          employeeId,
          "2",
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(
        await feature.cancelEmploymentRevision.execute(
          { companyId, permissions },
          operation,
          input,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    }
    expect(
      await feature.cancelEmploymentRevision.execute(
        { companyId, permissions: ["people.read"] },
        operation,
        input,
        signal(),
      ),
    ).toMatchObject({ ok: false });
    for (const revision of ["-1", "01", "1e2", "9007199254740992", ""])
      expect(
        await feature.loadEmploymentRevision.execute(access, employeeId, revision, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_employment_revision" } });
    expect(
      await feature.loadEmploymentRevision.execute(access, "../other", "2", signal()),
    ).toMatchObject({ ok: false, failure: { code: "employee_not_found" } });
    expect(request).not.toHaveBeenCalled();
    request.mockResolvedValueOnce({ ...details(), canCancel: false });
    expect(
      await feature.loadEmploymentRevision.execute(
        { companyId, permissions: ["people.read"] },
        employeeId.toUpperCase(),
        "2",
        signal(),
      ),
    ).toMatchObject({ ok: true, value: { canCancel: false } });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/employees/${employeeId}/revisions/2`,
    );
  });

  it("maps exact revision evidence and the observed aggregate version into an immutable private projection", async () => {
    const dto = details();
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({
      ...dto,
      employeeId: employeeId.toUpperCase(),
      revision: { ...dto.revision, actorId: "PRIVATE" },
      profile: "PRIVATE",
    });
    const result = await createPeopleFeature({ request }).loadEmploymentRevision.execute(
      access,
      employeeId,
      "2",
      signal(),
    );
    expect(result).toMatchObject({
      ok: true,
      value: {
        employeeId,
        version: 9,
        companyDate: "2026-10-01",
        revision: { revision: 2 },
        canCancel: true,
      },
    });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    if (!result.ok) throw new Error("Revision fixture failed");
    for (const value of [result.value, result.value.revision, result.value.revision.terms])
      expect(Object.isFrozen(value)).toBe(true);
  });

  it("rejects another employee, inconsistent revision versions and impossible cancellation availability", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createPeopleFeature({ request });
    const dto = details();
    for (const response of [
      { ...dto, employeeId: companyId },
      { ...dto, version: 1 },
      { ...dto, companyDate: "0000-01-01" },
      { ...dto, companyDate: "2027-01-01" },
      { ...dto, revision: { ...dto.revision, revision: 3 } },
      {
        ...dto,
        revision: {
          ...dto.revision,
          cancellation: { reason: "Already cancelled", recordedAt: "2026-09-02T00:00:00Z" },
        },
      },
    ]) {
      request.mockResolvedValueOnce(response);
      expect(
        await feature.loadEmploymentRevision.execute(access, employeeId, "2", signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    request.mockResolvedValueOnce({ ...dto, revision: { ...dto.revision, revision: 0 } });
    expect(
      await feature.loadEmploymentRevision.execute(access, employeeId, "0", signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("validates cancellation input and transmits only the reason and observed version with a stable operation", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createPeopleFeature({ request });
    for (const values of [
      { revision: 0 },
      { revision: 10 },
      { expectedVersion: 0 },
      { expectedVersion: Number.MAX_SAFE_INTEGER },
      { reason: " " },
      { reason: "r".repeat(1001) },
    ])
      expect(
        await feature.cancelEmploymentRevision.execute(
          access,
          operation,
          { ...input, ...values },
          signal(),
        ),
      ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
    request.mockResolvedValueOnce({ id: employeeId.toUpperCase(), version: 10 });
    expect(
      await feature.cancelEmploymentRevision.execute(access, operation, input, signal()),
    ).toEqual({ ok: true, value: { id: employeeId, version: 10 } });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/employees/${employeeId}/revisions/2/cancel`,
      method: "POST",
      operationId: operation,
      body: { expectedVersion: 9, reason: "Cancel scheduled change" },
    });
    for (const receipt of [
      { id: companyId, version: 10 },
      { id: employeeId, version: 3 },
      { id: employeeId, version: 9 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(
        await feature.cancelEmploymentRevision.execute(access, operation, input, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });

  it("retains safe failure codes, propagates cancellation and makes no automatic retry", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValueOnce(
      new HttpResponseError(409, {
        code: "effective_revision_cannot_be_cancelled",
        detail: "PRIVATE",
      }),
    );
    const feature = createPeopleFeature({ request });
    expect(
      await feature.cancelEmploymentRevision.execute(access, operation, input, signal()),
    ).toEqual({
      ok: false,
      failure: { code: "effective_revision_cannot_be_cancelled", fields: {}, parameters: {} },
    });
    let resolve!: (value: unknown) => void;
    request.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const pending = new AbortController();
    const result = feature.cancelEmploymentRevision.execute(
      access,
      operation,
      input,
      pending.signal,
    );
    pending.abort();
    resolve({ id: employeeId, version: 10 });
    await expect(result).rejects.toMatchObject({ name: "AbortError" });
    expect(() =>
      feature.loadEmploymentRevision.execute(access, employeeId, "2", pending.signal),
    ).toThrow();
    expect(request).toHaveBeenCalledTimes(2);
  });
});
