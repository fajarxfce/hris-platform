import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { PersonProfileHistoryPage } from "../../domain/entities/person-profile-revision";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { PersonProfileHistoryController } from "./person-profile-history-controller";

const access = {
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  permissions: ["people.profile.read"],
};
const id = "40000000-0000-4000-8000-000000000001";
const page: PersonProfileHistoryPage = {
  items: [
    {
      revision: 0,
      legalName: "Private profile",
      birthDate: "1995-01-01",
      nationality: "ID",
      email: null,
      actorId: null,
      accountId: null,
      reason: "Initial employment",
      recordedAt: "2026-01-01T00:00:00Z",
    },
  ],
  nextCursor: null,
};

describe("private profile history ownership", () => {
  it("selects only visible revisions including zero and clears the selection before a failed refresh", async () => {
    const execute = vi
      .fn<PeopleUseCases["loadPersonProfileHistory"]["execute"]>()
      .mockResolvedValueOnce(success(page))
      .mockResolvedValue(failed("access_denied"));
    const controller = new PersonProfileHistoryController({ execute }, access, id, null);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().page).toBe(page));
    controller.openRevision("9");
    expect(controller.getSnapshot().selected).toBeNull();
    controller.openRevision("0");
    expect(controller.getSnapshot().selected).toBe(page.items[0]);
    controller.closeRevision();
    expect(controller.getSnapshot().selected).toBeNull();
    controller.openRevision("0");
    const refresh = controller.refresh();
    expect(controller.getSnapshot()).toMatchObject({
      page: null,
      selected: null,
      stage: "loading",
    });
    await refresh;
    controller.openRevision("0");
    expect(controller.getSnapshot()).toMatchObject({
      page: null,
      selected: null,
      failure: { code: "access_denied" },
    });
    controller.deactivate();
  });
  it("rejects late history outcomes after tab disposal and retains the exact cursor", async () => {
    for (const rejects of [false, true]) {
      let resolve!: (result: Result<PersonProfileHistoryPage>) => void;
      let reject!: (error: unknown) => void;
      const held = new Promise<Result<PersonProfileHistoryPage>>((done, fail) => {
        resolve = done;
        reject = fail;
      });
      const execute = vi
        .fn<PeopleUseCases["loadPersonProfileHistory"]["execute"]>()
        .mockReturnValueOnce(held)
        .mockResolvedValue(success(page));
      const controller = new PersonProfileHistoryController({ execute }, access, id, "0");
      controller.activate();
      controller.activate();
      expect(execute.mock.lastCall?.slice(0, 3)).toEqual([access, id, "0"]);
      const signal = execute.mock.lastCall?.[3];
      controller.deactivate();
      expect(signal?.aborted).toBe(true);
      if (rejects) reject(new Error("PRIVATE LATE FAILURE"));
      else resolve(success(page));
      await Promise.resolve();
      expect(controller.getSnapshot()).toEqual({
        stage: "loading",
        page: null,
        selected: null,
        failure: null,
      });
      controller.openRevision("0");
      await controller.refresh();
      expect(execute).toHaveBeenCalledTimes(1);
      controller.activate();
      await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
      controller.deactivate();
    }
  });
});
