import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { OperationId } from "../../../core/domain/identifiers";
import type { HolidayChange } from "../domain/entities/holiday-change";
import {
  holidayAccess as access,
  holidayChangeFixture as changeFixture,
  company,
  holidayDtoFixture,
  identifier,
  operation,
  holidayRangeFixture as range,
  signal,
} from "./holiday-fixture";
import { createWorkforceFeature } from "./workforce-feature";

describe("holiday feature boundaries", () => {
  it("sends normalized calendar values, an observed version and one operation in the active company", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: identifier().toUpperCase(), version: 0 });
    const feature = createWorkforceFeature({ request });
    const change = {
      ...changeFixture(),
      id: identifier().toUpperCase(),
      name: " New year ",
      reason: " Annual calendar ",
    };
    expect(await feature.saveHoliday.execute(access, operation, change, signal())).toEqual({
      ok: true,
      value: { id: identifier(), version: 0 },
    });
    expect(request).toHaveBeenCalledTimes(1);
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${company}/workforce/holidays/${identifier()}`,
      method: "PUT",
      operationId: operation,
      body: {
        workDate: "2026-01-01",
        name: "New year",
        active: true,
        expectedVersion: null,
        reason: "Annual calendar",
      },
    });
    expect(change.name).toBe(" New year ");
  });

  it("rejects missing grants, invalid calendar dates, identifiers, reasons and versions before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createWorkforceFeature({ request });
    for (const permissions of [
      [],
      ["company.read"],
      ["workforce.manage"],
      ["workforce.read", "workforce.manage"],
    ])
      expect(
        await feature.saveHoliday.execute(
          { ...access, permissions },
          operation,
          changeFixture(),
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    const input = changeFixture();
    const changes: HolidayChange[] = [
      { ...input, id: "../other" },
      { ...input, expectedVersion: -1 },
      { ...input, expectedVersion: 0.5 },
      { ...input, expectedVersion: Number.MAX_SAFE_INTEGER },
      { ...input, name: " " },
      { ...input, name: "x".repeat(201) },
      { ...input, reason: " " },
      { ...input, reason: "x".repeat(1001) },
      ...[
        "2026-02-29",
        "2026-04-31",
        "0000-01-01",
        "2026-01-01T00:00:00Z",
        "2026-1-1",
        "10000-01-01",
      ].map((workDate) => ({ ...input, workDate })),
    ];
    for (const change of changes)
      expect(await feature.saveHoliday.execute(access, operation, change, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_holiday" },
      });
    expect(
      (await feature.saveHoliday.execute(access, "invalid" as OperationId, input, signal())).ok,
    ).toBe(false);
    expect((await feature.loadHoliday.execute(access, "../other", signal())).ok).toBe(false);
    expect(
      (await feature.loadHoliday.execute({ ...access, permissions: [] }, identifier(), signal()))
        .ok,
    ).toBe(false);
    expect(
      (
        await feature.loadHolidays.execute(
          { ...access, permissions: ["workforce.read"] },
          range,
          signal(),
        )
      ).ok,
    ).toBe(false);
    for (const invalid of [
      { from: "2026-02-29", until: "2026-03-01" },
      { from: "2026-10-11", until: "2026-10-10" },
      { from: "2024-01-01", until: "2025-01-01" },
      { from: "0000-01-01", until: "0000-12-31" },
    ])
      expect(await feature.loadHolidays.execute(access, invalid, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_calendar_range" },
      });
    expect(request).not.toHaveBeenCalled();
  });

  it("returns immutable holiday details under company read access and rejects a foreign or malformed response", async () => {
    const dto = holidayDtoFixture();
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const feature = createWorkforceFeature({ request });
    const result = await feature.loadHoliday.execute(
      { ...access, permissions: ["company.read"] },
      identifier().toUpperCase(),
      signal(),
    );
    expect(result).toMatchObject({
      ok: true,
      value: { id: identifier(), companyId: company, workDate: "2026-01-01" },
    });
    if (result.ok) {
      expect(Object.isFrozen(result.value)).toBe(true);
      dto.name = "Changed raw data";
      expect(result.value.name).toBe("Holiday 1");
    }
    for (const invalid of [
      { ...holidayDtoFixture(), id: identifier(2) },
      { ...holidayDtoFixture(), version: Number.MAX_SAFE_INTEGER + 1 },
      { ...holidayDtoFixture(), workDate: "2026-02-29" },
      { ...holidayDtoFixture(), name: " " },
    ]) {
      request.mockResolvedValue(invalid);
      expect(await feature.loadHoliday.execute(access, identifier(), signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("admits a complete leap year and rejects oversized, duplicate, unordered or out-of-range lists", async () => {
    const first = Date.UTC(2024, 0, 1);
    const items = Array.from({ length: 366 }, (_, index) => ({
      ...holidayDtoFixture(),
      id: identifier(index + 1),
      workDate: new Date(first + index * 86_400_000).toISOString().slice(0, 10),
    }));
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(items);
    const feature = createWorkforceFeature({ request });
    const fullYear = { from: "2024-01-01", until: "2024-12-31" };
    const result = await feature.loadHolidays.execute(access, fullYear, signal());
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.value).toHaveLength(366);
      expect(Object.isFrozen(result.value)).toBe(true);
    }
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${company}/workforce/holidays?from=2024-01-01&until=2024-12-31`,
    );
    for (const invalid of [
      [...items, { ...holidayDtoFixture(), workDate: "2025-01-01" }],
      [items[0], items[0]],
      [items[0], { ...items[0], id: identifier(2) }],
      [items[1], items[0]],
      [items[0], { ...items[1], id: identifier() }],
      [{ ...holidayDtoFixture(), workDate: "2023-12-31" }],
      [{ ...holidayDtoFixture(), workDate: "2025-01-01" }],
    ]) {
      request.mockResolvedValue(invalid);
      expect(await feature.loadHolidays.execute(access, fullYear, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    request.mockResolvedValue([]);
    expect(
      await feature.loadHolidays.execute(
        access,
        { from: "2024-02-29", until: "2024-02-29" },
        signal(),
      ),
    ).toEqual({ ok: true, value: [] });
  });

  it("requires the exact mutation receipt and preserves domain failures without retrying", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createWorkforceFeature({ request });
    for (const receipt of [
      { id: identifier(2), version: 0 },
      { id: identifier(), version: 1 },
    ]) {
      request.mockResolvedValue(receipt);
      expect(
        await feature.saveHoliday.execute(access, operation, changeFixture(), signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    request.mockResolvedValue({ id: identifier(), version: 5 });
    expect(
      (
        await feature.saveHoliday.execute(
          access,
          operation,
          { ...changeFixture(), expectedVersion: 4 },
          signal(),
        )
      ).ok,
    ).toBe(true);
    const failure = { code: "work_period_locked", fields: {}, parameters: {} };
    request.mockRejectedValue(new HttpResponseError(409, failure));
    expect(await feature.saveHoliday.execute(access, operation, changeFixture(), signal())).toEqual(
      { ok: false, failure },
    );
    expect(request).toHaveBeenCalledTimes(4);
  });

  it("propagates cancellation before I/O and after a late successful transport result", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createWorkforceFeature({ request });
    const cancelled = new AbortController();
    cancelled.abort();
    expect(() => feature.loadHoliday.execute(access, identifier(), cancelled.signal)).toThrow();
    expect(request).not.toHaveBeenCalled();
    const pending = new AbortController();
    request.mockImplementation(async () => {
      pending.abort();
      return holidayDtoFixture();
    });
    await expect(
      feature.loadHoliday.execute(access, identifier(), pending.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(request).toHaveBeenCalledTimes(1);
  });
});
