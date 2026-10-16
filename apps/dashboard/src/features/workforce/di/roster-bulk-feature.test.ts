import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { RosterBulkSelection } from "../domain/entities/roster-bulk-selection";
import {
  access,
  company,
  identifier,
  operation,
  shiftDtoFixture,
  signal,
} from "./calendar-fixture";
import { bulkChange, bulkDtoFixture, bulkSelection } from "./roster-bulk-fixture";
import { createWorkforceFeature } from "./workforce-feature";

describe("bulk roster data and policy boundaries", () => {
  it("detaches and normalizes a bounded read-only selection before HTTP and deeply owns its review", async () => {
    const dto = bulkDtoFixture();
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const original = bulkSelection();
    const employeeIds = [...original.employeeIds].reverse().map((value) => value.toUpperCase());
    const dates = [...original.dates].reverse();
    const pending = signal();
    const result = await createWorkforceFeature({ request }).reviewRosterBulk.execute(
      access,
      { ...original, employeeIds, dates },
      pending,
    );
    expect(request).toHaveBeenCalledExactlyOnceWith(
      {
        path: `/api/v1/companies/${company}/workforce/roster/bulk/review`,
        method: "POST",
        body: original,
      },
      pending,
    );
    expect(result).toMatchObject({
      ok: true,
      value: { companyId: company, selection: original, changeCount: 4 },
    });
    if (!result.ok) throw new Error("Missing review");
    employeeIds.splice(0);
    dates.splice(0);
    const source = dto.employees[0];
    if (!source) throw new Error("Missing employee");
    source.employee.legalName = "Changed caller";
    source.days.splice(0);
    expect(result.value.employees[0]?.employee.legalName).toBe("Employee 1");
    expect(result.value.employees[0]?.days).toHaveLength(2);
    expect(Object.isFrozen(result.value.employees[0]?.days[0]?.before)).toBe(true);
    expect(Object.isFrozen(result.value.selection.employeeIds)).toBe(true);
    expect(Object.isFrozen(result.value.selection.dates)).toBe(true);
  });

  it("rejects invalid capacity, duplicate identities, invalid dates and inconsistent mode before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createWorkforceFeature({ request });
    const input = bulkSelection();
    const shift = { id: shiftDtoFixture().id, version: 0 };
    const invalid: RosterBulkSelection[] = [
      { ...input, employeeIds: [] },
      { ...input, dates: [] },
      { ...input, employeeIds: ["../other"] },
      { ...input, employeeIds: [identifier(501), identifier(501).toUpperCase()] },
      { ...input, employeeIds: Array.from({ length: 51 }, (_, index) => identifier(index + 100)) },
      {
        ...input,
        employeeIds: Array.from({ length: 50 }, (_, index) => identifier(index + 100)),
        dates: ["2026-10-12", "2026-10-13", "2026-10-14", "2026-10-15", "2026-10-16"],
      },
      { ...input, dates: ["2026-10-12", "2026-10-12"] },
      { ...input, dates: ["1899-12-31"] },
      { ...input, dates: ["2026-02-29"] },
      { ...input, dates: ["2026-10-01", "2026-11-01"] },
      { ...input, mode: "SHIFT", shift: null },
      { ...input, mode: "CALENDAR", shift },
      { ...input, mode: "SHIFT", shift: { ...shift, version: 0.5 } },
      { ...input, mode: "SHIFT", shift: { ...shift, version: Number.MAX_SAFE_INTEGER + 1 } },
    ];
    for (const selection of invalid)
      expect(await feature.reviewRosterBulk.execute(access, selection, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_roster_bulk_selection" },
      });
    expect(request).not.toHaveBeenCalled();
  });

  it("permits read scope but rejects management, foreign company and invalid commands before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(bulkDtoFixture());
    const feature = createWorkforceFeature({ request });
    const read = { ...access, permissions: ["workforce.read"] };
    expect((await feature.reviewRosterBulk.execute(read, bulkSelection(), signal())).ok).toBe(true);
    request.mockClear();
    expect(
      await feature.reviewRosterBulk.execute(
        { ...access, permissions: [] },
        bulkSelection(),
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await feature.applyRosterBulk.execute(read, operation, bulkChange(), signal()),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await feature.applyRosterBulk.execute(
        access,
        operation,
        { ...bulkChange(), companyId: identifier(999) as CompanyId },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const input of [
      { ...bulkChange(), reason: " " },
      { ...bulkChange(), reason: "a".repeat(1001) },
      { ...bulkChange(), reviewRevision: "opaque" },
    ])
      expect(
        await feature.applyRosterBulk.execute(access, operation, input, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_roster_bulk_change" } });
    expect(
      await feature.applyRosterBulk.execute(access, "bad" as OperationId, bulkChange(), signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_roster_bulk_change" } });
    expect(request).not.toHaveBeenCalled();
  });

  it.each(["DAY_OFF", "SHIFT", "CALENDAR"] as const)(
    "accepts the reviewed %s transition without flattening source identities",
    async (mode) => {
      const selection: RosterBulkSelection = {
        ...bulkSelection(),
        mode,
        shift: mode === "SHIFT" ? { id: shiftDtoFixture().id, version: 3 } : null,
      };
      const request = vi.fn<HttpClient["request"]>().mockResolvedValue(bulkDtoFixture(selection));
      const result = await createWorkforceFeature({ request }).reviewRosterBulk.execute(
        access,
        selection,
        signal(),
      );
      expect(result).toMatchObject({ ok: true, value: { selection, changeCount: 4 } });
    },
  );

  it("accepts 200 explicit cells and sparse dates including the last supported year", async () => {
    const selection: RosterBulkSelection = {
      ...bulkSelection(),
      employeeIds: Array.from({ length: 50 }, (_, index) => identifier(index + 100)),
      dates: ["9999-12-01", "9999-12-10", "9999-12-20", "9999-12-31"],
    };
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(bulkDtoFixture(selection));
    const result = await createWorkforceFeature({ request }).reviewRosterBulk.execute(
      access,
      selection,
      signal(),
    );
    expect(result).toMatchObject({ ok: true, value: { changeCount: 200 } });
  });

  it("preserves unchanged active overrides and retained inactive versions", async () => {
    const dto = bulkDtoFixture();
    const first = dto.employees[0]?.days[0];
    const second = dto.employees[0]?.days[1];
    if (!first || !second) throw new Error("Missing days");
    first.expectedVersion = 4;
    first.overrideActive = true;
    first.changed = false;
    first.before = { ...first.after, originVersion: 4 };
    first.after = { ...first.before };
    second.expectedVersion = 8;
    second.after.originVersion = 9;
    dto.changeCount = 3;
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    expect(
      await createWorkforceFeature({ request }).reviewRosterBulk.execute(
        access,
        bulkSelection(),
        signal(),
      ),
    ).toMatchObject({
      ok: true,
      value: {
        changeCount: 3,
        employees: [
          {
            days: [
              { expectedVersion: 4, changed: false },
              { expectedVersion: 8, changed: true },
            ],
          },
          expect.anything(),
        ],
      },
    });
  });

  it("rejects substituted, reordered, omitted and inconsistent review facts inside the HTTP boundary", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createWorkforceFeature({ request });
    const mutations: ((dto: ReturnType<typeof bulkDtoFixture>) => void)[] = [
      (dto) => {
        dto.employeeIds[0] = identifier(900);
      },
      (dto) => {
        dto.employees.reverse();
      },
      (dto) => {
        dto.employees.pop();
      },
      (dto) => {
        dto.dates.reverse();
      },
      (dto) => {
        dto.mode = "SHIFT";
      },
      (dto) => {
        dto.shiftVersion = 0;
      },
      (dto) => {
        dto.changeCount = 3;
      },
      (dto) => {
        dto.revision = "invalid";
      },
      (dto) => {
        dto.employees[0]?.days.reverse();
      },
      (dto) => {
        dto.employees[0]?.days.pop();
      },
      (dto) => {
        const day = dto.employees[0]?.days[0];
        if (day) day.expectedVersion = -1;
      },
      (dto) => {
        const day = dto.employees[0]?.days[0];
        if (day) day.expectedVersion = 0.5;
      },
      (dto) => {
        const day = dto.employees[0]?.days[0];
        if (day) day.overrideActive = true;
      },
      (dto) => {
        const day = dto.employees[0]?.days[0];
        if (day) day.before.workDate = "2026-10-14";
      },
      (dto) => {
        const day = dto.employees[0]?.days[0];
        if (day) day.before.plannedMinutes = 1;
      },
      (dto) => {
        const day = dto.employees[0]?.days[0];
        if (day) day.after.origin = "PATTERN";
      },
      (dto) => {
        const day = dto.employees[0]?.days[0];
        if (day) day.after.originVersion = 1;
      },
      (dto) => {
        const day = dto.employees[0]?.days[0];
        if (day) day.changed = false;
      },
    ];
    for (const mutate of mutations) {
      const dto = bulkDtoFixture();
      mutate(dto);
      request.mockResolvedValue(dto);
      expect(
        await feature.reviewRosterBulk.execute(access, bulkSelection(), signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });

  it("rejects a changed selected shift and a reset that still resolves to the roster", async () => {
    for (const mode of ["SHIFT", "CALENDAR"] as const) {
      const selection: RosterBulkSelection = {
        ...bulkSelection(),
        mode,
        shift: mode === "SHIFT" ? { id: shiftDtoFixture().id, version: 3 } : null,
      };
      const dto = bulkDtoFixture(selection);
      const day = dto.employees[0]?.days[0];
      if (!day) throw new Error("Missing day");
      if (mode === "SHIFT" && day.after.shift) day.after.shift.revision = 4;
      else day.after.origin = "ROSTER";
      const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
      expect(
        await createWorkforceFeature({ request }).reviewRosterBulk.execute(
          access,
          selection,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });

  it("uses the original scoped operation and accepts only its acknowledgement", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({ id: operation, version: 0 });
    const feature = createWorkforceFeature({ request });
    const pending = signal();
    expect(
      await feature.applyRosterBulk.execute(
        access,
        operation,
        { ...bulkChange(), reason: " Coverage update " },
        pending,
      ),
    ).toEqual({ ok: true, value: { id: operation, version: 0 } });
    expect(request).toHaveBeenCalledExactlyOnceWith(
      {
        path: `/api/v1/companies/${company}/workforce/roster/bulk`,
        method: "POST",
        operationId: operation,
        body: {
          selection: bulkSelection(),
          reviewRevision: "b".repeat(64),
          reason: "Coverage update",
        },
      },
      pending,
    );
    for (const receipt of [
      { id: identifier(3), version: 0 },
      { id: operation, version: 1 },
      { id: operation, version: 0.5 },
    ]) {
      request.mockResolvedValue(receipt);
      expect(
        await feature.applyRosterBulk.execute(access, operation, bulkChange(), signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });

  it("preserves typed failure parameters and propagates preflight and late cancellation", async () => {
    const failure = {
      code: "work_period_locked",
      fields: {},
      parameters: { employeeId: identifier(501), workDate: "2026-10-12" },
    };
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValue(new HttpResponseError(409, failure));
    const feature = createWorkforceFeature({ request });
    expect(
      await feature.applyRosterBulk.execute(access, operation, bulkChange(), signal()),
    ).toEqual({ ok: false, failure });
    const cancelled = new AbortController();
    cancelled.abort();
    expect(() =>
      feature.reviewRosterBulk.execute(access, bulkSelection(), cancelled.signal),
    ).toThrow();
    expect(() =>
      feature.applyRosterBulk.execute(access, operation, bulkChange(), cancelled.signal),
    ).toThrow();
    const late = new AbortController();
    request.mockImplementation(async () => {
      late.abort();
      return bulkDtoFixture();
    });
    await expect(
      feature.reviewRosterBulk.execute(access, bulkSelection(), late.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    const afterSave = new AbortController();
    request.mockImplementation(async () => {
      afterSave.abort();
      return { id: operation, version: 0 };
    });
    await expect(
      feature.applyRosterBulk.execute(access, operation, bulkChange(), afterSave.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
});
