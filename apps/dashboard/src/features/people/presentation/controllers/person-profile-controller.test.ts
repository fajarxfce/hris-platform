import { afterEach, describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { PersonId, PersonProfile } from "../../domain/entities/person-profile";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { PersonProfileController, type ProfileEditableFields } from "./person-profile-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const employeeId = "40000000-0000-4000-8000-000000000001";
const personId = "50000000-0000-4000-8000-000000000001" as PersonId;
const profile: PersonProfile = {
  personId,
  ownerCompanyId: companyId,
  accountId: null,
  legalName: "Private profile",
  birthDate: "1990-03-04",
  nationality: "ID",
  email: null,
  version: 7,
};
const fields: ProfileEditableFields = {
  legalName: "Updated private profile",
  birthDate: null,
  nationality: "SG",
  email: null,
  reason: "Verified correction",
};
const owners: PersonProfileController[] = [];
afterEach(() => {
  for (const owner of owners.splice(0)) owner.deactivate();
});
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
function fixture(
  mode: "read" | "edit" = "edit",
  permissions = ["people.profile.read", "people.profile.manage"],
) {
  const loadPersonProfile = {
    execute: vi
      .fn<PeopleUseCases["loadPersonProfile"]["execute"]>()
      .mockResolvedValue(success(profile)),
  };
  const savePersonProfile = {
    execute: vi
      .fn<PeopleUseCases["savePersonProfile"]["execute"]>()
      .mockResolvedValue(success({ id: personId, version: 8 })),
  };
  let issued = 0;
  const next = vi.fn(() => `60000000-0000-4000-8000-${String(++issued).padStart(12, "0")}`);
  const controller = new PersonProfileController(
    { loadPersonProfile, savePersonProfile },
    { companyId, permissions },
    employeeId,
    mode,
    next,
  );
  owners.push(controller);
  controller.activate();
  return { controller, loadPersonProfile, savePersonProfile, next };
}

describe("private profile ownership", () => {
  it("pins person identity and the loaded profile version, excludes double saves, and keeps acknowledgement independent from reads", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    const held = pending<Result<MutationReceipt>>();
    f.savePersonProfile.execute.mockReturnValueOnce(held.promise);
    const input = { ...fields };
    const saving = f.controller.save(input);
    input.reason = "Changed after submitting";
    await f.controller.save(fields);
    await f.controller.refresh();
    expect(f.savePersonProfile.execute).toHaveBeenCalledOnce();
    expect(f.savePersonProfile.execute.mock.lastCall?.[3]).toEqual({
      ...fields,
      personId,
      ownerCompanyId: companyId,
      expectedVersion: 7,
    });
    expect(Object.isFrozen(f.savePersonProfile.execute.mock.lastCall?.[3])).toBe(true);
    held.resolve(success({ id: personId, version: 8 }));
    await saving;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      receipt: { id: personId, version: 8 },
      operationId: null,
    });
    expect(f.loadPersonProfile.execute).toHaveBeenCalledOnce();
  });
  it("keeps uncertain changes immutable through MFA and stale retries without generating another operation", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.savePersonProfile.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(fields);
    const first = f.savePersonProfile.execute.mock.calls[0];
    f.controller.reportScopeFailure({ code: "mfa_required", fields: {}, parameters: {} });
    await f.controller.refresh();
    await f.controller.save({ ...fields, legalName: "New attempt" });
    for (let attempt = 0; attempt < 2; attempt += 1) {
      await f.controller.retrySave();
      expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
      expect(f.savePersonProfile.execute.mock.lastCall?.[2]).toBe(first?.[2]);
      expect(f.savePersonProfile.execute.mock.lastCall?.[3]).toBe(first?.[3]);
    }
    await f.controller.retrySave();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledOnce();
    expect(f.loadPersonProfile.execute).toHaveBeenCalledOnce();
  });
  it("requires explicit reload after a definite conflict before taking a new observed version", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.savePersonProfile.execute.mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(fields);
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.save(fields);
    expect(f.savePersonProfile.execute).toHaveBeenCalledOnce();
    f.loadPersonProfile.execute.mockResolvedValueOnce(success({ ...profile, version: 9 }));
    await f.controller.refresh();
    await f.controller.save(fields);
    expect(f.savePersonProfile.execute.mock.lastCall?.[3].expectedVersion).toBe(9);
    expect(f.next).toHaveBeenCalledTimes(2);
  });
  it("does not let a read-only or non-owning editor acquire a writable profile", async () => {
    const denied = fixture("edit", ["people.profile.read"]);
    expect(denied.controller.getSnapshot().stage).toBe("unavailable");
    expect(denied.loadPersonProfile.execute).not.toHaveBeenCalled();
    const foreign = fixture();
    await vi.waitFor(() => expect(foreign.controller.getSnapshot().stage).toBe("editing"));
    foreign.loadPersonProfile.execute.mockResolvedValueOnce(
      success({ ...profile, ownerCompanyId: "10000000-0000-4000-8000-000000000002" as CompanyId }),
    );
    await foreign.controller.refresh();
    await foreign.controller.save(fields);
    expect(foreign.controller.getSnapshot()).toMatchObject({
      profile: null,
      failure: { code: "profile_owner_required" },
    });
    expect(foreign.savePersonProfile.execute).not.toHaveBeenCalled();
    const reading = fixture("read");
    await vi.waitFor(() => expect(reading.controller.getSnapshot().stage).toBe("ready"));
    await reading.controller.save(fields);
    expect(reading.savePersonProfile.execute).not.toHaveBeenCalled();
  });
  it.each([false, true])(
    "disposal rejects a late save (failure: %s) and cannot restore private state after reactivation",
    async (rejects) => {
      const f = fixture();
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
      const held = pending<Result<MutationReceipt>>();
      f.savePersonProfile.execute.mockReturnValueOnce(held.promise);
      const saving = f.controller.save(fields);
      const signal = f.savePersonProfile.execute.mock.lastCall?.[4];
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      f.controller.activate();
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
      if (rejects) held.reject(new Error("PRIVATE LATE FAILURE"));
      else held.resolve(success({ id: personId, version: 8 }));
      await saving;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "editing",
        receipt: null,
        operationId: null,
        failure: null,
      });
    },
  );
  it("clears sensitive overview on an invalidated history read and ignores an obsolete profile response", async () => {
    const f = fixture("read");
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
    const held = pending<Result<PersonProfile>>();
    f.loadPersonProfile.execute.mockReturnValueOnce(held.promise);
    const refresh = f.controller.refresh();
    f.controller.reportScopeFailure({ code: "access_denied", fields: {}, parameters: {} });
    held.resolve(success(profile));
    await refresh;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      profile: null,
      failure: { code: "access_denied" },
    });
    expect(f.savePersonProfile.execute).not.toHaveBeenCalled();
  });
});
