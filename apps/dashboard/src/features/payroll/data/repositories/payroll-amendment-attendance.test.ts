import { assert, describe, expect, it, vi } from "vitest";
import {
  amendmentPreparation,
  amendmentPreview,
} from "../../../../../tests/fixtures/payroll-amendment-authoring";
import { amendmentRevision } from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import { payslipId } from "../../../../../tests/fixtures/payroll-payslips";
import {
  workAdjustment,
  workAdjustmentId,
  workDays,
} from "../../../../../tests/fixtures/payroll-work";
import type { HttpClient } from "../../../../core/data/http/http-client";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { HttpPayrollAmendmentSourceDataSource } from "../datasources/http-payroll-amendment-source-data-source";
import { RemotePayrollAmendmentSourceRepository } from "./remote-payroll-amendment-source-repository";

const company = calculationCompany as CompanyId,
  signal = new AbortController().signal;
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return {
    http,
    source: new RemotePayrollAmendmentSourceRepository(
      new HttpPayrollAmendmentSourceDataSource(http),
    ),
  };
}
function preparation() {
  const raw = amendmentPreparation(),
    first = raw.assessments[0];
  assert(first);
  first.workAdjustmentId = workAdjustmentId();
  first.availableWorkAdjustment = workAdjustment(2);
  return { raw, first };
}
describe("amendment attendance evidence", () => {
  it("keeps published and available references distinct and detaches the complete calendar", async () => {
    const { raw, first } = preparation(),
      result = await fixture(raw).source.prepare(company, payslipId(), signal);
    assert(result.ok && first.workDays?.[0] && first.availableWorkAdjustment);
    first.workDays[0].overtime.push({ requestId: workAdjustmentId(55), revision: 0, minutes: 60 });
    first.availableWorkAdjustment.reason = "Changed caller data";
    const basis = result.value.assessments[0];
    expect(basis?.workAdjustmentId).toBe(workAdjustmentId());
    expect(basis?.availableWorkAdjustment?.id).toBe(workAdjustmentId(2));
    expect(basis?.availableWorkAdjustment?.reason).toBe("Verified attendance correction 2");
    expect(basis?.workDays?.[0]?.overtime).toEqual([]);
    expect(Object.isFrozen(basis?.workDays?.[0]?.overtime)).toBe(true);
  });
  it.each([
    "missing day",
    "reordered day",
    "wrong month",
    "duplicate overtime",
    "metadata month",
    "missing facts",
  ])("rejects %s within the data boundary", async (kind) => {
    const { raw, first } = preparation();
    assert(first.workDays?.[0] && first.workDays[1] && first.availableWorkAdjustment);
    if (kind === "missing day") first.workDays.pop();
    if (kind === "reordered day") first.workDays.reverse();
    if (kind === "wrong month") first.workDays[0].date = "2026-08-01";
    if (kind === "duplicate overtime") {
      const overtime = { requestId: workAdjustmentId(55), revision: 1, minutes: 60 };
      first.workDays[0].overtime.push(overtime);
      first.workDays[1].overtime.push({ ...overtime });
    }
    if (kind === "metadata month") first.availableWorkAdjustment.workDate = "2026-08-01";
    if (kind === "missing facts") first.workDays = null;
    expect(await fixture(raw).source.prepare(company, payslipId(), signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("accepts older responses without inventing work evidence and omits unchanged attendance in requests", async () => {
    const raw = amendmentPreparation(),
      legacy = {
        ...raw,
        assessments: raw.assessments.map(
          ({
            workDays: _days,
            workAdjustmentId: _selected,
            availableWorkAdjustment: _available,
            ...row
          }) => row,
        ),
      };
    const { source, http } = fixture(legacy),
      result = await source.prepare(company, payslipId(), signal);
    assert(result.ok);
    expect(result.value.assessments[0]).toMatchObject({
      workDays: null,
      workAdjustmentId: null,
      availableWorkAdjustment: null,
    });
    http.request.mockResolvedValue(amendmentPreview());
    expect(
      (await source.preview(company, payslipId(), amendmentRevision(0).terms, signal)).ok,
    ).toBe(true);
    const body = http.request.mock.calls[1]?.[0].body as { corrections: Record<string, unknown>[] };
    expect(body.corrections[0]).not.toHaveProperty("workAdjustmentId");
  });
  it("transmits the explicit attendance-only selection and rejects a substituted calculation source", async () => {
    const terms = {
      ...amendmentRevision(0).terms,
      corrections: [
        {
          assessmentId: payslipId(),
          compensation: null,
          input: null,
          workAdjustmentId: workAdjustmentId(2),
        },
      ],
    };
    const raw = amendmentPreview(amendmentPreparation(), terms),
      { source, http } = fixture(raw);
    const result = await source.preview(company, payslipId(), terms, signal);
    assert(result.ok && raw.calculations[0]);
    expect(http.request.mock.calls[0]?.[0].body).toEqual(terms);
    expect(result.value.calculations[0]?.workDays?.[0]?.attendance).toBe("WORKED");
    raw.calculations[0].workAdjustmentId = workAdjustmentId(3);
    expect(await source.preview(company, payslipId(), terms, signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
    raw.calculations[0].workAdjustmentId = null;
    expect(await source.preview(company, payslipId(), terms, signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("preserves a published adjustment when a proposal changes only remuneration", async () => {
    const { raw } = preparation(),
      terms = amendmentRevision(0).terms,
      response = amendmentPreview(raw, terms);
    assert(response.calculations[0]);
    response.calculations[0].workDays = workDays();
    const result = await fixture(response).source.preview(company, payslipId(), terms, signal);
    assert(result.ok);
    expect(result.value.calculations[0]?.workAdjustmentId).toBe(workAdjustmentId());
  });
});
