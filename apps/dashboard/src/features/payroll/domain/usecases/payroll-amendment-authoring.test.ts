import { assert, describe, expect, it, vi } from "vitest";
import {
  amendmentPreparation,
  amendmentPreview,
} from "../../../../../tests/fixtures/payroll-amendment-authoring";
import {
  amendmentDetails,
  amendmentId,
  amendmentRevision,
} from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import { payslipId } from "../../../../../tests/fixtures/payroll-payslips";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollAmendmentPreview } from "../../data/mappers/payroll-amendment-calculation-mapper";
import { toPayrollAmendmentDetails } from "../../data/mappers/payroll-amendment-mapper";
import { toPayrollAmendmentPreparation } from "../../data/mappers/payroll-amendment-preparation-mapper";
import type { PayrollAmendmentSourceSearch } from "../entities/payroll-amendment-preparation";
import type { PayrollAmendmentRepository } from "../repositories/payroll-amendment-repository";
import type { PayrollAmendmentSourceRepository } from "../repositories/payroll-amendment-source-repository";
import { ListPayrollAmendmentSources } from "./list-payroll-amendment-sources";
import { LoadPayrollAmendmentEditor } from "./load-payroll-amendment-editor";
import { PreviewPayrollAmendment } from "./preview-payroll-amendment";
import { SavePayrollAmendment } from "./save-payroll-amendment";

const access: CompanyAccess = {
    companyId: calculationCompany as CompanyId,
    permissions: ["payroll.amend"],
  },
  signal = new AbortController().signal,
  operation = amendmentId(99) as OperationId;
