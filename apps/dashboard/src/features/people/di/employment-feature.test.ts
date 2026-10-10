import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { EmploymentDetailsDto } from "../data/models/employment-details-dto";
import type { EmployeeId } from "../domain/entities/employee";
import type { EmploymentChange } from "../domain/entities/employment-change";
import { createPeopleFeature } from "./people-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const employeeId = "40000000-abcd-4000-8000-000000000001" as EmployeeId;
const branchId = "30000000-abcd-4000-8000-000000000001";
const managerId = "40000000-abcd-4000-8000-000000000002";
const operation = "60000000-0000-4000-8000-000000000001" as OperationId;
const access = { companyId, permissions: ["people.read", "people.manage"] };
const asOf = "2026-10-01";
const signal = () => new AbortController().signal;
const details = (): EmploymentDetailsDto => ({
  asOf,
  employee: {
    id: employeeId,
    companyId,
    employeeNumber: "EMP-001",
    person: { legalName: "Employee One", email: null },
    terms: {
      effectiveFrom: "2026-01-01",
      startDate: "2026-01-01",
      endDate: null,
      status: "ACTIVE",
      contract: "PERMANENT",
      branchId,
      departmentId: null,
      positionId: null,
      costCenterId: null,
      managerId,
    },
    version: 7,
    appliedRevision: 0,
  },
  branch: { id: branchId, code: "HQ", name: "Head Office", active: false },
  department: null,
  position: null,
  costCenter: null,
  manager: { id: managerId, employeeNumber: "MGR-001", legalName: "Manager One", working: false },
});
const change = (): EmploymentChange => ({
  employeeId,
  expectedVersion: 7,
  terms: { ...details().employee.terms, effectiveFrom: "2027-01-01" } as EmploymentChange["terms"],
  reason: " Approved reassignment ",
});

