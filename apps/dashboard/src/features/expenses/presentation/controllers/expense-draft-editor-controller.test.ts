import { afterEach, assert, describe, expect, it, vi } from "vitest";
import { expenseIdentifier } from "../../../../../tests/fixtures/expense-claims";
import {
  expenseDraftEmployees,
  expenseDraftInput,
  expenseDraftRecord,
  expenseDraftReferences,
} from "../../../../../tests/fixtures/expense-drafts";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toExpenseClaimReview } from "../../data/mappers/expense-claim-review-mapper";
import {
  toExpenseDraftDirectory,
  toExpenseDraftReferences,
} from "../../data/mappers/expense-drafting-mapper";
import type { ExpenseClaimId } from "../../domain/entities/expense-claim";
import type { ExpenseDraftEditReview } from "../../domain/entities/expense-draft-edit-review";
import { expenseDraftReferenceQuery } from "../../domain/policies/expense-draft-policy";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { ExpenseDraftEditorController } from "./expense-draft-editor-controller";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId,
  access = { companyId: company, permissions: ["expenses.manage"] },
  id = expenseIdentifier(1);
const controllers: ExpenseDraftEditorController[] = [];
afterEach(() => {
  for (const controller of controllers.splice(0)) controller.deactivate();
});
function deferred<T>() {
  let resolve!: (value: Result<T>) => void;
  const promise = new Promise<Result<T>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function review(): ExpenseDraftEditReview {
  const raw = expenseDraftRecord(0, true),
    claim = toExpenseClaimReview(raw, company, raw.claim.id as ExpenseClaimId, null).claim;
  return {
    claim,
    references: toExpenseDraftReferences(
      expenseDraftReferences(),
      company,
      claim.employmentId,
      expenseDraftReferenceQuery(claim.draft.lines),
    ),
  };
}
function fixture(creating = true) {
  const original = review(),
    actions = {
      reviewDraftEdit: {
        execute: vi
          .fn<ExpenseUseCases["reviewDraftEdit"]["execute"]>()
          .mockResolvedValue(success(original)),
      },
      saveDraft: {
        execute: vi
          .fn<ExpenseUseCases["saveDraft"]["execute"]>()
          .mockResolvedValue(success({ id, version: creating ? 0 : 3 })),
      },
    };
  let next = 1000;
  const nextId = vi.fn(() => expenseIdentifier(++next)),
    controller = new ExpenseDraftEditorController(actions, access, creating, id, nextId);
  controllers.push(controller);
  controller.activate();
  const employee = toExpenseDraftDirectory(expenseDraftEmployees(), company, null).employees
    .items[0];
  assert(employee);
  return {
    controller,
    actions,
    original,
    nextId,
    employee,
    choose: () => controller.chooseEmployee(employee, "2026-10-13"),
    fields: { title: "Client travel", description: "October visit", reason: "Recorded" },
  };
}
describe("expense draft editor ownership", () => {
  it("chooses a scoped employee once; opening and cancelling an item does not write", async () => {
    const f = fixture();
    f.controller.chooseEmployee(
      { ...f.employee, companyId: expenseIdentifier(444) as CompanyId },
      "2026-10-13",
    );
    expect(f.controller.getSnapshot().stage).toBe("choosing");
    f.choose();
    f.controller.chooseEmployee({ ...f.employee, id: expenseIdentifier(101) }, "2026-10-13");
    expect(f.controller.getSnapshot().employee?.id).toBe(f.employee.id);
    f.controller.addLine("2026-10-12");
    const panel = f.controller.getSnapshot().panel;
    assert(panel.kind === "line");
    expect(panel.line.occurredOn).toBe("2026-10-13");
    f.controller.setLineDirty(panel.line.id, true);
    await f.controller.save(f.fields);
    expect(f.actions.saveDraft.execute).not.toHaveBeenCalled();
    f.controller.closeLine();
    f.controller.setLineDirty(panel.line.id, true);
    expect(f.controller.getSnapshot().panel).toEqual({ kind: "closed" });
    expect(f.controller.getSnapshot().lines).toEqual([]);
  });
  it("stages validated items with bounded labels and removes them locally", () => {
    const f = fixture();
    f.choose();
    f.controller.addLine("2026-10-13");
    const panel = f.controller.getSnapshot().panel,
      source = expenseDraftInput().lines[0];
    assert(panel.kind === "line" && source);
    f.controller.saveLine({ ...source, id: panel.line.id, amount: "wrong" }, f.original.references);
    expect(f.controller.getSnapshot().entryFailure?.fields.amount).toBe("invalid_amount");
    f.controller.saveLine(
      { ...source, id: panel.line.id, description: "  Train ticket  " },
      f.original.references,
    );
    expect(f.controller.getSnapshot().lines[0]?.description).toBe("Train ticket");
    expect(f.controller.getSnapshot().references?.receipts).toHaveLength(1);
    f.controller.editLine(panel.line.id);
    f.controller.removeLine();
    expect(f.controller.getSnapshot().lines).toHaveLength(0);
    expect(f.controller.getSnapshot().references?.receipts).toHaveLength(0);
    expect(f.actions.saveDraft.execute).not.toHaveBeenCalled();
  });
  it("bounds staged items and rejects stale line-form callbacks", () => {
    const f = fixture(),
      source = expenseDraftInput().lines[0];
    assert(source);
    f.choose();
    for (let i = 0; i < 20; i++) {
      f.controller.addLine("2026-10-13");
      const panel = f.controller.getSnapshot().panel;
      assert(panel.kind === "line");
      f.controller.saveLine({ ...source, id: panel.line.id }, f.original.references);
    }
    f.controller.addLine("2026-10-13");
    expect(f.controller.getSnapshot().panel.kind).toBe("closed");
    expect(f.nextId).toHaveBeenCalledTimes(20);
    const first = f.controller.getSnapshot().lines[0],
      second = f.controller.getSnapshot().lines[1];
    assert(first && second);
    f.controller.editLine(second.id);
    f.controller.saveLine(first, f.original.references);
    f.controller.setLineDirty(first.id, true);
    expect(f.controller.getSnapshot().panel).toMatchObject({
      kind: "line",
      line: { id: second.id },
      dirty: false,
    });
    expect(f.controller.getSnapshot().references?.categories).toHaveLength(1);
  });
  it("saves a returned claim at the reviewed version without changing employment or original item IDs", async () => {
    const f = fixture(false);
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    expect(f.controller.getSnapshot().original?.claim.status).toBe("RETURNED");
    await f.controller.save(f.fields);
    expect(f.actions.saveDraft.execute.mock.calls[0]?.[2]).toMatchObject({
      id,
      employmentId: f.original.claim.employmentId,
      expectedVersion: 2,
      lines: f.original.claim.draft.lines,
    });
    expect(f.nextId).toHaveBeenCalledTimes(1);
    expect(f.controller.getSnapshot().stage).toBe("saved");
  });
  it("freezes one original command through response loss, MFA, a later rejection and retry", async () => {
    const f = fixture();
    f.choose();
    f.actions.saveDraft.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_version"))
      .mockResolvedValueOnce(success({ id, version: 0 }));
    await f.controller.save(f.fields);
    const original = f.actions.saveDraft.execute.mock.calls[0];
    assert(original);
    f.fields.title = "Changed after request";
    await f.controller.save(f.fields);
    await f.controller.refresh();
    expect(f.actions.saveDraft.execute).toHaveBeenCalledTimes(1);
    await f.controller.retry();
    expect(f.controller.getSnapshot().employee).toBeNull();
    expect(f.controller.getSnapshot().original).toBeNull();
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    for (const call of f.actions.saveDraft.execute.mock.calls) {
      expect(call[1]).toBe(original[1]);
      expect(call[2]).toBe(original[2]);
      expect(call[2].title).toBe("Client travel");
    }
    expect(Object.isFrozen(original[2].lines)).toBe(true);
    expect(f.nextId).toHaveBeenCalledTimes(1);
  });
  it("requires explicit reload after a definite conflict and never adopts another version silently", async () => {
    const f = fixture(false);
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.actions.saveDraft.execute.mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(f.fields);
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    expect(f.controller.getSnapshot().original?.claim.version).toBe(2);
    await f.controller.save(f.fields);
    await f.controller.retry();
    expect(f.actions.saveDraft.execute).toHaveBeenCalledTimes(1);
    f.actions.reviewDraftEdit.execute.mockResolvedValue(
      success({ ...f.original, claim: { ...f.original.claim, version: 3 } }),
    );
    await f.controller.refresh();
    expect(f.controller.getSnapshot().original?.claim.version).toBe(3);
  });
  it("keeps a definite validation rejection editable and hides access-denied evidence", async () => {
    const f = fixture();
    f.choose();
    f.actions.saveDraft.execute
      .mockResolvedValueOnce(failed("expense_receipt_unavailable"))
      .mockResolvedValueOnce(failed("expense_access_denied"));
    await f.controller.save(f.fields);
    expect(f.controller.getSnapshot().stage).toBe("editing");
    expect(f.controller.getSnapshot().operationId).toBeNull();
    await f.controller.save(f.fields);
    expect(f.controller.getSnapshot().stage).toBe("unavailable");
    expect(f.controller.getSnapshot().employee).toBeNull();
    expect(f.actions.saveDraft.execute.mock.calls[0]?.[1]).not.toBe(
      f.actions.saveDraft.execute.mock.calls[1]?.[1],
    );
  });
  it("withdraws visible employee and item evidence after a picker loses scope", async () => {
    const f = fixture(false);
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.controller.withdrawEvidence({ code: "request_timeout", fields: {}, parameters: {} });
    expect(f.controller.getSnapshot().stage).toBe("editing");
    f.controller.withdrawEvidence({ code: "employee_not_found", fields: {}, parameters: {} });
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      employee: null,
      original: null,
      references: null,
      lines: [],
    });
  });
  it("aborts obsolete reviews and ignores late denials after refresh", async () => {
    const f = fixture(false);
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    const pending = deferred<ExpenseDraftEditReview>();
    f.actions.reviewDraftEdit.execute.mockReturnValueOnce(pending.promise);
    const first = f.controller.refresh(),
      old = f.actions.reviewDraftEdit.execute.mock.calls.at(-1)?.[2];
    await f.controller.refresh();
    expect(old?.aborted).toBe(true);
    pending.resolve(failed("access_denied"));
    await first;
    expect(f.controller.getSnapshot().stage).toBe("editing");
    expect(f.controller.getSnapshot().failure).toBeNull();
  });
  it.each(["receipt", "failure"])(
    "aborts a pending save on disposal and ignores its late %s",
    async (outcome) => {
      const f = fixture(),
        pending = deferred<{ id: string; version: number }>();
      f.choose();
      f.actions.saveDraft.execute.mockReturnValueOnce(pending.promise);
      const save = f.controller.save(f.fields);
      await f.controller.save(f.fields);
      await f.controller.retry();
      expect(f.actions.saveDraft.execute).toHaveBeenCalledTimes(1);
      const old = f.actions.saveDraft.execute.mock.calls[0]?.[3];
      f.controller.deactivate();
      expect(old?.aborted).toBe(true);
      pending.resolve(
        outcome === "receipt" ? success({ id, version: 0 }) : failed("access_denied"),
      );
      await save;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "loading",
        receipt: null,
        employee: null,
        operationId: null,
      });
    },
  );
});