const search: PayrollAmendmentSourceSearch = {
  year: 2026,
  query: "",
  employeeId: null,
  after: null,
};
function fixture() {
  const preparation = toPayrollAmendmentPreparation(
    amendmentPreparation(),
    access.companyId,
    payslipId(),
  );
  const sources = {
      list: vi
        .fn<PayrollAmendmentSourceRepository["list"]>()
        .mockResolvedValue(success({ items: [], nextCursor: null })),
      prepare: vi
        .fn<PayrollAmendmentSourceRepository["prepare"]>()
        .mockResolvedValue(success(preparation)),
      preview: vi
        .fn<PayrollAmendmentSourceRepository["preview"]>()
        .mockResolvedValue(success(toPayrollAmendmentPreview(amendmentPreview()))),
    },
    amendments = {
      list: vi.fn<PayrollAmendmentRepository["list"]>(),
      calculate: vi.fn<PayrollAmendmentRepository["calculate"]>(),
      cancel: vi.fn<PayrollAmendmentRepository["cancel"]>(),
      get: vi
        .fn<PayrollAmendmentRepository["get"]>()
        .mockResolvedValue(
          success(toPayrollAmendmentDetails(amendmentDetails(0), access.companyId, amendmentId())),
        ),
      revision: vi.fn<PayrollAmendmentRepository["revision"]>(),
      history: vi.fn<PayrollAmendmentRepository["history"]>(),
      calculation: vi.fn<PayrollAmendmentRepository["calculation"]>(),
      save: vi
        .fn<PayrollAmendmentRepository["save"]>()
        .mockResolvedValue(success({ id: amendmentId(), version: 0 })),
    };
  return { preparation, sources, amendments };
}
describe("amendment authoring policy", () => {
  it.each([[], ["payroll.read"], ["payroll.amend.review"], ["payroll.amend.publish"]])(
    "does not grant authoring to %j",
    async (...permissions) => {
      const f = fixture(),
        denied = { ...access, permissions };
      expect(
        await new ListPayrollAmendmentSources(f.sources).execute(denied, search, signal),
      ).toEqual(failed("access_denied"));
      expect(
        await new LoadPayrollAmendmentEditor(f.amendments, f.sources).execute(
          denied,
          { kind: "create", assessmentId: payslipId() },
          signal,
        ),
      ).toEqual(failed("access_denied"));
      expect(
        await new PreviewPayrollAmendment(f.sources).execute(
          denied,
          f.preparation,
          amendmentRevision(0).terms,
          signal,
        ),
      ).toEqual(failed("access_denied"));
      expect(
        await new SavePayrollAmendment(f.amendments).execute(
          denied,
          amendmentId(),
          operation,
          {
            ...amendmentRevision(0).terms,
            firstAssessmentId: payslipId(),
            expectedVersion: null,
            reason: "Correction",
          },
          signal,
        ),
      ).toEqual(failed("access_denied"));
      expect(f.sources.prepare).not.toHaveBeenCalled();
      expect(f.sources.list).not.toHaveBeenCalled();
      expect(f.sources.preview).not.toHaveBeenCalled();
      expect(f.amendments.save).not.toHaveBeenCalled();
    },
  );
  it.each([
    { year: 2023 },
    { year: 2101 },
    { after: "bad" },
    { employeeId: "bad" },
    { query: "x".repeat(121) },
    { query: "a\nb" },
  ])("bounds source discovery: %j", async (invalid) => {
    const f = fixture();
    expect(
      await new ListPayrollAmendmentSources(f.sources).execute(
        access,
        { ...search, ...invalid },
        signal,
      ),
    ).toEqual(failed("invalid_page"));
    expect(f.sources.list).not.toHaveBeenCalled();
  });
  it("acquires complete edit context without replacing stale draft source revisions", async () => {
    const f = fixture();
    const updated = toPayrollAmendmentPreparation(
      amendmentPreparation(true, 1),
      access.companyId,
      payslipId(),
    );
    f.sources.prepare.mockResolvedValue(success(updated));
    const result = await new LoadPayrollAmendmentEditor(f.amendments, f.sources).execute(
      access,
      { kind: "edit", amendmentId: amendmentId() },
      signal,
    );
    assert(result.ok);
    expect(result.value.sourcesChanged).toBe(true);
    expect(result.value.current?.terms.sourceVersions[0]?.revision).toBe(0);
    expect(result.value.preparation.sourceVersions[0]?.revision).toBe(1);
    expect(f.sources.prepare).toHaveBeenCalledExactlyOnceWith(
      access.companyId,
      payslipId(),
      signal,
    );
  });
  it("honors unavailable edit actions and dependent denial", async () => {
    const f = fixture(),
      read = toPayrollAmendmentDetails(amendmentDetails(0), access.companyId, amendmentId());
    f.amendments.get.mockResolvedValue(success({ ...read, availableActions: [] }));
    expect(
      await new LoadPayrollAmendmentEditor(f.amendments, f.sources).execute(
        access,
        { kind: "edit", amendmentId: amendmentId() },
        signal,
      ),
    ).toEqual(failed("payroll_amendment_not_editable"));
    expect(f.sources.prepare).not.toHaveBeenCalled();
    f.amendments.get.mockResolvedValue(success(read));
    f.sources.prepare.mockResolvedValue(failed("access_denied"));
    expect(
      await new LoadPayrollAmendmentEditor(f.amendments, f.sources).execute(
        access,
        { kind: "edit", amendmentId: amendmentId() },
        signal,
      ),
    ).toEqual(failed("access_denied"));
  });
  it("cancels between dependent reads", async () => {
    const f = fixture(),
      abort = new AbortController();
    f.amendments.get.mockImplementationOnce(async () => {
      abort.abort();
      return success(
        toPayrollAmendmentDetails(amendmentDetails(0), access.companyId, amendmentId()),
      );
    });
    await expect(
      new LoadPayrollAmendmentEditor(f.amendments, f.sources).execute(
        access,
        { kind: "edit", amendmentId: amendmentId() },
        abort.signal,
      ),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(f.sources.prepare).not.toHaveBeenCalled();
  });
  it("normalizes an immutable proposal before preview and preserves its original caller", async () => {
    const f = fixture(),
      terms = amendmentRevision(0).terms;
    assert(terms.corrections[0]?.compensation);
    terms.corrections[0].compensation.basicSalary = " 9999999.25 ";
    expect(
      (await new PreviewPayrollAmendment(f.sources).execute(access, f.preparation, terms, signal))
        .ok,
    ).toBe(true);
    const sent = f.sources.preview.mock.calls[0]?.[2];
    expect(sent?.corrections[0]?.compensation?.basicSalary).toBe("9999999.25");
    expect(Object.isFrozen(sent?.corrections[0]?.compensation)).toBe(true);
    expect(terms.corrections[0].compensation.basicSalary).toBe(" 9999999.25 ");
  });
  it.each([
    "empty",
    "missing-first",
    "duplicate",
    "empty-change",
    "sources",
    "tax",
    "amount",
    "input",
    "scope",
  ])("rejects %s before preview I/O", async (kind) => {
    const f = fixture(),
      terms = amendmentRevision(0).terms;
    assert(terms.corrections[0]?.compensation);
    assert(terms.corrections[0]?.input);
    assert(terms.sourceVersions[0]);
    const correction = terms.corrections[0],
      compensation = correction.compensation,
      input = correction.input;
    assert(compensation);
    assert(input);
    let preparation = f.preparation;
    if (kind === "empty") terms.corrections = [];
    if (kind === "missing-first") correction.assessmentId = payslipId(2);
    if (kind === "duplicate") terms.corrections.push({ ...correction });
    if (kind === "empty-change") {
      correction.compensation = null;
      correction.input = null;
    }
    if (kind === "sources") terms.sourceVersions[0].revision = 1;
    if (kind === "tax") compensation.tax.ptkp = "K3";
    if (kind === "amount") compensation.basicSalary = "NaN";
    if (kind === "input") input.nonCashTaxable = "-1";
    if (kind === "scope") preparation = { ...preparation, companyId: amendmentId(2) as CompanyId };
    const result = await new PreviewPayrollAmendment(f.sources).execute(
      access,
      preparation,
      terms,
      signal,
    );
    expect(result.ok).toBe(false);
    expect(f.sources.preview).not.toHaveBeenCalled();
    if (kind === "amount")
      expect(result).toMatchObject({
        failure: {
          fields: { "corrections[0].compensation.basicSalary": "out_of_range" },
          parameters: { taxMonth: "2026-09" },
        },
      });
  });
  it("rejects corrections before the chosen first month", async () => {
    const f = fixture(),
      preparation = toPayrollAmendmentPreparation(
        amendmentPreparation(true),
        access.companyId,
        payslipId(),
      ),
      terms = amendmentRevision(0).terms;
    terms.sourceVersions = preparation.sourceVersions.map((row) => ({ ...row }));
    assert(terms.corrections[0]);
    terms.corrections.push({ ...terms.corrections[0], assessmentId: payslipId(2) });
    expect(
      await new PreviewPayrollAmendment(f.sources).execute(access, preparation, terms, signal),
    ).toEqual(failed("invalid_payroll_amendment_corrections"));
    expect(f.sources.preview).not.toHaveBeenCalled();
  });
  it.each(["", "\n", "x".repeat(1001)])(
    "validates the save reason before delivery",
    async (reason) => {
      const f = fixture();
      expect(
        (
          await new SavePayrollAmendment(f.amendments).execute(
            access,
            amendmentId(),
            operation,
            {
              ...amendmentRevision(0).terms,
              firstAssessmentId: payslipId(),
              expectedVersion: null,
              reason,
            },
            signal,
          )
        ).ok,
      ).toBe(false);
      expect(f.amendments.save).not.toHaveBeenCalled();
    },
  );
  it("replays the original save without reacquiring or substituting its source year", async () => {
    const f = fixture(),
      useCase = new SavePayrollAmendment(f.amendments),
      input = {
        ...amendmentRevision(0).terms,
        firstAssessmentId: payslipId(),
        expectedVersion: null,
        reason: "Verified correction",
      };
    f.amendments.save.mockResolvedValueOnce(failed("connection_unavailable"));
    await useCase.execute(access, amendmentId(), operation, input, signal);
    expect((await useCase.execute(access, amendmentId(), operation, input, signal)).ok).toBe(true);
    expect(f.amendments.save.mock.calls[0]?.slice(0, 4)).toEqual(
      f.amendments.save.mock.calls[1]?.slice(0, 4),
    );
    expect(f.sources.prepare).not.toHaveBeenCalled();
    expect(f.amendments.get).not.toHaveBeenCalled();
  });
});
