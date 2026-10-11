import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { EmploymentTransferReviewDto } from "../data/models/employment-transfer-dto";
import type { EmployeeId } from "../domain/entities/employee";
import type { EmploymentTransferInput } from "../domain/entities/employment-transfer-input";
import { createPeopleFeature } from "./people-feature";

const source = "10000000-0000-4000-8000-000000000001" as CompanyId;
const target = "10000000-0000-4000-8000-000000000002" as CompanyId;
const employeeId = "40000000-abcd-4000-8000-000000000001" as EmployeeId;
const targetEmploymentId = "40000000-abcd-4000-8000-000000000002" as EmployeeId;
const operation = "60000000-0000-4000-8000-000000000001" as OperationId;
const date = "2026-10-01";
const access = {
  companyId: source,
  permissions: ["people.read", "people.manage", "people.transfer"],
};
const signal = () => new AbortController().signal;
const review = (): EmploymentTransferReviewDto => ({
  employee: {
    id: employeeId,
    companyId: source,
    employeeNumber: "NORTH-001",
    person: { legalName: "Employee One", email: null },
    version: 7,
    appliedRevision: 0,
    terms: {
      effectiveFrom: "2026-01-01",
      startDate: "2026-01-01",
      endDate: null,
      status: "ACTIVE",
      contract: "PERMANENT",
    },
  },
  sourceCompany: { id: source, code: "NORTH", name: "North Company", timezone: "Asia/Jakarta" },
  targetCompany: { id: target, code: "SOUTH", name: "South Company", timezone: "Asia/Makassar" },
  sourceDate: date,
  targetDate: date,
  evaluatedAt: "2026-10-01T12:00:00Z",
  targetPermissions: ["people.manage", "people.transfer", "company.read"],
});
const input = (): EmploymentTransferInput => ({
  employeeId,
  targetCompanyId: target,
  targetEmploymentId,
  expectedVersion: 7,
  employeeNumber: " south-001 ",
  reason: " Approved transfer ",
  terms: {
    effectiveFrom: date,
    startDate: date,
    endDate: null,
    contract: "PERMANENT",
    status: "ACTIVE",
    branchId: null,
    departmentId: null,
    positionId: null,
    costCenterId: null,
    managerId: null,
  },
});
const history = () => ({
  id: operation,
  sourceCompanyId: source,
  sourceEmploymentId: employeeId,
  targetCompanyId: target,
  targetEmploymentId,
  sourceVersion: 8,
  effectiveDate: date,
  reason: "Approved transfer",
  recordedAt: "2026-10-01T12:00:00Z",
});

