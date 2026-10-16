import { act, renderHook, waitFor } from "@testing-library/react";
import { assert, describe, expect, it, vi } from "vitest";
import { amendmentPreparation } from "../../../../../tests/fixtures/payroll-amendment-authoring";
import { amendmentRevision } from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import { payslipId } from "../../../../../tests/fixtures/payroll-payslips";
import { workAdjustment, workAdjustmentId } from "../../../../../tests/fixtures/payroll-work";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { toPayrollAmendmentPreparation } from "../../data/mappers/payroll-amendment-preparation-mapper";
import type { PayrollAmendmentCorrection } from "../../domain/entities/payroll-amendment";
import { payrollAmendmentAttendanceChoiceView } from "../models/payroll-amendment-attendance-view";
import { usePayrollAmendmentCorrectionForm } from "./use-payroll-amendment-correction-form";

function fixture(correction: PayrollAmendmentCorrection | null = null) {
  const raw = amendmentPreparation();
  assert(raw.assessments[0]);
  raw.assessments[0].availableWorkAdjustment = workAdjustment(2);
  raw.assessments[0].workAdjustmentId = workAdjustmentId();
  const source = toPayrollAmendmentPreparation(raw, calculationCompany as CompanyId, payslipId())
    .assessments[0];
  assert(source);
  const apply = vi.fn(),
    close = vi.fn(),
    dirty = vi.fn();
  const hook = renderHook(() =>
    usePayrollAmendmentCorrectionForm(source, correction, apply, close, dirty),
  );
  return { ...hook, source, apply, close, dirty };
}
describe("amendment month attendance choice", () => {
  it("starts with published attendance and permits an explicit attendance-only change", async () => {
    const f = fixture();
    expect(f.result.current.attendance.value).toBe("");
    await act(async () => {
      f.result.current.changeCompensation(false);
      f.result.current.attendance.onChange(workAdjustmentId(2));
    });
    await act(async () => f.result.current.submit());
    expect(f.apply).toHaveBeenCalledWith({
      assessmentId: payslipId(),
      compensation: null,
      input: null,
      workAdjustmentId: workAdjustmentId(2),
    });
    f.unmount();
    expect(f.dirty).toHaveBeenLastCalledWith(false);
  });
  it("keeps a stale saved option visible and requires a deliberate replacement", async () => {
    const correction = amendmentRevision(0).terms.corrections[0];
    assert(correction);
    const f = fixture({ ...correction, workAdjustmentId: workAdjustmentId() });
    const view = payrollAmendmentAttendanceChoiceView(
      f.source,
      f.result.current.attendance.value,
      "en",
      "Asia/Jakarta",
    );
    expect(view.stale).toBe(true);
    expect(view.options).toContainEqual({
      value: workAdjustmentId(),
      label: "Previously selected attendance",
    });
    await act(async () => f.result.current.submit());
    expect(f.apply).not.toHaveBeenCalled();
    expect(f.result.current.failure?.code).toBe("stale_workforce_version");
    await act(async () => {
      f.result.current.attendance.onChange("");
    });
    await act(async () => f.result.current.submit());
    expect(f.apply).toHaveBeenCalledWith({ ...correction, workAdjustmentId: null });
    f.unmount();
  });
  it("protects an unsaved attendance choice and rejects an empty correction", async () => {
    const f = fixture();
    await act(async () => {
      f.result.current.changeCompensation(false);
      f.result.current.attendance.onChange(workAdjustmentId(2));
    });
    await waitFor(() => expect(f.dirty).toHaveBeenLastCalledWith(true));
    await act(async () => {
      f.result.current.close();
    });
    expect(f.result.current.discardRequested).toBe(true);
    expect(f.close).not.toHaveBeenCalled();
    await act(async () => {
      f.result.current.keepEditing();
      f.result.current.attendance.onChange("");
    });
    await act(async () => f.result.current.submit());
    expect(f.apply).not.toHaveBeenCalled();
    expect(f.result.current.failure?.code).toBe("invalid_payroll_amendment_corrections");
    f.unmount();
  });
});
