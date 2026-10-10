import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { EmployeeId } from "../domain/entities/employee";
import type { EmployeeCreation } from "../domain/entities/employee-creation";
import type { PersonId } from "../domain/entities/person-profile";
import { createPeopleFeature } from "./people-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const employeeId = "40000000-abcd-4000-8000-000000000001" as EmployeeId;
const personId = "50000000-abcd-4000-8000-000000000001" as PersonId;
const operation = "60000000-0000-4000-8000-000000000001" as OperationId;
const access = { companyId, permissions: ["people.manage"] };
const input = (): EmployeeCreation => ({
  employeeId,
  personId,
  employeeNumber: " emp-001 ",
  legalName: " New employee ",
  birthDate: null,
  nationality: " id ",
  email: " PERSON@INTERNAL ",
  startDate: "2027-01-01",
  endDate: null,
  contract: "PERMANENT",
  status: "ACTIVE",
  branchId: null,
  departmentId: null,
  positionId: null,
  costCenterId: null,
  managerId: null,
  reason: " Onboarding ",
});
const signal = () => new AbortController().signal;

describe("employee creation contract", () => {
  it("requires management and validates the initial employment before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createPeopleFeature({ request });
    for (const permissions of [[], ["people.read"], ["people.profile.manage"], ["people.import"]])
      expect(
        await feature.createEmployee.execute(
          { companyId, permissions },
          operation,
          input(),
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const change of [
      { employeeNumber: "x" },
      { legalName: " " },
      { birthDate: "2026-02-30" },
      { nationality: "123" },
      { email: "invalid" },
      { reason: " " },
      { startDate: "0000-01-01" },
      { startDate: "2026-02-30" },
      { endDate: "2026-01-01" },
      { contract: "FIXED_TERM" as const },
      { contract: "FIXED_TERM" as const, status: "PROBATION" as const, endDate: "2027-12-31" },
      { status: "ENDED" as const },
      { managerId: employeeId },
      { employeeId: "../other" as EmployeeId },
    ])
      expect(
        await feature.createEmployee.execute(
          access,
          operation,
          { ...input(), ...change },
          signal(),
        ),
      ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
  });
  it("creates a distinct person and employment with one idempotent command and no account binding", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: employeeId.toUpperCase(), version: 0, ignored: "PRIVATE" });
    const result = await createPeopleFeature({ request }).createEmployee.execute(
      access,
      operation,
      input(),
      signal(),
    );
    expect(result).toEqual({ ok: true, value: { id: employeeId, version: 0 } });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/employees`,
      method: "POST",
      operationId: operation,
      body: {
        id: employeeId,
        employeeNumber: "EMP-001",
        person: {
          id: personId,
          legalName: "New employee",
          birthDate: null,
          nationality: "ID",
          email: "person@internal",
        },
        terms: {
          effectiveFrom: "2027-01-01",
          startDate: "2027-01-01",
          endDate: null,
          contract: "PERMANENT",
          status: "ACTIVE",
          branchId: null,
          departmentId: null,
          positionId: null,
          costCenterId: null,
          managerId: null,
        },
        reason: "Onboarding",
      },
    });
    if (!result.ok) throw new Error("Creation fixture failed");
    expect(Object.isFrozen(result.value)).toBe(true);
  });
  it("rejects an unrelated or advanced receipt instead of acknowledging a different operation", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createPeopleFeature({ request });
    for (const receipt of [
      { id: personId, version: 0 },
      { id: employeeId, version: 1 },
      { id: employeeId, version: -1 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(
        await feature.createEmployee.execute(access, operation, input(), signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    expect(request).toHaveBeenCalledTimes(3);
  });
  it("preserves classified field errors and propagates cancelled or late operations without retry", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValueOnce(
      new HttpResponseError(400, {
        code: "invalid_person",
        fields: { nationality: "invalid_country" },
        detail: "PRIVATE DB ERROR",
      }),
    );
    const feature = createPeopleFeature({ request });
    expect(await feature.createEmployee.execute(access, operation, input(), signal())).toEqual({
      ok: false,
      failure: {
        code: "invalid_person",
        fields: { nationality: "invalid_country" },
        parameters: {},
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
    const response = feature.createEmployee.execute(access, operation, input(), pending.signal);
    pending.abort();
    resolve({ id: employeeId, version: 0 });
    await expect(response).rejects.toMatchObject({ name: "AbortError" });
    expect(() =>
      feature.createEmployee.execute(access, operation, input(), pending.signal),
    ).toThrow();
    expect(request).toHaveBeenCalledTimes(2);
  });
});
