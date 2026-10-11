import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { OperationId } from "../../../core/domain/identifiers";
import type { ShiftChange } from "../domain/entities/shift-change";
import { createWorkforceFeature } from "./workforce-feature";
import {
  access,
  changeFixture,
  company,
  identifier,
  operation,
  shiftDtoFixture,
  signal,
} from "./workforce-fixture";

describe("shift feature boundaries", () => {
  it("normalizes an overnight template and sends one scoped command with an observed version", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: identifier().toUpperCase(), version: 0 });
    const feature = createWorkforceFeature({ request });
    const input = changeFixture();
    const change = {
      ...input,
      id: input.id.toUpperCase(),
      reason: " New coverage ",
      details: {
        ...input.details,
        code: " night ",
        name: " Night shift ",
        timezone: " Asia/Jakarta ",
      },
    };
    const result = await feature.saveShift.execute(access, operation, change, signal());
    expect(result).toEqual({ ok: true, value: { id: identifier(), version: 0 } });
    expect(request).toHaveBeenCalledTimes(1);
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${company}/workforce/shifts/${identifier()}`,
      method: "PUT",
      operationId: operation,
      body: {
        ...input.details,
        code: "NIGHT",
        name: "Night shift",
        active: true,
        expectedVersion: null,
        reason: "New coverage",
      },
    });
  });

  it("rejects missing grants, invalid identifiers, versions and time or location policy before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createWorkforceFeature({ request });
    for (const permissions of [[], ["workforce.read"], ["workforce.manage"]])
      expect(
        await feature.saveShift.execute(
          { ...access, permissions },
          operation,
          changeFixture(),
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    const input = changeFixture();
    const invalid: ShiftChange[] = [
      { ...input, id: "../other" },
      { ...input, expectedVersion: -1 },
      { ...input, expectedVersion: Number.MAX_SAFE_INTEGER },
      { ...input, reason: " " },
      { ...input, reason: "x".repeat(1001) },
      ...[
        { code: "A" },
        { name: " " },
        { timezone: "" },
        { startsAt: "23:61" },
        { startsAt: "22:00:01" },
        { startsAt: "06:00", endsAt: "06:00" },
        { breakMinutes: 480 },
        { breakMinutes: 0.5 },
        { maxAccuracyMeters: Number.NaN },
        { locationRequired: false },
        { fence: { latitude: 91, longitude: 0, radiusMeters: 1 } },
        { fence: { latitude: 0, longitude: 181, radiusMeters: 1 } },
        { fence: { latitude: 0, longitude: 0, radiusMeters: 100_001 } },
      ].map((details) => ({ ...input, details: { ...input.details, ...details } })),
    ];
    for (const change of invalid)
      expect((await feature.saveShift.execute(access, operation, change, signal())).ok).toBe(false);
    expect(
      (await feature.saveShift.execute(access, "escape" as OperationId, input, signal())).ok,
    ).toBe(false);
    expect((await feature.loadShift.execute(access, "../other", signal())).ok).toBe(false);
    expect(
      (await feature.loadShifts.execute(access, { query: "x".repeat(121), after: null }, signal()))
        .ok,
    ).toBe(false);
    expect(
      (await feature.loadShifts.execute(access, { query: "", after: "escape/path" }, signal())).ok,
    ).toBe(false);
    expect(
      (await feature.loadShift.execute({ ...access, permissions: [] }, identifier(), signal())).ok,
    ).toBe(false);
    expect(request).not.toHaveBeenCalled();
  });

  it("maps current templates into immutable scoped values and rejects mismatched detail responses", async () => {
    const dto = shiftDtoFixture();
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const feature = createWorkforceFeature({ request });
    const result = await feature.loadShift.execute(access, identifier().toUpperCase(), signal());
    expect(result).toMatchObject({
      ok: true,
      value: {
        id: identifier(),
        companyId: company,
        version: 0,
        details: { startsAt: "22:00", endsAt: "06:00" },
      },
    });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${company}/workforce/shifts/${identifier()}`,
    );
    if (result.ok) {
      expect(Object.isFrozen(result.value)).toBe(true);
      expect(Object.isFrozen(result.value.details.fence)).toBe(true);
      if (!dto.details.fence) throw new Error("Expected the fixture area restriction");
      dto.details.fence.latitude = 20;
      expect(result.value.details.fence?.latitude).toBe(-6.2);
    }
    for (const invalid of [
      { ...shiftDtoFixture(), id: identifier(2) },
      { ...shiftDtoFixture(), version: Number.MAX_SAFE_INTEGER + 1 },
      { ...shiftDtoFixture(), details: { ...shiftDtoFixture().details, startsAt: "22:00:01" } },
      { ...shiftDtoFixture(), details: { ...shiftDtoFixture().details, endsAt: "22:00:00" } },
    ]) {
      request.mockResolvedValue(invalid);
      expect(await feature.loadShift.execute(access, identifier(), signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("bounds list pages and rejects duplicate IDs, duplicate codes and non-progressing cursors", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createWorkforceFeature({ request });
    const items = Array.from({ length: 50 }, (_, index) => shiftDtoFixture(index + 1));
    const nextCursor = "SHIFT_050";
    const first = shiftDtoFixture();
    request.mockResolvedValue({ items, nextCursor });
    const page = await feature.loadShifts.execute(
      access,
      { query: " night ", after: null },
      signal(),
    );
    expect(page.ok).toBe(true);
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${company}/workforce/shifts?query=night&limit=50`,
    );
    if (page.ok) expect(Object.isFrozen(page.value.items)).toBe(true);
    for (const invalid of [
      { items: [...items, shiftDtoFixture(51)], nextCursor: null },
      { items: [items[0], items[0]], nextCursor: null },
      { items: [items[0], { ...items[1], details: first.details }], nextCursor: null },
      { items: [items[0]], nextCursor: first.details.code },
      { items, nextCursor: "UNRELATED" },
    ]) {
      request.mockResolvedValue(invalid);
      expect(
        await feature.loadShifts.execute(access, { query: "", after: null }, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    request.mockResolvedValue({ items, nextCursor });
    expect(
      await feature.loadShifts.execute(access, { query: "", after: nextCursor }, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("requires the expected receipt, preserves server classifications, and propagates cancellation", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createWorkforceFeature({ request });
    for (const receipt of [
      { id: identifier(2), version: 0 },
      { id: identifier(), version: 1 },
    ]) {
      request.mockResolvedValue(receipt);
      expect(
        await feature.saveShift.execute(access, operation, changeFixture(), signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    const problem = { code: "stale_version", fields: {}, parameters: {} };
    request.mockRejectedValue(new HttpResponseError(409, problem));
    expect(await feature.saveShift.execute(access, operation, changeFixture(), signal())).toEqual({
      ok: false,
      failure: problem,
    });
    const aborted = new AbortController();
    aborted.abort();
    const count = request.mock.calls.length;
    expect(() => feature.loadShift.execute(access, identifier(), aborted.signal)).toThrow();
    expect(request).toHaveBeenCalledTimes(count);
    const pending = new AbortController();
    request.mockImplementation(async () => {
      pending.abort();
      return shiftDtoFixture();
    });
    await expect(
      feature.loadShift.execute(access, identifier(), pending.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
});