describe("employment transfer data and application boundaries", () => {
  it("requires all source grants for transfer and full directory access for history before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const f = createPeopleFeature({ request });
    for (const permissions of [
      [],
      ["people.read", "people.manage"],
      ["people.manage", "people.transfer"],
      ["people.read", "people.transfer"],
    ]) {
      expect(
        await f.loadEmploymentTransferReview.execute(
          { ...access, permissions },
          employeeId,
          target,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(
        await f.transferEmployee.execute({ ...access, permissions }, operation, input(), signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    }
    expect(
      await f.loadEmploymentTransfers.execute(
        { ...access, permissions: ["people.self.read"] },
        employeeId,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const destination of [source, "../other"])
      expect(
        await f.loadEmploymentTransferReview.execute(access, employeeId, destination, signal()),
      ).toMatchObject({ ok: false });
    expect(await f.loadEmploymentTransfers.execute(access, "../other", signal())).toMatchObject({
      ok: false,
    });
    expect(request).not.toHaveBeenCalled();
    request.mockResolvedValueOnce([]);
    expect(
      await f.loadEmploymentTransfers.execute(
        { ...access, permissions: ["people.read"] },
        employeeId,
        signal(),
      ),
    ).toEqual({ ok: true, value: [] });
  });

  it("uses server company dates and current version, strips private fields and freezes the scoped review", async () => {
    const dto = review();
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({
      ...dto,
      employee: {
        ...dto.employee,
        id: employeeId.toUpperCase(),
        accountId: "PRIVATE",
        person: { ...dto.employee.person, birthDate: "PRIVATE" },
      },
    });
    const result = await createPeopleFeature({ request }).loadEmploymentTransferReview.execute(
      access,
      employeeId.toUpperCase(),
      target,
      signal(),
    );
    expect(result).toMatchObject({
      ok: true,
      value: {
        sourceDate: date,
        targetDate: date,
        employee: { id: employeeId, version: 7 },
        targetPermissions: dto.targetPermissions,
      },
    });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${source}/employees/${employeeId}/transfer-review?targetCompanyId=${target}`,
    );
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    if (!result.ok) throw new Error("Review fixture failed");
    for (const value of [
      result.value,
      result.value.employee,
      result.value.employee.terms,
      result.value.sourceCompany,
      result.value.targetCompany,
      result.value.targetPermissions,
    ])
      expect(Object.isFrozen(value)).toBe(true);
    dto.targetPermissions.push("identity.members.manage");
    expect(result.value.targetPermissions).not.toContain("identity.members.manage");
  });

  it("rejects incoherent company scope, dates, employment versions and destination authority inside the safe boundary", async () => {
    const dto = review();
    const request = vi.fn<HttpClient["request"]>();
    const f = createPeopleFeature({ request });
    for (const response of [
      { ...dto, sourceCompany: dto.targetCompany },
      { ...dto, targetCompany: dto.sourceCompany },
      { ...dto, employee: { ...dto.employee, companyId: target } },
      { ...dto, employee: { ...dto.employee, id: targetEmploymentId } },
      { ...dto, employee: { ...dto.employee, appliedRevision: 8 } },
      {
        ...dto,
        employee: {
          ...dto.employee,
          terms: { ...dto.employee.terms, startDate: date, effectiveFrom: date },
        },
      },
      { ...dto, targetDate: "2026-10-02" },
      { ...dto, sourceDate: "2026-02-30", targetDate: "2026-02-30" },
      { ...dto, targetPermissions: ["people.manage"] },
      { ...dto, targetPermissions: [...dto.targetPermissions, "people.manage"] },
    ]) {
      request.mockResolvedValueOnce(response);
      expect(
        await f.loadEmploymentTransferReview.execute(access, employeeId, target, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });

  it("validates immediate destination terms and stable identifiers before sending a command", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const f = createPeopleFeature({ request });
    for (const change of [
      { ...input(), targetCompanyId: source },
      { ...input(), expectedVersion: Number.MAX_SAFE_INTEGER },
      { ...input(), employeeNumber: "!" },
      { ...input(), reason: " " },
      { ...input(), terms: { ...input().terms, effectiveFrom: "2026-10-02" } },
      { ...input(), terms: { ...input().terms, endDate: "2026-09-30" } },
      { ...input(), terms: { ...input().terms, status: "SUSPENDED" as const } },
      { ...input(), terms: { ...input().terms, contract: "FIXED_TERM" as const } },
      {
        ...input(),
        terms: { ...input().terms, managerId: targetEmploymentId.toUpperCase() as EmployeeId },
      },
    ])
      expect(await f.transferEmployee.execute(access, operation, change, signal())).toMatchObject({
        ok: false,
      });
    expect(
      await f.transferEmployee.execute(access, "invalid" as OperationId, input(), signal()),
    ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
  });

  it("posts only destination terms and the observed source version and checks the exact receipt", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce({ id: targetEmploymentId.toUpperCase(), version: 0 });
    const f = createPeopleFeature({ request });
    const change = {
      ...input(),
      accountId: "PRIVATE",
      terms: { ...input().terms, permissions: ["PRIVATE"] },
    };
    expect(await f.transferEmployee.execute(access, operation, change, signal())).toEqual({
      ok: true,
      value: { id: targetEmploymentId, version: 0 },
    });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${source}/employees/${employeeId}/transfer`,
      method: "POST",
      operationId: operation,
      body: {
        targetCompanyId: target,
        targetEmploymentId,
        expectedVersion: 7,
        employeeNumber: "SOUTH-001",
        reason: "Approved transfer",
        terms: input().terms,
      },
    });
    for (const receipt of [
      { id: employeeId, version: 0 },
      { id: targetEmploymentId, version: 1 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(await f.transferEmployee.execute(access, operation, input(), signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("validates the bounded immutable history against the requested employment on either side", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue([history()]);
    const f = createPeopleFeature({ request });
    for (const [companyId, id] of [
      [source, employeeId],
      [target, targetEmploymentId],
    ] as const) {
      const result = await f.loadEmploymentTransfers.execute(
        { ...access, companyId },
        id,
        signal(),
      );
      expect(result).toEqual({ ok: true, value: [history()] });
      if (!result.ok) throw new Error("History fixture failed");
      expect(Object.isFrozen(result.value)).toBe(true);
      expect(Object.isFrozen(result.value[0])).toBe(true);
    }
    for (const response of [
      [history(), history()],
      [{ ...history(), sourceEmploymentId: targetEmploymentId }],
      [{ ...history(), targetCompanyId: source }],
      [{ ...history(), effectiveDate: "2026-02-30" }],
      Array(3).fill(history()),
    ]) {
      request.mockResolvedValueOnce(response);
      expect(await f.loadEmploymentTransfers.execute(access, employeeId, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("preserves classified failures, propagates cancellation and never retries a command internally", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValueOnce(
      new HttpResponseError(403, {
        code: "destination_membership_required",
        parameters: { companyId: target },
        detail: "PRIVATE",
      }),
    );
    const f = createPeopleFeature({ request });
    expect(await f.transferEmployee.execute(access, operation, input(), signal())).toEqual({
      ok: false,
      failure: {
        code: "destination_membership_required",
        fields: {},
        parameters: { companyId: target },
      },
    });
    let resolve!: (value: unknown) => void;
    request.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const pending = new AbortController();
    const result = f.transferEmployee.execute(access, operation, input(), pending.signal);
    pending.abort();
    resolve({ id: targetEmploymentId, version: 0 });
    await expect(result).rejects.toMatchObject({ name: "AbortError" });
    expect(() => f.loadEmploymentTransfers.execute(access, employeeId, pending.signal)).toThrow();
    expect(() =>
      f.loadEmploymentTransferReview.execute(access, employeeId, target, pending.signal),
    ).toThrow();
    expect(request).toHaveBeenCalledTimes(2);
  });
});
