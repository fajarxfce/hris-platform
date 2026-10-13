import { assert, describe, expect, it, vi } from "vitest";
import { expenseIdentifier } from "../../../../tests/fixtures/expense-claims";
import {
  expenseDraftCategories,
  expenseDraftCostCenters,
  expenseDraftEmployees,
  expenseDraftInput,
  expenseDraftReceipts,
  expenseDraftRecord,
  expenseDraftReferences,
} from "../../../../tests/fixtures/expense-drafts";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { Result } from "../../../core/domain/result";
import { toExpenseDraftReferences } from "../data/mappers/expense-drafting-mapper";
import { expenseDraftReferencesDto } from "../data/models/expense-drafting-dto";
import {
  expenseDraftReferenceQuery,
  expenseDraftSaveWasRejected,
} from "../domain/policies/expense-draft-policy";
import { createExpenseFeature } from "./expense-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId,
  access = { companyId: company, permissions: ["expenses.manage"] };
const operation = expenseIdentifier(999) as OperationId,
  id = expenseIdentifier(998),
  employment = expenseIdentifier(100);
const signal = () => new AbortController().signal;
function fixture() {
  const request = vi.fn();
  return {
    request,
    feature: createExpenseFeature({ request }, { downloadBinary: vi.fn(), downloadText: vi.fn() }),
  };
}
describe("expense draft boundaries", () => {
  it("acquires only bounded drafting metadata without broader People or Document authority", async () => {
    const f = fixture();
    f.request
      .mockResolvedValueOnce(expenseDraftEmployees(0, 20))
      .mockResolvedValueOnce(expenseDraftReceipts())
      .mockResolvedValueOnce(expenseDraftCostCenters());
    const employees = await f.feature.loadDraftEmployees.execute(access, "  EMP  ", null, signal());
    assert(employees.ok);
    expect(employees.value.companyDate).toBe("2026-10-13");
    expect(employees.value.employees.items[0]).toMatchObject({ companyId: company });
    expect(Object.isFrozen(employees.value.employees.items)).toBe(true);
    const receipts = await f.feature.loadDraftReceipts.execute(
      access,
      employment.toUpperCase(),
      null,
      signal(),
    );
    assert(receipts.ok);
    expect(receipts.value.items[0]).toMatchObject({ companyId: company, employmentId: employment });
    expect((await f.feature.loadCostCenters.execute(access, " OPS ", null, signal())).ok).toBe(
      true,
    );
    expect(f.request.mock.calls.map((call) => call[0].path)).toEqual([
      `/api/v1/companies/${company}/expenses/drafting/employees?query=EMP&limit=20`,
      `/api/v1/companies/${company}/expenses/drafting/employees/${employment}/receipts?limit=20`,
      `/api/v1/companies/${company}/expenses/cost-centers?query=OPS&limit=20`,
    ]);
  });
  it("uses effective category revisions instead of requiring the latest policy head", async () => {
    const f = fixture();
    f.request.mockResolvedValue(expenseDraftCategories());
    const result = await f.feature.loadCategories.execute(access, "2026-10-12", null, signal());
    assert(result.ok);
    expect(result.value.items[0]).toMatchObject({
      version: 3,
      appliedRevision: 1,
      companyId: company,
    });
    expect(f.request.mock.calls[0]?.[0].path).toContain("categories?asOf=2026-10-12&limit=20");
  });
  it("rejects invalid authority, employee IDs, date ranges and cursors before I/O", async () => {
    const f = fixture();
    expect(
      (
        await f.feature.loadDraftEmployees.execute(
          { ...access, permissions: ["expenses.self.manage"] },
          "",
          null,
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(
      (await f.feature.loadDraftEmployees.execute(access, "x".repeat(121), null, signal())).ok,
    ).toBe(false);
    expect((await f.feature.loadDraftEmployees.execute(access, "", " ", signal())).ok).toBe(false);
    expect((await f.feature.loadDraftReceipts.execute(access, "../", null, signal())).ok).toBe(
      false,
    );
    expect(
      (await f.feature.loadDraftReceipts.execute(access, employment, "../", signal())).ok,
    ).toBe(false);
    expect((await f.feature.loadCostCenters.execute(access, "", "OPS", signal())).ok).toBe(false);
    expect((await f.feature.loadCategories.execute(access, "2026-02-30", null, signal())).ok).toBe(
      false,
    );
    expect((await f.feature.loadCategories.execute(access, "2026-10-13", "../", signal())).ok).toBe(
      false,
    );
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each([
    "employee-duplicate",
    "employee-order",
    "employee-limit",
    "timezone",
    "date",
    "employee-cursor",
    "receipt-order",
    "receipt-duplicate",
    "receipt-limit",
    "receipt-cursor",
    "cost-center-order",
    "category-future",
    "category-archived",
    "category-revision",
  ])("rejects an incoherent %s response", async (issue) => {
    const f = fixture();
    let result: Result<unknown>;
    if (issue.startsWith("employee") || ["timezone", "date"].includes(issue)) {
      const raw = expenseDraftEmployees(0, issue === "employee-limit" ? 21 : 2),
        first = raw.employees.items[0],
        second = raw.employees.items[1];
      assert(first && second);
      if (issue === "employee-duplicate") second.id = first.id;
      if (issue === "employee-order") raw.employees.items.reverse();
      if (issue === "timezone") raw.timezone = "Unknown/Zone";
      if (issue === "date") raw.companyDate = "2026-02-30";
      if (issue === "employee-cursor") raw.employees.nextCursor = first.employeeNumber;
      f.request.mockResolvedValue(raw);
      result = await f.feature.loadDraftEmployees.execute(access, "", null, signal());
    } else if (issue.startsWith("receipt")) {
      const raw = expenseDraftReceipts(0, issue === "receipt-limit" ? 21 : 2),
        first = raw.items[0],
        second = raw.items[1];
      assert(first && second);
      if (issue === "receipt-order") raw.items.reverse();
      if (issue === "receipt-duplicate") second.revisionId = first.revisionId;
      if (issue === "receipt-cursor") raw.nextCursor = first.documentId;
      f.request.mockResolvedValue(raw);
      result = await f.feature.loadDraftReceipts.execute(access, employment, null, signal());
    } else if (issue === "cost-center-order") {
      f.request.mockResolvedValue(expenseDraftCostCenters());
      result = await f.feature.loadCostCenters.execute(access, "", "COST_CENTER:OPS", signal());
    } else {
      const raw = expenseDraftCategories(),
        first = raw.items[0];
      assert(first);
      if (issue === "category-future") first.effectiveFrom = "2027-01-01";
      if (issue === "category-archived") first.active = false;
      if (issue === "category-revision") first.appliedRevision = 4;
      f.request.mockResolvedValue(raw);
      result = await f.feature.loadCategories.execute(access, "2026-10-13", null, signal());
    }
    expect(result).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("reviews a returned claim and labels without replacing historical IDs or reserving receipts", async () => {
    const f = fixture(),
      raw = expenseDraftRecord(0, true),
      labels = expenseDraftReferences();
    labels.categories[0] = {
      id: expenseIdentifier(400),
      code: "TRAVEL",
      name: "Archived travel",
      active: false,
    };
    labels.receipts[0] = {
      ...(labels.receipts[0] as NonNullable<(typeof labels.receipts)[0]>),
      status: "UPLOADING",
    };
    f.request.mockResolvedValueOnce(raw).mockResolvedValueOnce(labels);
    const result = await f.feature.reviewDraftEdit.execute(access, raw.claim.id, signal());
    assert(result.ok);
    expect(result.value.claim.status).toBe("RETURNED");
    expect(result.value.references.categories[0]?.active).toBe(false);
    expect(result.value.references.receipts[0]?.status).toBe("UPLOADING");
    expect(f.request.mock.calls[1]?.[0].path).toContain(
      `/drafting/employees/${employment}/references?categoryIds=`,
    );
    expect(f.request.mock.calls).toHaveLength(2);
  });
  it("keeps unavailable reference identities while rejecting missing, extra or foreign metadata", () => {
    const query = expenseDraftReferenceQuery(expenseDraftInput().lines),
      raw = expenseDraftReferencesDto.parse(expenseDraftReferences());
    raw.categories = [{ id: expenseIdentifier(400), code: null, name: null, active: null }];
    const result = toExpenseDraftReferences(raw, company, employment, query);
    expect(result.categories).toEqual([
      { id: expenseIdentifier(400), code: null, name: null, active: null },
    ]);
    expect(() =>
      toExpenseDraftReferences({ ...raw, categories: [] }, company, employment, query),
    ).toThrow();
    expect(() =>
      toExpenseDraftReferences(
        { ...raw, employmentId: expenseIdentifier(888) },
        company,
        employment,
        query,
      ),
    ).toThrow();
    expect(() =>
      toExpenseDraftReferences(
        { ...raw, receipts: [...raw.receipts, ...raw.receipts] },
        company,
        employment,
        query,
      ),
    ).toThrow();
    expect(() =>
      toExpenseDraftReferences(
        {
          ...raw,
          categories: [
            { id: expenseIdentifier(400), code: null, name: "Partial private label", active: true },
          ],
        },
        company,
        employment,
        query,
      ),
    ).toThrow();
  });
  it("does not acquire reference metadata for a submitted or exhausted claim", async () => {
    const f = fixture(),
      raw = expenseDraftRecord();
    raw.claim.status = "PENDING";
    const first = raw.history.items[0];
    assert(first);
    first.status = "PENDING";
    f.request.mockResolvedValue(raw);
    expect(await f.feature.reviewDraftEdit.execute(access, raw.claim.id, signal())).toMatchObject({
      ok: false,
      failure: { code: "expense_not_editable" },
    });
    expect(f.request).toHaveBeenCalledTimes(1);
  });
  it("saves exact decimal amounts and immutable line IDs; an empty draft is valid", async () => {
    const f = fixture(),
      input = {
        ...expenseDraftInput(),
        companyId: company,
        id,
        title: "  Travel to client office  ",
      };
    f.request.mockResolvedValue({ id, version: 0 });
    expect(await f.feature.saveDraft.execute(access, operation, input, signal())).toEqual({
      ok: true,
      value: { id, version: 0 },
    });
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${company}/expenses/claims/${id}/draft`,
      method: "PUT",
      operationId: operation,
      body: expenseDraftInput(),
    });
    expect(
      await f.feature.saveDraft.execute(access, operation, { ...input, lines: [] }, signal()),
    ).toMatchObject({ ok: true });
  });
  it.each([
    "duplicate",
    "precision",
    "zero",
    "total",
    "count",
    "receipts",
    "scope",
    "note",
    "version",
  ])("rejects %s before saving", async (issue) => {
    const f = fixture(),
      input = { ...expenseDraftInput(), companyId: company, id },
      first = input.lines[0];
    assert(first);
    if (issue === "duplicate") input.lines = [first, first];
    if (issue === "precision") input.lines = [{ ...first, amount: "10.001" }];
    if (issue === "zero") input.lines = [{ ...first, amount: "0.00" }];
    if (issue === "total")
      input.lines = [
        { ...first, amount: "999999999999.99" },
        { ...first, id: expenseIdentifier(555), amount: "0.01" },
      ];
    if (issue === "count")
      input.lines = Array.from({ length: 21 }, (_, i) => ({ ...first, id: expenseIdentifier(i) }));
    if (issue === "receipts")
      input.lines = [
        {
          ...first,
          receiptRevisionIds: [expenseIdentifier(1), expenseIdentifier(1).toUpperCase()],
        },
      ];
    if (issue === "scope") input.companyId = expenseIdentifier(666) as CompanyId;
    if (issue === "note") input.reason = " ";
    if (issue === "version") input.expectedVersion = -1;
    expect((await f.feature.saveDraft.execute(access, operation, input, signal())).ok).toBe(false);
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each([
    { id: expenseIdentifier(10), version: 0 },
    { id, version: 4 },
  ])("rejects an unrelated save receipt $id/$version", async (receipt) => {
    const f = fixture();
    f.request.mockResolvedValue(receipt);
    expect(
      await f.feature.saveDraft.execute(
        access,
        operation,
        { ...expenseDraftInput(), companyId: company, id },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("preserves safe failure codes and cancellation around acquisition and mapping", async () => {
    const f = fixture(),
      pending = new AbortController();
    f.request.mockRejectedValue(
      new HttpResponseError(409, { code: "expense_receipt_unavailable", detail: "PRIVATE CLAIM" }),
    );
    expect(
      await f.feature.saveDraft.execute(
        access,
        operation,
        { ...expenseDraftInput(), companyId: company, id },
        signal(),
      ),
    ).toEqual({
      ok: false,
      failure: { code: "expense_receipt_unavailable", fields: {}, parameters: {} },
    });
    expect(
      expenseDraftSaveWasRejected({
        code: "expense_receipt_unavailable",
        fields: {},
        parameters: {},
      }),
    ).toBe(true);
    f.request.mockImplementation(async () => {
      pending.abort();
      return expenseDraftEmployees();
    });
    await expect(
      f.feature.loadDraftEmployees.execute(access, "", null, pending.signal),
    ).rejects.toThrow();
    expect(() =>
      f.feature.loadCategories.execute(access, "2026-10-13", null, pending.signal),
    ).toThrow();
  });
});