describe("employment details and revision contracts", () => {
  it("requires full directory access for assignment details and management separately for editing", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createPeopleFeature({ request });
    for (const permissions of [[], ["people.self.read"], ["people.team.read"], ["people.manage"]]) {
      expect(
        await feature.loadEmploymentDetails.execute(
          { companyId, permissions },
          employeeId,
          asOf,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(
        await feature.reviseEmployment.execute(
          { companyId, permissions },
          operation,
          change(),
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    }
    expect(
      await feature.reviseEmployment.execute(
        { companyId, permissions: ["people.read"] },
        operation,
        change(),
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await feature.loadEmploymentDetails.execute(access, "../other", asOf, signal()),
    ).toMatchObject({ ok: false, failure: { code: "employee_not_found" } });
    expect(
      await feature.loadEmploymentDetails.execute(access, employeeId, "2026-02-30", signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_employee_date" } });
    expect(request).not.toHaveBeenCalled();
    request.mockResolvedValueOnce(details());
    expect(
      await feature.loadEmploymentDetails.execute(
        { companyId, permissions: ["people.read"] },
        employeeId,
        asOf,
        signal(),
      ),
    ).toMatchObject({ ok: true });
  });

  it("retains historical terms, the current aggregate version and unavailable assignments without exposing profile fields", async () => {
    const dto = details();
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({
      ...dto,
      employee: {
        ...dto.employee,
        id: employeeId.toUpperCase(),
        person: { ...dto.employee.person, birthDate: "PRIVATE", accountId: "PRIVATE" },
        terms: { ...dto.employee.terms, branchId: branchId.toUpperCase() },
      },
      branch: { ...dto.branch, id: branchId.toUpperCase(), description: "PRIVATE" },
      manager: { ...dto.manager, email: "PRIVATE", accountId: "PRIVATE" },
    });
    const feature = createPeopleFeature({ request });
    const result = await feature.loadEmploymentDetails.execute(
      access,
      employeeId.toUpperCase(),
      asOf,
      signal(),
    );
    expect(result).toMatchObject({
      ok: true,
      value: {
        asOf,
        employee: {
          id: employeeId,
          version: 7,
          appliedRevision: 0,
          terms: { branchId, managerId },
        },
        branch: { id: branchId, active: false },
        manager: { working: false },
      },
    });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    if (!result.ok) throw new Error("Employment fixture failed");
    for (const value of [
      result.value,
      result.value.employee,
      result.value.employee.terms,
      result.value.branch,
      result.value.manager,
    ])
      expect(Object.isFrozen(value)).toBe(true);
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/employees/${employeeId}/employment?asOf=${asOf}`,
    );
    request.mockResolvedValueOnce({ ...dto, branch: null, manager: null });
    expect(
      await feature.loadEmploymentDetails.execute(access, employeeId, asOf, signal()),
    ).toMatchObject({
      ok: true,
      value: { branch: null, manager: null, employee: { terms: { branchId, managerId } } },
    });
  });

  it("rejects mismatched scope, dates, versions and reference identities within the data failure boundary", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createPeopleFeature({ request });
    const dto = details();
    for (const response of [
      { ...dto, asOf: "2026-10-02" },
      { ...dto, employee: { ...dto.employee, companyId: managerId } },
      { ...dto, employee: { ...dto.employee, id: managerId } },
      { ...dto, employee: { ...dto.employee, appliedRevision: 8 } },
      {
        ...dto,
        employee: { ...dto.employee, terms: { ...dto.employee.terms, branchId: undefined } },
      },
      { ...dto, branch: { ...dto.branch, id: managerId } },
      { ...dto, department: dto.branch },
      { ...dto, manager: { ...dto.manager, id: employeeId } },
      { ...dto, manager: { ...dto.manager, legalName: " " } },
      { ...dto, branch: { ...dto.branch, code: "invalid code" } },
    ]) {
      request.mockResolvedValueOnce(response);
      expect(
        await feature.loadEmploymentDetails.execute(access, employeeId, asOf, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });

  it("validates the proposed revision, including case-insensitive self management and version exhaustion before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createPeopleFeature({ request });
    for (const input of [
      { ...change(), expectedVersion: -1 },
      { ...change(), expectedVersion: Number.MAX_SAFE_INTEGER },
      { ...change(), reason: " " },
      { ...change(), reason: "r".repeat(1001) },
      {
        ...change(),
        terms: { ...change().terms, managerId: employeeId.toUpperCase() as EmployeeId },
      },
      { ...change(), terms: { ...change().terms, effectiveFrom: "2025-12-31" } },
      { ...change(), terms: { ...change().terms, effectiveFrom: "2027-02-30" } },
      { ...change(), terms: { ...change().terms, contract: "FIXED_TERM" as const } },
      { ...change(), terms: { ...change().terms, status: "ENDED" as const } },
    ])
      expect(
        await feature.reviseEmployment.execute(access, operation, input, signal()),
      ).toMatchObject({ ok: false });
    expect(
      await feature.reviseEmployment.execute(access, "invalid" as OperationId, change(), signal()),
    ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
  });

  it("posts only versioned employment fields under the original operation and validates the exact acknowledgement", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce({ id: employeeId.toUpperCase(), version: 8 });
    const feature = createPeopleFeature({ request });
    const input = { ...change(), terms: { ...change().terms, accountId: "PRIVATE" } };
    expect(await feature.reviseEmployment.execute(access, operation, input, signal())).toEqual({
      ok: true,
      value: { id: employeeId, version: 8 },
    });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/employees/${employeeId}/revisions`,
      method: "POST",
      operationId: operation,
      body: { version: 7, terms: change().terms, reason: "Approved reassignment" },
    });
    for (const receipt of [
      { id: managerId, version: 8 },
      { id: employeeId, version: 7 },
      { id: employeeId, version: 9 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(
        await feature.reviseEmployment.execute(access, operation, change(), signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });

  it("preserves safe failure codes, propagates cancellation and never retries an ambiguous mutation", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValueOnce(
      new HttpResponseError(409, {
        code: "reporting_cycle_or_depth",
        detail: "PRIVATE DATABASE DETAIL",
      }),
    );
    const feature = createPeopleFeature({ request });
    expect(await feature.reviseEmployment.execute(access, operation, change(), signal())).toEqual({
      ok: false,
      failure: { code: "reporting_cycle_or_depth", fields: {}, parameters: {} },
    });
    let resolve!: (value: unknown) => void;
    request.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const pending = new AbortController();
    const response = feature.reviseEmployment.execute(access, operation, change(), pending.signal);
    pending.abort();
    resolve({ id: employeeId, version: 8 });
    await expect(response).rejects.toMatchObject({ name: "AbortError" });
    expect(() =>
      feature.loadEmploymentDetails.execute(access, employeeId, asOf, pending.signal),
    ).toThrow();
    expect(() =>
      feature.reviseEmployment.execute(access, operation, change(), pending.signal),
    ).toThrow();
    expect(request).toHaveBeenCalledTimes(2);
  });
});
