import { describe, expect, it, vi } from "vitest";
import {
  documentCompanyId,
  documentHeaderId,
  documentId,
  documentRevision,
  documentRevisionId,
} from "../../../../../tests/fixtures/documents";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toDocumentRevision } from "../../data/mappers/document-revision-mapper";
import type { DocumentRevision } from "../../domain/entities/document-revision";
import type { DocumentRevisionIntent } from "../../domain/entities/document-revision-action";
import type { DocumentsUseCases } from "../contracts/documents-use-cases";
import { DocumentActionController } from "./document-action-controller";

const access = {
  companyId: documentCompanyId as CompanyId,
  permissions: [
    "documents.read",
    "people.profile.read",
    "documents.manage",
    "people.profile.manage",
  ],
};
function pending<T>() {
  let resolve!: (value: Result<T>) => void;
  const promise = new Promise<Result<T>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
async function setup(intent: DocumentRevisionIntent = "validate") {
  const revision = toDocumentRevision(
    {
      ...documentRevision(),
      status: "UPLOADING",
      validatedAt: null,
      validationJobId: null,
      validationAttempts: 0,
      availableActions: ["VALIDATE", "CANCEL_UPLOAD"],
    },
    access.companyId,
  );
  const receipt = { id: revision.id, version: revision.version + 1 };
  const review = vi
    .fn<DocumentsUseCases["reviewAction"]["execute"]>()
    .mockResolvedValue(success(revision));
  const validate = vi
    .fn<DocumentsUseCases["validate"]["execute"]>()
    .mockResolvedValue(success(receipt));
  const cancel = vi
    .fn<DocumentsUseCases["cancelUpload"]["execute"]>()
    .mockResolvedValue(success(receipt));
  const identifier = vi.fn(() => documentId(80));
  const controller = new DocumentActionController(
    {
      reviewAction: { execute: review },
      validate: { execute: validate },
      cancelUpload: { execute: cancel },
    },
    access,
    documentHeaderId,
    documentRevisionId,
    intent,
    identifier,
  );
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("reviewing"));
  return { controller, review, validate, cancel, identifier, revision, receipt };
}

