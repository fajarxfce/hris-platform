import { describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { ApprovalAssigneePage } from "../../domain/entities/approval-assignee";
import type {
  ApprovalTemplate,
  ApprovalTemplateId,
  ApprovalTemplatePage,
} from "../../domain/entities/approval-template";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { ApprovalAssigneePickerController } from "./approval-assignee-picker-controller";
import { ApprovalTemplateController } from "./approval-template-controller";
import { ApprovalTemplatesController } from "./approval-templates-controller";

const access = {
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  permissions: ["approvals.manage"],
};
const template: ApprovalTemplate = {
  id: "50000000-0000-4000-8000-000000000001" as ApprovalTemplateId,
  companyId: access.companyId,
  name: "Leave",
  kind: "LEAVE",
  active: true,
  version: 1,
  revision: 0,
  effectiveFrom: "2026-01-01",
  category: null,
  minimumAmount: "0",
  stages: [{ assignment: "MANAGER", accountIds: [], permission: null }],
};
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
describe("approval administration reader ownership", () => {
  it("cancels and clears an obsolete catalog, including late failures", async () => {
    const held = deferred<Result<ApprovalTemplatePage>>();
    const load = vi
      .fn<ApprovalsUseCases["loadTemplates"]["execute"]>()
      .mockReturnValueOnce(held.promise)
      .mockResolvedValue(success({ items: [template], nextCursor: null }));
    const controller = new ApprovalTemplatesController({ execute: load }, access, {
      kind: "LEAVE",
      asOf: "2026-10-01",
      after: null,
    });
    controller.activate();
    controller.activate();
    expect(load).toHaveBeenCalledOnce();
    const signal = load.mock.lastCall?.[2];
    await controller.refresh();
    expect(signal?.aborted).toBe(true);
    held.reject(new Error("Late obsolete failure"));
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    load.mockResolvedValueOnce(failed("company_access_denied"));
    await controller.refresh();
    expect(controller.getSnapshot()).toMatchObject({ stage: "unavailable", page: null });
    controller.deactivate();
    await controller.refresh();
    expect(load).toHaveBeenCalledTimes(3);
  });
  it("pins the requested revision and rejects a result after disposal and reactivation", async () => {
    const held = deferred<Result<ApprovalTemplate>>();
    const load = vi
      .fn<ApprovalsUseCases["loadTemplate"]["execute"]>()
      .mockReturnValueOnce(held.promise)
      .mockResolvedValueOnce(failed("approval_template_not_found"));
    const controller = new ApprovalTemplateController({ execute: load }, access, template.id, "0");
    controller.activate();
    expect(load.mock.lastCall?.slice(0, 3)).toEqual([access, template.id, "0"]);
    const signal = load.mock.lastCall?.[3];
    controller.deactivate();
    controller.activate();
    expect(signal?.aborted).toBe(true);
    held.resolve(success(template));
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("unavailable"));
    expect(controller.getSnapshot().template).toBeNull();
    controller.deactivate();
  });
  it("owns picker queries, uses bounded pages and ignores results from a closed dialog", async () => {
    const account = {
      id: "20000000-0000-4000-8000-000000000001" as AccountId,
      displayName: "Reviewer",
    };
    const held = deferred<Result<ApprovalAssigneePage>>();
    const last = deferred<Result<ApprovalAssigneePage>>();
    const load = vi
      .fn<ApprovalsUseCases["loadAssignees"]["execute"]>()
      .mockReturnValueOnce(held.promise)
      .mockResolvedValueOnce(success({ items: [account], nextCursor: account.id }))
      .mockReturnValueOnce(last.promise);
    const controller = new ApprovalAssigneePickerController({ execute: load }, access, "LEAVE");
    controller.activate();
    const obsoleteSignal = load.mock.lastCall?.[2];
    await controller.search("Reviewer");
    expect(obsoleteSignal?.aborted).toBe(true);
    held.resolve(success({ items: [{ ...account, displayName: "Obsolete" }], nextCursor: null }));
    await vi.waitFor(() =>
      expect(controller.getSnapshot().page?.items[0]?.displayName).toBe("Reviewer"),
    );
    const next = controller.nextPage();
    expect(load.mock.lastCall?.[1]).toEqual({
      kind: "LEAVE",
      query: "Reviewer",
      after: account.id,
    });
    expect(controller.getSnapshot().page).toBeNull();
    const pendingSignal = load.mock.lastCall?.[2];
    controller.deactivate();
    expect(pendingSignal?.aborted).toBe(true);
    last.reject(new Error("Late closed picker failure"));
    await next;
    expect(controller.getSnapshot()).toMatchObject({
      page: null,
      failure: null,
      query: "",
      after: null,
    });
    await controller.nextPage();
    await controller.search("Ignored");
    expect(load).toHaveBeenCalledTimes(3);
  });
});
