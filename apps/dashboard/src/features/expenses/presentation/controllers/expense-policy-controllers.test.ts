import { expect, it, vi } from "vitest";
import { expensePolicyRecord } from "../../../../../tests/fixtures/expense-policies";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  toExpensePolicyPage,
  toExpensePolicyReview,
} from "../../data/mappers/expense-policy-mapper";
import type {
  ExpensePolicyId,
  ExpensePolicyPage,
} from "../../domain/entities/expense-policy-definition";
import type { ExpensePolicyReview } from "../../domain/entities/expense-policy-review";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { ExpensePoliciesController } from "./expense-policies-controller";
import { ExpensePolicyController } from "./expense-policy-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["expenses.policy.manage"] };
const query = { active: null, after: null };
const raw = expensePolicyRecord(0, 1, 2);
const review = toExpensePolicyReview(raw, companyId, raw.current.id as ExpensePolicyId, null);
const page = toExpensePolicyPage({ items: [raw.current], nextCursor: null }, companyId, null, null);
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

it("selection is limited to loaded revisions and cannot restart I/O or replace the current head", async () => {
  const load = vi.fn<ExpenseUseCases["loadPolicy"]["execute"]>().mockResolvedValue(success(review));
  const controller = new ExpensePolicyController(
    { execute: load },
    access,
    review.current.id,
    null,
  );
  controller.selectRevision("1");
  expect(controller.getSnapshot().selectedRevision).toBeNull();
  controller.activate();
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
  controller.selectRevision("1");
  expect(controller.getSnapshot().selectedRevision).toBe(review.history.items[1]);
  expect(controller.getSnapshot().review?.current.version).toBe(2);
  controller.selectRevision("900");
  expect(controller.getSnapshot().selectedRevision).toBe(review.history.items[1]);
  expect(load).toHaveBeenCalledTimes(1);
  controller.deactivate();
  expect(controller.getSnapshot().selectedRevision).toBeNull();
});

it("a refresh redacts selected evidence and cancels an older read before an access failure", async () => {
  const load = vi.fn<ExpenseUseCases["loadPolicy"]["execute"]>().mockResolvedValue(success(review));
  const controller = new ExpensePolicyController({ execute: load }, access, review.current.id, "4");
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
  expect(load.mock.lastCall?.slice(0, 3)).toEqual([access, review.current.id, "4"]);
  controller.selectRevision("1");
  const old = deferred<Result<ExpensePolicyReview>>();
  load.mockReturnValueOnce(old.promise);
  const reading = controller.refresh();
  const signal = load.mock.lastCall?.[3];
  expect(controller.getSnapshot()).toMatchObject({ review: null, selectedRevision: null });
  load.mockResolvedValueOnce(failed("expense_category_not_found"));
  await controller.refresh();
  old.resolve(success(review));
  await reading;
  expect(signal?.aborted).toBe(true);
  expect(controller.getSnapshot()).toMatchObject({
    stage: "unavailable",
    review: null,
    selectedRevision: null,
    failure: { code: "expense_category_not_found" },
  });
  controller.deactivate();
});

it.each(["result", "failure"] as const)(
  "detail disposal ignores a late %s and releases its subscriber",
  async (kind) => {
    const held = deferred<Result<ExpensePolicyReview>>();
    const load = vi.fn<ExpenseUseCases["loadPolicy"]["execute"]>().mockReturnValue(held.promise);
    const controller = new ExpensePolicyController(
      { execute: load },
      access,
      review.current.id,
      null,
    );
    const listener = vi.fn();
    const unsubscribe = controller.subscribe(listener);
    controller.activate();
    const work = controller.refresh();
    const signal = load.mock.lastCall?.[3];
    unsubscribe();
    const calls = listener.mock.calls.length;
    controller.deactivate();
    if (kind === "result") held.resolve(success(review));
    else held.reject(new Error("Private late failure"));
    await work;
    expect(signal?.aborted).toBe(true);
    expect(listener).toHaveBeenCalledTimes(calls);
    expect(controller.getSnapshot()).toEqual({
      stage: "loading",
      review: null,
      selectedRevision: null,
      failure: null,
    });
    await controller.refresh();
    expect(load).toHaveBeenCalledTimes(2);
  },
);

it("catalog refresh cannot restore an old page after current access was denied", async () => {
  const load = vi.fn<ExpenseUseCases["loadPolicies"]["execute"]>().mockResolvedValue(success(page));
  const controller = new ExpensePoliciesController({ execute: load }, access, query);
  controller.activate();
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().page).toBe(page));
  expect(load).toHaveBeenCalledTimes(1);
  const old = deferred<Result<ExpensePolicyPage>>();
  load.mockReturnValueOnce(old.promise);
  const reading = controller.refresh();
  const signal = load.mock.lastCall?.[2];
  expect(controller.getSnapshot().page).toBeNull();
  load.mockResolvedValueOnce(failed("access_denied"));
  await controller.refresh();
  old.reject(new Error("Obsolete failure"));
  await reading;
  expect(signal?.aborted).toBe(true);
  expect(controller.getSnapshot()).toMatchObject({
    stage: "unavailable",
    page: null,
    failure: { code: "access_denied" },
  });
  controller.deactivate();
});

it("catalog disposal aborts a pending page and supports a fresh owned activation", async () => {
  const held = deferred<Result<ExpensePolicyPage>>();
  const load = vi.fn<ExpenseUseCases["loadPolicies"]["execute"]>().mockReturnValue(held.promise);
  const controller = new ExpensePoliciesController({ execute: load }, access, query);
  controller.activate();
  const reading = controller.refresh();
  const signal = load.mock.lastCall?.[2];
  controller.deactivate();
  held.resolve(success(page));
  await reading;
  expect(signal?.aborted).toBe(true);
  expect(controller.getSnapshot()).toEqual({ stage: "loading", page: null, failure: null });
  load.mockResolvedValueOnce(success(page));
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().page).toBe(page));
  controller.deactivate();
});