describe("document action ownership", () => {
  it.each(["validate", "cancel"] as const)(
    "allows one %s command and owns its immutable review",
    async (intent) => {
      const { controller, validate, cancel, identifier, receipt, revision, review } =
        await setup(intent);
      const action = intent === "validate" ? validate : cancel,
        held = pending<MutationReceipt>();
      action.mockReturnValueOnce(held.promise);
      controller.activate();
      const first = controller.confirm("Inspect content");
      await controller.confirm("Different reason");
      await controller.retry();
      await controller.refresh();
      expect(action).toHaveBeenCalledTimes(1);
      expect(intent === "validate" ? cancel : validate).not.toHaveBeenCalled();
      expect(review).toHaveBeenCalledTimes(1);
      expect(identifier).toHaveBeenCalledTimes(1);
      const captured = action.mock.calls[0]?.[2];
      expect(captured).not.toBe(revision);
      expect(Object.isFrozen(captured)).toBe(true);
      expect(Object.isFrozen(captured?.availableActions)).toBe(true);
      held.resolve(success(receipt));
      await first;
      expect(controller.getSnapshot()).toMatchObject({
        stage: "saved",
        review: null,
        operationId: null,
        receipt,
      });
      controller.deactivate();
    },
  );

  it.each(["stale_version", "mfa_required", "document_access_denied"])(
    "keeps the original uncertain command after a later %s rejection",
    async (code) => {
      const { controller, validate, identifier, review } = await setup();
      validate
        .mockResolvedValueOnce(failed("network_unavailable"))
        .mockResolvedValueOnce(failed(code));
      await controller.confirm("Original reason");
      expect(controller.getSnapshot().stage).toBe("unconfirmed");
      await controller.refresh();
      await controller.confirm("Replacement reason");
      await controller.retry();
      expect(controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        operationId: documentId(80),
        failure: { code },
      });
      if (code !== "stale_version") expect(controller.getSnapshot().review).toBeNull();
      await controller.retry();
      expect(controller.getSnapshot().stage).toBe("saved");
      expect(review).toHaveBeenCalledTimes(1);
      expect(identifier).toHaveBeenCalledTimes(1);
      for (const call of validate.mock.calls) {
        expect(call.slice(0, 4)).toEqual(validate.mock.calls[0]?.slice(0, 4));
        expect(call[2]).toBe(validate.mock.calls[0]?.[2]);
      }
      controller.deactivate();
    },
  );

  it("requires a fresh review after a definite conflict before creating another command", async () => {
    const { controller, validate, identifier, review, revision } = await setup();
    validate.mockResolvedValueOnce(failed("stale_version"));
    await controller.confirm("Old reason");
    expect(controller.getSnapshot()).toMatchObject({ stage: "conflict", operationId: null });
    await controller.confirm("New reason");
    await controller.retry();
    expect(validate).toHaveBeenCalledTimes(1);
    review.mockResolvedValueOnce(success({ ...revision, version: revision.version + 2 }));
    identifier.mockReturnValueOnce(documentId(81));
    await controller.refresh();
    await controller.confirm("New reason");
    expect(validate.mock.calls[1]?.slice(1, 4)).toEqual([
      documentId(81),
      expect.objectContaining({ version: revision.version + 2 }),
      "New reason",
    ]);
    expect(controller.getSnapshot().stage).toBe("saved");
    controller.deactivate();
  });

  it("hides denied metadata throughout a pending or failed review", async () => {
    const { controller, validate, review } = await setup(),
      held = pending<DocumentRevision>();
    validate.mockResolvedValueOnce(failed("document_access_denied"));
    await controller.confirm("Reason");
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      operationId: null,
    });
    review.mockReturnValueOnce(held.promise);
    const next = controller.refresh();
    expect(controller.getSnapshot()).toMatchObject({ stage: "loading", review: null });
    held.resolve(failed("network_unavailable"));
    await next;
    expect(controller.getSnapshot()).toMatchObject({ stage: "unavailable", review: null });
    await controller.confirm("Blocked");
    expect(validate).toHaveBeenCalledTimes(1);
    await controller.refresh();
    expect(controller.getSnapshot().stage).toBe("reviewing");
    controller.deactivate();
  });

  it("rejects an obsolete review after refresh without reviving a revoked resource", async () => {
    const { controller, review, revision } = await setup(),
      held = pending<DocumentRevision>();
    review
      .mockReturnValueOnce(held.promise)
      .mockResolvedValueOnce(failed("document_revision_not_found"));
    const first = controller.refresh();
    await controller.refresh();
    expect(review.mock.calls[1]?.[4].aborted).toBe(true);
    held.resolve(success(revision));
    await first;
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      failure: { code: "document_revision_not_found" },
    });
    controller.deactivate();
  });

  it.each(["review", "command"] as const)(
    "disposal cancels a pending %s and rejects its late result",
    async (kind) => {
      const { controller, review, validate, receipt, revision } = await setup();
      const heldReview = pending<DocumentRevision>(),
        heldCommand = pending<MutationReceipt>();
      review.mockReturnValueOnce(heldReview.promise);
      validate.mockReturnValueOnce(heldCommand.promise);
      const changed = vi.fn(),
        unsubscribe = controller.subscribe(changed);
      const running = kind === "review" ? controller.refresh() : controller.confirm("Reason");
      const requestSignal =
        kind === "review" ? review.mock.lastCall?.[4] : validate.mock.lastCall?.[4];
      unsubscribe();
      controller.deactivate();
      expect(requestSignal?.aborted).toBe(true);
      const before = changed.mock.calls.length;
      heldReview.resolve(success(revision));
      heldCommand.resolve(success(receipt));
      await running;
      await controller.confirm("Late");
      await controller.retry();
      expect(controller.getSnapshot()).toMatchObject({
        stage: "loading",
        review: null,
        operationId: null,
      });
      expect(changed).toHaveBeenCalledTimes(before);
    },
  );

  it("does not claim an unconfirmed command when identifier creation fails", async () => {
    const { controller, identifier, validate } = await setup();
    identifier.mockImplementationOnce(() => {
      throw new Error("PRIVATE PLATFORM ERROR");
    });
    await controller.confirm("Reason");
    expect(controller.getSnapshot()).toMatchObject({
      stage: "reviewing",
      operationId: null,
      failure: { code: "unexpected_error" },
    });
    expect(validate).not.toHaveBeenCalled();
    await controller.confirm("Reason");
    expect(controller.getSnapshot().stage).toBe("saved");
    controller.deactivate();
  });

  it("treats unexpected command exceptions as uncertain without exposing technical text", async () => {
    const { controller, validate, identifier } = await setup();
    validate.mockRejectedValueOnce(new Error("PRIVATE TRANSPORT ERROR"));
    await controller.confirm("Reason");
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "unexpected_error", fields: {}, parameters: {} },
    });
    await controller.retry();
    expect(controller.getSnapshot().stage).toBe("saved");
    expect(identifier).toHaveBeenCalledTimes(1);
    controller.deactivate();
  });
});
