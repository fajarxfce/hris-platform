import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyMemberPage } from "../../domain/entities/company-member";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import { CompanyMemberController } from "./company-member-controller";
import { CompanyMembersController } from "./company-members-controller";

const access = {
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  permissions: ["identity.manage"],
};
const page: CompanyMemberPage = { items: [], nextCursor: null };
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}

describe("membership screen ownership", () => {
  it("drops superseded results and late failures without retaining a previous page", async () => {
    for (const rejects of [false, true]) {
      const previous = pending<Result<CompanyMemberPage>>();
      const execute = vi
        .fn<IdentityAdministrationUseCases["loadCompanyMembers"]["execute"]>()
        .mockReturnValueOnce(previous.promise)
        .mockResolvedValue(success(page));
      const controller = new CompanyMembersController({ execute }, access, null);
      controller.activate();
      controller.activate();
      expect(execute).toHaveBeenCalledTimes(1);
      const oldSignal = execute.mock.calls[0]?.[2];
      await controller.refresh();
      expect(oldSignal?.aborted).toBe(true);
      if (rejects) previous.reject(new Error("Private late failure"));
      else previous.resolve(failed("access_denied"));
      await Promise.resolve();
      expect(controller.getSnapshot()).toEqual({ stage: "ready", page, failure: null });
      controller.deactivate();
      expect(controller.getSnapshot().page).toBeNull();
      await controller.refresh();
      expect(execute).toHaveBeenCalledTimes(2);
    }
  });
  it("stops detail requests on disposal and ignores an uncooperative late rejection", async () => {
    const previous =
      pending<
        Awaited<ReturnType<IdentityAdministrationUseCases["loadCompanyMember"]["execute"]>>
      >();
    const execute = vi
      .fn<IdentityAdministrationUseCases["loadCompanyMember"]["execute"]>()
      .mockReturnValueOnce(previous.promise)
      .mockResolvedValue(failed("company_access_denied"));
    const controller = new CompanyMemberController(
      { execute },
      access,
      "20000000-0000-4000-8000-000000000001",
    );
    controller.activate();
    const signal = execute.mock.calls[0]?.[2];
    controller.deactivate();
    expect(signal?.aborted).toBe(true);
    controller.activate();
    await vi.waitFor(() =>
      expect(controller.getSnapshot().failure?.code).toBe("company_access_denied"),
    );
    previous.reject(new Error("Private late response"));
    await Promise.resolve();
    expect(controller.getSnapshot().grant).toBeNull();
    expect(controller.getSnapshot().failure?.code).toBe("company_access_denied");
    controller.deactivate();
  });
});
