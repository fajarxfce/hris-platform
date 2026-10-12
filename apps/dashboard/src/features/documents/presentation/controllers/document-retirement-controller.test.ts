import { describe, expect, it, vi } from "vitest";
import { documentRetirementReview } from "../../../../../tests/fixtures/document-retirement";
import {
  documentCompanyId,
  documentHeaderId,
  documentId,
  documentRevisionId,
} from "../../../../../tests/fixtures/documents";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toDocumentRetirementReview } from "../../data/mappers/document-retirement-mapper";
import type { DocumentRetirementReview } from "../../domain/entities/document-retirement";
import type { DocumentRetentionUseCases } from "../contracts/document-retention-use-cases";
import { DocumentRetirementController } from "./document-retirement-controller";

const access = { companyId: documentCompanyId as CompanyId, permissions: ["documents.retention"] };
function pending<T>() {
  let resolve!: (value: Result<T>) => void, reject!: (reason: Error) => void;
  const promise = new Promise<Result<T>>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
async function setup(blockingReason: string | null = null) {
  const snapshot = toDocumentRetirementReview(
    { ...documentRetirementReview(), blockingReason },
    access.companyId,
    documentHeaderId,
    documentRevisionId,
  );
  const review = vi
    .fn<DocumentRetentionUseCases["reviewRetirement"]["execute"]>()
    .mockResolvedValue(success(snapshot));
  const receipt = { id: documentRevisionId, version: snapshot.revisionVersion + 1 };
  const retire = vi
    .fn<DocumentRetentionUseCases["retireRevision"]["execute"]>()
    .mockResolvedValue(success(receipt));
  const identifier = vi.fn(() => documentId(80));
  const controller = new DocumentRetirementController(
    { reviewRetirement: { execute: review }, retireRevision: { execute: retire } },
    access,
    documentHeaderId,
    documentRevisionId,
    identifier,
  );
  controller.activate();
  await vi.waitFor(() =>
    expect(controller.getSnapshot().stage).toBe(blockingReason ? "blocked" : "reviewing"),
  );
  return { controller, review, retire, identifier, snapshot, receipt };
}
describe("retirement command ownership", () => {
  it("owns one immutable review and rejects duplicate confirmation, refresh and retry", async () => {
    const f = await setup(),
      held = pending<MutationReceipt>();
    f.retire.mockReturnValueOnce(held.promise);
    f.controller.activate();
    const first = f.controller.confirm("Original retirement");
    await f.controller.confirm("Duplicate");
    await f.controller.retry();
    await f.controller.refresh();
    expect(f.retire).toHaveBeenCalledTimes(1);
    expect(f.identifier).toHaveBeenCalledTimes(1);
    expect(f.review).toHaveBeenCalledTimes(1);
    const call = f.retire.mock.calls[0];
    expect(call?.slice(1, 4)).toEqual([documentId(80), f.snapshot, "Original retirement"]);
    expect(call?.[2]).not.toBe(f.snapshot);
    expect(Object.isFrozen(call?.[2])).toBe(true);
    expect(Object.isFrozen(call?.[2].retention)).toBe(true);
    expect(Object.isFrozen(call?.[2].retention.archive)).toBe(true);
    held.resolve(success(f.receipt));
    await first;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      review: null,
      receipt: f.receipt,
      operationId: null,
    });
    f.controller.deactivate();
  });
  it.each(["stale_version", "document_legal_hold", "mfa_required", "document_revision_not_found"])(
    "keeps an unknown outcome pinned through a subsequent %s",
    async (code) => {
      const f = await setup();
      f.retire
        .mockResolvedValueOnce(failed("network_unavailable"))
        .mockResolvedValueOnce(failed(code));
      await f.controller.confirm("Original retirement");
      await f.controller.refresh();
      await f.controller.confirm("Changed retirement");
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        failure: { code },
        operationId: documentId(80),
      });
      if (code === "mfa_required" || code === "document_revision_not_found")
        expect(f.controller.getSnapshot().review).toBeNull();
      await f.controller.retry();
      expect(f.controller.getSnapshot().stage).toBe("saved");
      for (const call of f.retire.mock.calls) {
        expect(call.slice(0, 4)).toEqual(f.retire.mock.calls[0]?.slice(0, 4));
        expect(call[2]).toBe(f.retire.mock.calls[0]?.[2]);
      }
      expect(f.identifier).toHaveBeenCalledTimes(1);
      expect(f.review).toHaveBeenCalledTimes(1);
      f.controller.deactivate();
    },
  );
  it("does not allocate an operation for a blocked review or retry it automatically", async () => {
    const f = await setup("document_future_blocker");
    await f.controller.confirm("Should not submit");
    await f.controller.retry();
    expect(f.identifier).not.toHaveBeenCalled();
    expect(f.retire).not.toHaveBeenCalled();
    f.review.mockResolvedValueOnce(success({ ...f.snapshot, blockingReason: null }));
    await f.controller.refresh();
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
    await f.controller.confirm("Reviewed after refresh");
    expect(f.controller.getSnapshot().stage).toBe("saved");
    f.controller.deactivate();
  });
  it("a definite conflict requires fresh versions and a new explicit confirmation", async () => {
    const f = await setup();
    f.retire.mockResolvedValueOnce(failed("document_legal_hold"));
    await f.controller.confirm("Original retirement");
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "conflict", operationId: null });
    await f.controller.retry();
    await f.controller.confirm("Ignored");
    expect(f.retire).toHaveBeenCalledTimes(1);
    f.review.mockResolvedValueOnce(
      success({ ...f.snapshot, retention: { ...f.snapshot.retention, version: 2 } }),
    );
    f.identifier.mockReturnValueOnce(documentId(81));
    await f.controller.refresh();
    await f.controller.confirm("Reviewed after hold removal");
    expect(f.retire.mock.calls[1]?.[1]).toBe(documentId(81));
    expect(f.retire.mock.calls[1]?.[2].retention.version).toBe(2);
    f.controller.deactivate();
  });
  it("hides a denied review while reauthorization is pending or fails", async () => {
    const f = await setup(),
      held = pending<DocumentRetirementReview>();
    f.retire.mockResolvedValueOnce(failed("access_denied"));
    await f.controller.confirm("Review");
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", review: null });
    f.review.mockReturnValueOnce(held.promise);
    const retry = f.controller.refresh();
    expect(f.controller.getSnapshot().review).toBeNull();
    held.resolve(failed("network_unavailable"));
    await retry;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", review: null });
    await f.controller.refresh();
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
    expect(f.retire).toHaveBeenCalledTimes(1);
    f.controller.deactivate();
  });
  it.each(["result", "failure", "exception"])(
    "ignores a late %s after disposal",
    async (completion) => {
      const f = await setup(),
        held = pending<MutationReceipt>();
      f.retire.mockReturnValueOnce(held.promise);
      const submission = f.controller.confirm("Retire reviewed revision");
      const listener = vi.fn(),
        unsubscribe = f.controller.subscribe(listener);
      unsubscribe();
      f.controller.deactivate();
      expect(f.retire.mock.calls[0]?.[4].aborted).toBe(true);
      if (completion === "exception") held.reject(new Error("Private diagnostic"));
      else held.resolve(completion === "result" ? success(f.receipt) : failed("access_denied"));
      await submission;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "loading",
        review: null,
        failure: null,
        receipt: null,
      });
      expect(listener).not.toHaveBeenCalled();
    },
  );
  it("rejects an obsolete review after reactivation", async () => {
    const f = await setup(),
      held = pending<DocumentRetirementReview>();
    f.review.mockReturnValueOnce(held.promise);
    const oldRead = f.controller.refresh();
    f.controller.deactivate();
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    expect(f.review.mock.calls[1]?.[3].aborted).toBe(true);
    held.resolve(failed("document_revision_not_found"));
    await oldRead;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "reviewing",
      failure: null,
      review: f.snapshot,
    });
    f.controller.deactivate();
  });
  it("keeps its review if operation allocation fails", async () => {
    const f = await setup();
    f.identifier.mockImplementationOnce(() => {
      throw new Error("Random provider unavailable");
    });
    await f.controller.confirm("Review");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "reviewing",
      operationId: null,
      failure: { code: "unexpected_error" },
    });
    expect(f.retire).not.toHaveBeenCalled();
    await f.controller.confirm("Review");
    expect(f.controller.getSnapshot().stage).toBe("saved");
    f.controller.deactivate();
  });
});
