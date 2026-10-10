import { afterEach, describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type {
  CompanyMember,
  CompanyMemberPage,
} from "../../../identity/domain/entities/company-member";
import type { LoadCompanyMembers } from "../../../identity/domain/usecases/load-company-members";
import type { PersonId, PersonProfile } from "../../domain/entities/person-profile";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { PersonAccountBindingController } from "./person-account-binding-controller";

const access = {
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  permissions: [
    "people.profile.read",
    "people.profile.manage",
    "people.account.link",
    "identity.manage",
  ],
};
const author = "20000000-0000-4000-8000-000000000001" as AccountId;
const profile: PersonProfile = {
  personId: "50000000-0000-4000-8000-000000000001" as PersonId,
  ownerCompanyId: access.companyId,
  accountId: null,
  legalName: "Employee profile",
  nationality: "ID",
  birthDate: null,
  email: null,
  version: 7,
};
const target: CompanyMember = {
  id: "20000000-0000-4000-8000-000000000002" as AccountId,
  email: "employee@example.invalid",
  displayName: "Employee account",
  accountActive: true,
  membershipActive: true,
  permissions: ["people.self.read"],
  version: 1,
};
const owners: PersonAccountBindingController[] = [];
afterEach(() => {
  for (const owner of owners.splice(0)) owner.deactivate();
});
function pending<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function fixture() {
  const loadPersonProfile = {
    execute: vi
      .fn<PeopleUseCases["loadPersonProfile"]["execute"]>()
      .mockResolvedValue(success(profile)),
  };
  const bindPersonAccount = {
    execute: vi
      .fn<PeopleUseCases["bindPersonAccount"]["execute"]>()
      .mockResolvedValue(success({ id: profile.personId, version: 8 })),
  };
  const loadMembers = {
    execute: vi
      .fn<LoadCompanyMembers["execute"]>()
      .mockResolvedValue(success({ items: [target], nextCursor: null })),
  };
  const next = vi.fn(() => "60000000-0000-4000-8000-000000000001");
  const controller = new PersonAccountBindingController(
    { loadPersonProfile, bindPersonAccount },
    loadMembers,
    access,
    author,
    "40000000-0000-4000-8000-000000000001",
    next,
  );
  owners.push(controller);
  return { controller, loadPersonProfile, bindPersonAccount, loadMembers, next };
}
async function ready(controller: PersonAccountBindingController) {
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().candidates).toHaveLength(1));
  controller.selectAccount(target.id);
}

describe("employee binding request ownership", () => {
  it("pins the selected identity and profile version while excluding duplicate submits", async () => {
    const f = fixture();
    await ready(f.controller);
    const held = pending<Result<MutationReceipt>>();
    f.bindPersonAccount.execute.mockReturnValueOnce(held.promise);
    const saved = f.controller.bind("Reviewed account evidence");
    f.controller.selectAccount("another");
    await f.controller.bind("Changed reason");
    await f.controller.refresh();
    await f.controller.loadCandidates(null);
    expect(f.bindPersonAccount.execute).toHaveBeenCalledTimes(1);
    expect(f.bindPersonAccount.execute.mock.calls[0]?.[4]).toEqual({
      personId: profile.personId,
      ownerCompanyId: profile.ownerCompanyId,
      accountId: target.id,
      expectedVersion: 7,
      reason: "Reviewed account evidence",
    });
    expect(Object.isFrozen(f.bindPersonAccount.execute.mock.calls[0]?.[4])).toBe(true);
    held.resolve(success({ id: profile.personId, version: 8 }));
    await saved;
    expect(f.controller.getSnapshot().stage).toBe("bound");
    expect(f.next).toHaveBeenCalledTimes(1);
  });
  it("keeps an uncertain command after a later rejection and recovers only its original receipt", async () => {
    const f = fixture();
    await ready(f.controller);
    f.bindPersonAccount.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("stale_version"));
    await f.controller.bind("Reviewed account evidence");
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.refresh();
    await f.controller.bind("Replacement command");
    expect(f.loadPersonProfile.execute).toHaveBeenCalledTimes(1);
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("bound");
    const submissions = f.bindPersonAccount.execute.mock.calls.map((call) => [call[3], call[4]]);
    expect(submissions).toEqual([submissions[0], submissions[0], submissions[0]]);
    expect(f.next).toHaveBeenCalledTimes(1);
  });
  it("filters the author and disabled memberships but preserves a progressing page cursor", async () => {
    const f = fixture();
    f.loadMembers.execute.mockResolvedValueOnce(
      success({
        items: [
          { ...target, id: author },
          { ...target, accountActive: false },
          { ...target, membershipActive: false },
        ],
        nextCursor: target.id,
      }),
    );
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().candidateNext).toBe(target.id));
    expect(f.controller.getSnapshot().candidates).toEqual([]);
    f.controller.selectAccount(author);
    expect(f.controller.getSnapshot().selected).toBeNull();
    await f.controller.loadCandidates(target.id);
    expect(f.controller.getSnapshot().candidates).toEqual([target]);
    expect(f.loadMembers.execute.mock.calls[1]?.[1]).toBe(target.id);
  });
  it("clears private data on cancellation and rejects late candidate and command responses", async () => {
    const f = fixture();
    await ready(f.controller);
    const held = pending<Result<MutationReceipt>>();
    f.bindPersonAccount.execute.mockReturnValueOnce(held.promise);
    const saved = f.controller.bind("Reviewed evidence");
    const signal = f.bindPersonAccount.execute.mock.calls[0]?.[5];
    f.controller.deactivate();
    expect(signal?.aborted).toBe(true);
    held.resolve(success({ id: profile.personId, version: 8 }));
    await saved;
    expect(f.controller.getSnapshot().profile).toBeNull();
    expect(f.controller.getSnapshot().receipt).toBeNull();
    const candidates = pending<Result<CompanyMemberPage>>();
    f.loadMembers.execute.mockReturnValueOnce(candidates.promise);
    f.controller.activate();
    await vi.waitFor(() => expect(f.loadMembers.execute).toHaveBeenCalledTimes(2));
    const candidateSignal = f.loadMembers.execute.mock.calls[1]?.[2];
    f.controller.deactivate();
    candidates.resolve(success({ items: [target], nextCursor: null }));
    await Promise.resolve();
    expect(candidateSignal?.aborted).toBe(true);
    expect(f.controller.getSnapshot().candidates).toEqual([]);
  });
  it("does not acquire candidate accounts for an already linked or foreign-owned profile", async () => {
    for (const change of [
      { accountId: target.id },
      { ownerCompanyId: "10000000-0000-4000-8000-000000000002" as CompanyId },
    ]) {
      const f = fixture();
      f.loadPersonProfile.execute.mockResolvedValueOnce(success({ ...profile, ...change }));
      f.controller.activate();
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("blocked"));
      expect(f.loadMembers.execute).not.toHaveBeenCalled();
      await f.controller.bind("Attempted replacement");
      expect(f.bindPersonAccount.execute).not.toHaveBeenCalled();
    }
  });
});
