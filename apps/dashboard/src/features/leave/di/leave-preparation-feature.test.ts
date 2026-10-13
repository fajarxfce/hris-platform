import { assert, describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { toLeaveEvidenceOptions } from "../data/mappers/leave-preparation-mapper";
import type { LeaveRequestPreviewDto } from "../data/models/leave-request-preview-dto";
import {
  prepareLeaveSubmission,
  requestedLeaveRange,
  validateLeaveSubmission,
} from "../domain/policies/leave-preparation-policy";
import { createLeaveFeature } from "./leave-feature";
import {
  preparationAccess as access,
  preparationCompany as company,
  preparationId as id,
  preparationOperation as operation,
  preparationDirectoryDto,
  preparationEvidenceDto,
  preparationPreviewDto,
  preparationReview,
  preparationTypesDto,
  preparationSelection as selection,
} from "./leave-preparation-fixture";

const signal = () => new AbortController().signal;
function fixture() {
  const request = vi.fn<HttpClient["request"]>();
  return { request, feature: createLeaveFeature({ request }, { downloadBinary: vi.fn() }) };
}

describe("administrative leave preparation", () => {
  it("requires leave management without using unrelated employee or document grants", async () => {
    const f = fixture();
    for (const permissions of [
      [],
      ["leave.read"],
      ["leave.self.manage"],
      ["people.read", "documents.read"],
    ]) {
      const denied = { ...access, permissions };
      for (const result of [
        await f.feature.loadPreparationEmployees.execute(denied, "", null, signal()),
        await f.feature.loadPreparationEvidence.execute(
          denied,
          selection.employeeId,
          null,
          signal(),
        ),
        await f.feature.loadRequestTypes.execute(denied, "2026-10-05", null, signal()),
        await f.feature.previewRequest.execute(denied, selection, signal()),
      ])
        expect(result).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    }
    expect(f.request).not.toHaveBeenCalled();
  });

  it("uses bounded minimal employee and evidence discovery with encoded search and owned records", async () => {
    const f = fixture();
    f.request.mockResolvedValue(preparationDirectoryDto());
    const employees = await f.feature.loadPreparationEmployees.execute(
      access,
      "  Leave & Co  ",
      "EMP-000",
      signal(),
    );
    expect(employees).toMatchObject({
      ok: true,
      value: {
        companyDate: "2026-10-01",
        employees: { items: [{ companyId: company, id: selection.employeeId, version: 2 }] },
      },
    });
    const url = new URL(f.request.mock.lastCall?.[0].path ?? "", "https://example.test");
    expect(url.pathname).toBe(`/api/v1/companies/${company}/leave/preparation/employees`);
    expect(Object.fromEntries(url.searchParams)).toEqual({
      query: "Leave & Co",
      limit: "20",
      after: "EMP-000",
    });
    f.request.mockResolvedValue(preparationEvidenceDto());
    const evidence = await f.feature.loadPreparationEvidence.execute(
      access,
      selection.employeeId,
      null,
      signal(),
    );
    expect(evidence).toMatchObject({
      ok: true,
      value: {
        items: [{ companyId: company, employeeId: selection.employeeId, revisionNumber: 2 }],
      },
    });
    expect(f.request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${company}/leave/preparation/employees/${selection.employeeId}/evidence?limit=20`,
    );
    if (evidence.ok) expect(Object.isFrozen(evidence.value.items[0])).toBe(true);
  });

  it("reads the policy effective on the first requested day, including a historical applied revision", async () => {
    const f = fixture();
    f.request.mockResolvedValue(preparationTypesDto());
    const result = await f.feature.loadRequestTypes.execute(access, "2026-10-05", null, signal());
    expect(result).toMatchObject({
      ok: true,
      value: {
        items: [
          { companyId: company, asOf: "2026-10-05", id: selection.typeId, allowPartialDays: true },
        ],
      },
    });
    expect(f.request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${company}/leave/types?asOf=2026-10-05&limit=20`,
    });
    const raw = preparationTypesDto();
    raw.items = raw.items.map((item) => ({ ...item, effectiveFrom: "2026-11-01" }));
    f.request.mockResolvedValue(raw);
    expect(
      await f.feature.loadRequestTypes.execute(access, "2026-10-05", null, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("rejects invalid dates, identifiers and page bounds before I/O", async () => {
    const f = fixture();
    for (const input of ["2026-02-30", "1899-12-31", "2201-01-01"])
      expect(await f.feature.loadRequestTypes.execute(access, input, null, signal())).toMatchObject(
        { ok: false },
      );
    expect(
      await f.feature.loadPreparationEmployees.execute(access, "x".repeat(121), null, signal()),
    ).toMatchObject({ ok: false });
    expect(
      await f.feature.loadPreparationEmployees.execute(access, "", "", signal()),
    ).toMatchObject({ ok: false });
    expect(
      await f.feature.loadPreparationEvidence.execute(access, "../other", null, signal()),
    ).toMatchObject({ ok: false });
    expect(
      await f.feature.loadPreparationEvidence.execute(
        access,
        selection.employeeId,
        "not-a-cursor",
        signal(),
      ),
    ).toMatchObject({ ok: false });
    for (const days of [
      [],
      [...selection.days, ...selection.days],
      [{ workDate: "2026-02-30", portion: "FULL" as const }],
    ])
      expect(
        await f.feature.previewRequest.execute(access, { ...selection, days }, signal()),
      ).toMatchObject({ ok: false });
    expect(f.request).not.toHaveBeenCalled();
  });

  it("rejects duplicate identities and pages that cannot advance", async () => {
    const f = fixture();
    const employees = preparationDirectoryDto();
    employees.employees.items.push(...employees.employees.items);
    f.request.mockResolvedValue(employees);
    expect(
      await f.feature.loadPreparationEmployees.execute(access, "", null, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    const evidence = preparationEvidenceDto();
    const document = evidence.items[0];
    assert(document);
    f.request.mockResolvedValue(evidence);
    expect(
      await f.feature.loadPreparationEvidence.execute(
        access,
        selection.employeeId,
        document.documentId,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    const types = preparationTypesDto();
    types.nextCursor = "OTHER";
    f.request.mockResolvedValue(types);
    expect(
      await f.feature.loadRequestTypes.execute(access, "2026-10-05", null, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("detaches reviewed selection, excluded dates and exact half-day balance facts", async () => {
    const f = fixture(),
      raw = preparationPreviewDto();
    f.request.mockResolvedValue(raw);
    const result = await f.feature.previewRequest.execute(access, selection, signal());
    expect(result).toMatchObject({
      ok: true,
      value: {
        preview: {
          companyId: company,
          chargedDays: "0.5",
          excludedDates: ["2026-10-06"],
          balances: [{ remainingDays: "12" }],
        },
      },
    });
    expect(f.request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${company}/leave/requests/preview`,
      method: "POST",
      body: selection,
    });
    if (!result.ok) return;
    const day = raw.days[0],
      balance = raw.balances[0];
    assert(day && balance);
    day.chargedMinutes = 1;
    balance.availableDays = "0";
    expect(result.value.preview.days[0]?.chargedMinutes).toBe(225);
    expect(result.value.preview.balances[0]?.availableDays).toBe("12.5");
    expect(Object.isFrozen(result.value.selection.days[0])).toBe(true);
    expect(Object.isFrozen(result.value.preview.balances)).toBe(true);
  });

  it.each<[string, (raw: LeaveRequestPreviewDto) => void]>([
    [
      "employee",
      (raw) => {
        raw.employeeId = id;
      },
    ],
    [
      "type",
      (raw) => {
        raw.policy.typeId = id;
      },
    ],
    [
      "timezone",
      (raw) => {
        raw.timezone = "Invalid/Timezone";
      },
    ],
    [
      "quantity",
      (raw) => {
        raw.chargedDays = "1";
      },
    ],
    [
      "portion",
      (raw) => {
        raw.days = raw.days.map((day) => ({ ...day, portion: "FULL" }));
      },
    ],
    [
      "excluded date",
      (raw) => {
        raw.excludedDates = ["2026-10-07"];
      },
    ],
    [
      "missing date",
      (raw) => {
        raw.excludedDates = [];
      },
    ],
    [
      "balance",
      (raw) => {
        raw.balances = raw.balances.map((balance) => ({ ...balance, remainingDays: "12.5" }));
      },
    ],
    [
      "year",
      (raw) => {
        raw.balances = raw.balances.map((balance) => ({ ...balance, year: 2025 }));
      },
    ],
    [
      "revision",
      (raw) => {
        raw.revision = "invalid";
      },
    ],
  ])("rejects inconsistent preview %s", async (_name, mutate) => {
    const f = fixture(),
      raw = preparationPreviewDto();
    mutate(raw);
    f.request.mockResolvedValue(raw);
    expect(await f.feature.previewRequest.execute(access, selection, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });

  it("preserves a localizable rejection and propagates cancellation after acquisition", async () => {
    const f = fixture();
    f.request.mockRejectedValue(
      new HttpResponseError(403, { code: "mfa_required", detail: "PRIVATE" }),
    );
    const result = await f.feature.previewRequest.execute(access, selection, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "mfa_required" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    const stopped = new AbortController();
    f.request.mockImplementation(async () => {
      stopped.abort();
      return preparationPreviewDto();
    });
    await expect(
      f.feature.previewRequest.execute(access, selection, stopped.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    const count = f.request.mock.calls.length;
    expect(() =>
      f.feature.loadPreparationEvidence.execute(access, selection.employeeId, null, stopped.signal),
    ).toThrow();
    expect(f.request).toHaveBeenCalledTimes(count);
  });
});

describe("reviewed leave submission", () => {
  it("retains company, review and selected evidence while replaying only the immutable request body", async () => {
    const f = fixture();
    const evidence = toLeaveEvidenceOptions(
      preparationEvidenceDto(),
      company,
      selection.employeeId,
      null,
    ).items;
    const prepared = prepareLeaveSubmission(
      access,
      operation,
      id,
      preparationReview(),
      "  Personal leave  ",
      evidence,
    );
    expect(prepared.ok).toBe(true);
    if (!prepared.ok) return;
    f.request.mockResolvedValue({ id, version: 0 });
    for (let attempt = 0; attempt < 2; attempt++)
      expect(
        await f.feature.submitRequest.execute(access, operation, prepared.value, signal()),
      ).toEqual({ ok: true, value: { id, version: 0 } });
    expect(f.request).toHaveBeenCalledTimes(2);
    expect(f.request.mock.calls[0]?.[0]).toEqual(f.request.mock.calls[1]?.[0]);
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${company}/leave/requests`,
      method: "POST",
      operationId: operation,
      body: {
        ...selection,
        id,
        reason: "Personal leave",
        attachmentRevisionIds: ["d2000000-0000-4000-8000-000000000001"],
        previewRevision: "a".repeat(64),
      },
    });
    expect(Object.isFrozen(prepared.value)).toBe(true);
    expect(Object.isFrozen(prepared.value.attachmentRevisionIds)).toBe(true);
    const other = { ...access, companyId: "10000000-0000-4000-8000-000000000002" as CompanyId };
    expect(
      await f.feature.submitRequest.execute(other, operation, prepared.value, signal()),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(f.request).toHaveBeenCalledTimes(2);
  });

  it("requires selected employee evidence, policy-required attachments and bounded input", () => {
    const review = preparationReview();
    const required = {
      ...review,
      preview: {
        ...review.preview,
        policy: { ...review.preview.policy, attachmentRequired: true },
      },
    };
    expect(
      prepareLeaveSubmission(access, operation, id, required, "Personal leave", []),
    ).toMatchObject({ ok: false, failure: { code: "leave_attachment_required" } });
    const evidence = toLeaveEvidenceOptions(preparationEvidenceDto(), company, id, null).items;
    expect(
      prepareLeaveSubmission(access, operation, id, review, "Personal leave", evidence),
    ).toMatchObject({ ok: false, failure: { code: "invalid_leave_attachments" } });
    expect(prepareLeaveSubmission(access, operation, id, review, " ", [])).toMatchObject({
      ok: false,
    });
    expect(
      prepareLeaveSubmission(access, operation, id, review, "x".repeat(1001), []),
    ).toMatchObject({ ok: false });
    expect(requestedLeaveRange("2024-02-28", "2024-03-01", "FULL")).toMatchObject({
      ok: true,
      value: [{ workDate: "2024-02-28" }, { workDate: "2024-02-29" }, { workDate: "2024-03-01" }],
    });
    expect(requestedLeaveRange("2026-01-01", "2027-01-02", "FULL")).toMatchObject({ ok: false });
  });

  it("rejects wrong receipts, unsafe input and late cancellation without manufacturing a new operation", async () => {
    const f = fixture(),
      prepared = prepareLeaveSubmission(
        access,
        operation,
        id,
        preparationReview(),
        "Personal leave",
        [],
      );
    if (!prepared.ok) throw new Error("Invalid test setup");
    for (const receipt of [
      { id: selection.employeeId, version: 0 },
      { id, version: 1 },
    ]) {
      f.request.mockResolvedValue(receipt);
      expect(
        await f.feature.submitRequest.execute(access, operation, prepared.value, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    expect(
      validateLeaveSubmission(operation, { ...prepared.value, previewRevision: "invalid" }),
    ).toMatchObject({ ok: false });
    const stopped = new AbortController();
    f.request.mockImplementation(async () => {
      stopped.abort();
      return { id, version: 0 };
    });
    await expect(
      f.feature.submitRequest.execute(access, operation, prepared.value, stopped.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
});
