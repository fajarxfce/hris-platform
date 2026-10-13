import { afterEach, assert, describe, expect, it, vi } from "vitest";
import { expenseIdentifier } from "../../../../../tests/fixtures/expense-claims";
import { expensePayablePage } from "../../../../../tests/fixtures/expense-payables";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toExpensePayablePage } from "../../data/mappers/expense-payable-mapper";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { expensePreparationView } from "../models/expense-preparation-view";
import { ExpensePreparationController } from "./expense-preparation-controller";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId: company, permissions: ["expenses.pay"] };
const controllers: ExpensePreparationController[] = [];
afterEach(() => {
  for (const controller of controllers.splice(0)) controller.deactivate();
});
function deferred<T>() {
  let resolve!: (result: Result<T>) => void;
  const promise = new Promise<Result<T>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function fixture() {
  const payables = toExpensePayablePage(expensePayablePage(), company, {
    from: "2026-10-01",
    until: "2026-10-31",
    after: null,
  }).items;
  const prepare = {
    execute: vi
      .fn<ExpenseUseCases["preparePayment"]["execute"]>()
      .mockResolvedValue(success({ id: expenseIdentifier(902), version: 0 })),
  };
  let index = 900;
  const next = vi.fn(() => expenseIdentifier(++index));
  const controller = new ExpensePreparationController(prepare, access, next);
  controllers.push(controller);
  controller.activate();
  const fill = () => {
    controller.openPayables();
    for (const payable of payables) controller.addPayable(payable);
    controller.closePanel();
    for (const payable of payables) {
      controller.selectDestination(payable.claimId);
      controller.saveDestination({
        bankCode: "014",
        accountNumber: "001234567890",
        accountName: payable.employeeName,
      });
    }
  };
  return { controller, payables, prepare, next, fill };
}
describe("expense preparation ownership", () => {
  it("stages and corrects multiple bank instructions without acquiring or writing", () => {
    const f = fixture();
    f.fill();
    const first = f.payables[0];
    assert(first);
    expect(f.controller.getSnapshot().items).toHaveLength(2);
    expect(expensePreparationView(f.controller.getSnapshot(), "en").properties).toContainEqual({
      label: "Selected amount",
      value: "IDR 425,001.00",
    });
    f.controller.selectDestination(first.claimId);
    f.controller.saveDestination({
      bankCode: "009",
      accountNumber: "000999999",
      accountName: "Corrected name",
    });
    expect(f.controller.getSnapshot().items[0]?.destination?.accountNumber).toBe("000999999");
    f.controller.selectDestination(first.claimId);
    f.controller.removeSelected();
    expect(f.controller.getSnapshot().items.map((item) => item.payable.claimId)).toEqual([
      f.payables[1]?.claimId,
    ]);
    expect(f.prepare.execute).not.toHaveBeenCalled();
    expect(f.next).not.toHaveBeenCalled();
  });
  it("ignores stale panel dirtiness and refuses review with uncommitted bank input", () => {
    const f = fixture();
    f.fill();
    const first = f.payables[0],
      second = f.payables[1];
    assert(first && second);
    f.controller.selectDestination(first.claimId);
    f.controller.setDestinationDirty(first.claimId, true);
    f.controller.review("Batch", "Reviewed");
    expect(f.controller.getSnapshot().stage).toBe("editing");
    f.controller.selectDestination(second.claimId);
    f.controller.setDestinationDirty(first.claimId, true);
    expect(f.controller.getSnapshot().panel).toEqual({
      kind: "destination",
      claimId: second.claimId,
      dirty: false,
    });
    f.controller.closePanel();
    f.controller.setDestinationDirty(second.claimId, true);
    expect(f.controller.getSnapshot().panel).toEqual({ kind: "closed" });
  });
  it("validates missing destinations, selected identity and bank input without leaving editing", () => {
    const f = fixture(),
      first = f.payables[0];
    assert(first);
    f.controller.addPayable(first);
    expect(f.controller.getSnapshot().items).toHaveLength(0);
    f.controller.openPayables();
    f.controller.addPayable(first);
    f.controller.addPayable(first);
    expect(f.controller.getSnapshot().entryFailure?.code).toBe("expense_payment_duplicate_claim");
    f.controller.closePanel();
    f.controller.review("Batch", "Reviewed");
    expect(f.controller.getSnapshot().failure?.code).toBe("invalid_expense_payment_destination");
    f.controller.selectDestination(expenseIdentifier(1));
    expect(f.controller.getSnapshot().panel.kind).toBe("closed");
    f.controller.selectDestination(first.claimId);
    f.controller.saveDestination({ bankCode: "", accountNumber: "12", accountName: "" });
    expect(f.controller.getSnapshot().panel.kind).toBe("destination");
    expect(f.controller.getSnapshot().entryFailure?.code).toBe(
      "invalid_expense_payment_destination",
    );
    expect(f.prepare.execute).not.toHaveBeenCalled();
  });
  it("captures one immutable command and ignores duplicate submission and local changes while pending", async () => {
    const f = fixture(),
      held = deferred<{ id: string; version: number }>();
    f.fill();
    f.controller.review("Original batch", "Original note");
    f.prepare.execute.mockReturnValueOnce(held.promise);
    const pending = f.controller.confirm();
    await f.controller.confirm();
    await f.controller.retry();
    f.controller.edit();
    f.controller.restart();
    f.controller.openPayables();
    f.controller.review("Changed", "Changed");
    expect(f.prepare.execute).toHaveBeenCalledTimes(1);
    const sent = f.prepare.execute.mock.calls[0];
    assert(sent);
    expect(sent.slice(0, 3)).toEqual([access, expenseIdentifier(901), expenseIdentifier(902)]);
    expect(sent[3]).toMatchObject({ title: "Original batch", reason: "Original note" });
    expect(Object.isFrozen(sent[3].items[0]?.destination)).toBe(true);
    expect(Object.isFrozen(sent[4])).toBe(true);
    expect(sent[4]).toEqual([expenseIdentifier(903), expenseIdentifier(904)]);
    expect(f.next).toHaveBeenCalledTimes(4);
    held.resolve(success({ id: expenseIdentifier(902), version: 0 }));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "saved", items: [], review: null });
  });
  it.each([
    "mfa_required",
    "access_denied",
    "data_conflict",
    "expense_claim_payment_reserved",
    "independent_expense_payment_required",
    "expense_payment_batch_exists",
  ])("retains all original instructions after response loss followed by %s", async (code) => {
    const f = fixture();
    f.fill();
    f.controller.review("Original", "Reviewed");
    f.prepare.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed(code));
    await f.controller.confirm();
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    f.controller.edit();
    f.controller.restart();
    f.controller.openPayables();
    await f.controller.confirm();
    expect(f.prepare.execute).toHaveBeenCalledTimes(2);
    await f.controller.retry();
    const calls = f.prepare.execute.mock.calls;
    expect(calls).toHaveLength(3);
    for (const call of calls) expect(call.slice(0, 5)).toEqual(calls[0]?.slice(0, 5));
    expect(f.next).toHaveBeenCalledTimes(4);
    expect(f.controller.getSnapshot().stage).toBe("saved");
  });
  it("requires explicit correction and review after an initially rejected reservation", async () => {
    const f = fixture();
    f.fill();
    f.controller.review("Original", "Reviewed");
    f.prepare.execute.mockResolvedValueOnce(failed("expense_claim_payment_reserved"));
    await f.controller.confirm();
    expect(f.controller.getSnapshot().stage).toBe("editing");
    await f.controller.confirm();
    await f.controller.retry();
    expect(f.prepare.execute).toHaveBeenCalledTimes(1);
    const first = f.payables[0];
    assert(first);
    f.controller.selectDestination(first.claimId);
    f.controller.removeSelected();
    f.controller.review("Remaining claim", "Excluded reserved claim");
    await f.controller.confirm();
    expect(f.prepare.execute.mock.calls[1]?.[1]).toBe(expenseIdentifier(905));
    expect(f.prepare.execute.mock.calls[1]?.[3].items).toHaveLength(1);
  });
  it("withdraws private data on initial access denial and does not restart an unknown write", async () => {
    const f = fixture();
    f.fill();
    f.controller.review("Batch", "Reviewed");
    f.prepare.execute.mockResolvedValueOnce(failed("access_denied"));
    await f.controller.confirm();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      items: [],
      review: null,
    });
    f.controller.restart();
    f.fill();
    f.controller.withdrawEvidence({ code: "mfa_required", fields: {}, parameters: {} });
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      items: [],
      review: null,
    });
  });
  it.each([true, false])(
    "disposal cancels a pending write and ignores late success=%s",
    async (succeeds) => {
      const f = fixture(),
        held = deferred<{ id: string; version: number }>();
      f.fill();
      f.controller.review("Batch", "Reviewed");
      f.prepare.execute.mockReturnValueOnce(held.promise);
      const pending = f.controller.confirm(),
        signal = f.prepare.execute.mock.calls[0]?.[5];
      f.controller.deactivate();
      f.controller.activate();
      expect(signal?.aborted).toBe(true);
      held.resolve(
        succeeds ? success({ id: expenseIdentifier(902), version: 0 }) : failed("access_denied"),
      );
      await pending;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "editing",
        items: [],
        failure: null,
      });
    },
  );
  it("removes subscriptions and does not reset active input on repeated activation", () => {
    const f = fixture(),
      listener = vi.fn(),
      unsubscribe = f.controller.subscribe(listener);
    f.fill();
    const state = f.controller.getSnapshot();
    f.controller.activate();
    expect(f.controller.getSnapshot()).toBe(state);
    unsubscribe();
    listener.mockClear();
    f.controller.openPayables();
    expect(listener).not.toHaveBeenCalled();
  });
});
