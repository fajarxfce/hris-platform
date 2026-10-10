import { describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type {
  ApprovalDelegation,
  ApprovalDelegationId,
  ApprovalDelegationPage,
} from "../../domain/entities/approval-delegation";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { ApprovalDelegationController } from "./approval-delegation-controller";
import { ApprovalDelegationsController } from "./approval-delegations-controller";

const companyId = "10000000-abcd-4000-8000-000000000001" as CompanyId;
const actor = "20000000-abcd-4000-8000-000000000001" as AccountId;
const id = "90000000-abcd-4000-8000-000000000001" as ApprovalDelegationId;
const access = { companyId, permissions: ["approvals.read"] };
const delegation: ApprovalDelegation = {
  id,
  companyId,
  fromAccount: actor,
  toAccount: "20000000-abcd-4000-8000-000000000002" as AccountId,
  kind: "LEAVE",
  active: false,
  version: 1,
  validFrom: "2026-10-01T00:00:00Z",
  validUntil: "2026-10-10T00:00:00Z",
};
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
describe("delegation read ownership", () => {
  it("cancels replaced and disposed list reads without publishing stale failures", async () => {
    const held = deferred<Result<ApprovalDelegationPage>>();
    const load = vi
      .fn<ApprovalsUseCases["loadDelegations"]["execute"]>()
      .mockReturnValueOnce(held.promise)
      .mockResolvedValueOnce(success({ items: [delegation], nextCursor: null }));
    const owner = new ApprovalDelegationsController({ execute: load }, access, actor, null);
    owner.activate();
    const oldSignal = load.mock.calls[0]?.[3];
    await owner.refresh();
    expect(oldSignal?.aborted).toBe(true);
    held.reject(new Error("Late failure"));
    await vi.waitFor(() => expect(owner.getSnapshot().stage).toBe("ready"));
    expect(owner.getSnapshot().page?.items).toEqual([delegation]);
    const late = deferred<Result<ApprovalDelegationPage>>();
    load.mockReturnValueOnce(late.promise);
    const work = owner.refresh();
    owner.deactivate();
    expect(load.mock.lastCall?.[3].aborted).toBe(true);
    late.resolve(success({ items: [delegation], nextCursor: null }));
    await work;
    expect(owner.getSnapshot()).toMatchObject({ stage: "loading", page: null, failure: null });
  });
  it("removes retained details while reloading and never restores data after an access failure", async () => {
    const load = vi
      .fn<ApprovalsUseCases["loadDelegation"]["execute"]>()
      .mockResolvedValue(success(delegation));
    const owner = new ApprovalDelegationController({ execute: load }, access, actor, id);
    owner.activate();
    await vi.waitFor(() => expect(owner.getSnapshot().stage).toBe("ready"));
    const held = deferred<Result<ApprovalDelegation>>();
    load.mockReturnValueOnce(held.promise).mockResolvedValueOnce(failed("access_denied"));
    const work = owner.refresh();
    expect(owner.getSnapshot().delegation).toBeNull();
    const oldSignal = load.mock.lastCall?.[3];
    await owner.refresh();
    expect(oldSignal?.aborted).toBe(true);
    held.resolve(success(delegation));
    await work;
    expect(owner.getSnapshot()).toMatchObject({
      stage: "unavailable",
      delegation: null,
      failure: { code: "access_denied" },
    });
    owner.deactivate();
  });
});
