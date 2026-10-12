import { describe, expect, it, vi } from "vitest";
import {
  archivedDocumentRetention,
  documentRetention,
} from "../../../../../tests/fixtures/document-retention";
import {
  documentCompanyId,
  documentHeaderId,
  documentId,
} from "../../../../../tests/fixtures/documents";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toDocumentRetention } from "../../data/mappers/document-retention-state-mapper";
import type {
  DocumentRetention,
  DocumentRetentionIntent,
} from "../../domain/entities/document-retention";
import type { DocumentRetentionUseCases } from "../contracts/document-retention-use-cases";
import { DocumentRetentionChangeController } from "./document-retention-change-controller";

const access = { companyId: documentCompanyId as CompanyId, permissions: ["documents.retention"] };
function pending<T>() {
  let resolve!: (value: Result<T>) => void, reject!: (reason: Error) => void;
  const promise = new Promise<Result<T>>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
async function setup(intent: DocumentRetentionIntent = "archive") {
  const raw = intent === "archive" ? documentRetention() : archivedDocumentRetention(1);
  if (intent === "release-hold") raw.legalHold = true;
  const snapshot = toDocumentRetention(raw, access.companyId, documentHeaderId);
  const review = vi
    .fn<DocumentRetentionUseCases["reviewChange"]["execute"]>()
    .mockResolvedValue(success(snapshot));
  const receipt = { id: documentHeaderId, version: (snapshot.version ?? -1) + 1 };
  const change = vi
    .fn<DocumentRetentionUseCases["change"]["execute"]>()
    .mockResolvedValue(success(receipt));
  const identifier = vi.fn(() => documentId(80));
  const controller = new DocumentRetentionChangeController(
    { reviewChange: { execute: review }, change: { execute: change } },
    access,
    documentHeaderId,
    intent,
    identifier,
  );
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("reviewing"));
  return { controller, review, change, identifier, snapshot, receipt };
}
describe("document retention command ownership", () => {
  it.each(["archive", "restore", "hold", "release-hold"] as const)(
    "owns one %s command with an immutable review",
    async (intent) => {
      const f = await setup(intent),
        held = pending<MutationReceipt>();
      f.change.mockReturnValueOnce(held.promise);
      f.controller.activate();
      const first = f.controller.confirm("Reviewed lifecycle action");
      await f.controller.confirm("Duplicate action");
      await f.controller.retry();
      await f.controller.refresh();
      expect(f.change).toHaveBeenCalledTimes(1);
      expect(f.identifier).toHaveBeenCalledTimes(1);
      expect(f.review).toHaveBeenCalledTimes(1);
      const call = f.change.mock.calls[0];
      expect(call?.slice(1, 5)).toEqual([
        documentId(80),
        f.snapshot,
        intent,
        "Reviewed lifecycle action",
      ]);
      expect(call?.[2]).not.toBe(f.snapshot);
      expect(Object.isFrozen(call?.[2])).toBe(true);
      if (f.snapshot.archive) expect(Object.isFrozen(call?.[2].archive)).toBe(true);
      held.resolve(success(f.receipt));
      await first;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "saved",
        review: null,
        operationId: null,
        receipt: f.receipt,
      });
      f.controller.deactivate();
    },
  );
  it.each(["stale_version", "mfa_required", "document_not_found"])(
    "keeps an uncertain command through a later %s rejection",
    async (code) => {
      const f = await setup("hold");
      f.change
        .mockResolvedValueOnce(failed("network_unavailable"))
        .mockResolvedValueOnce(failed(code));
      await f.controller.confirm("Original hold");
      await f.controller.refresh();
      await f.controller.confirm("Different hold");
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        failure: { code },
        operationId: documentId(80),
      });
      if (code !== "stale_version") expect(f.controller.getSnapshot().review).toBeNull();
      await f.controller.retry();
      expect(f.controller.getSnapshot().stage).toBe("saved");
      for (const call of f.change.mock.calls) {
        expect(call.slice(0, 5)).toEqual(f.change.mock.calls[0]?.slice(0, 5));
        expect(call[2]).toBe(f.change.mock.calls[0]?.[2]);
      }
      expect(f.identifier).toHaveBeenCalledTimes(1);
      expect(f.review).toHaveBeenCalledTimes(1);
      f.controller.deactivate();
    },
  );
  it("requires another review after an upload prevents archiving and does not retry by itself", async () => {
    const f = await setup();
    f.change.mockResolvedValueOnce(failed("document_upload_active"));
    await f.controller.confirm("Archive old record");
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "conflict", operationId: null });
    await f.controller.retry();
    await f.controller.confirm("Ignored duplicate");
    expect(f.change).toHaveBeenCalledTimes(1);
    f.review.mockResolvedValueOnce(success({ ...f.snapshot, version: 2 }));
    f.identifier.mockReturnValueOnce(documentId(81));
    await f.controller.refresh();
    await f.controller.confirm("Archive reviewed record");
    expect(f.change.mock.calls[1]?.[1]).toBe(documentId(81));
    expect(f.change.mock.calls[1]?.[2].version).toBe(2);
    f.controller.deactivate();
  });
  it("hides denied review during a pending and failed recovery", async () => {
    const f = await setup(),
      held = pending<DocumentRetention>();
    f.change.mockResolvedValueOnce(failed("access_denied"));
    await f.controller.confirm("Archive reviewed record");
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", review: null });
    f.review.mockReturnValueOnce(held.promise);
    const retry = f.controller.refresh();
    expect(f.controller.getSnapshot().review).toBeNull();
    held.resolve(failed("network_unavailable"));
    await retry;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", review: null });
    await f.controller.refresh();
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
    expect(f.change).toHaveBeenCalledTimes(1);
    f.controller.deactivate();
  });
  it.each(["result", "failure", "exception"])(
    "ignores a late %s after command cancellation",
    async (completion) => {
      const f = await setup(),
        held = pending<MutationReceipt>();
      f.change.mockReturnValueOnce(held.promise);
      const submission = f.controller.confirm("Archive selection");
      const listener = vi.fn(),
        unsubscribe = f.controller.subscribe(listener);
      unsubscribe();
      f.controller.deactivate();
      expect(f.change.mock.calls[0]?.[5].aborted).toBe(true);
      if (completion === "exception") held.reject(new Error("Private error"));
      else held.resolve(completion === "result" ? success(f.receipt) : failed("access_denied"));
      await submission;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "loading",
        review: null,
        receipt: null,
        failure: null,
      });
      expect(listener).not.toHaveBeenCalled();
    },
  );
  it("rejects a late obsolete review after owner reactivation", async () => {
    const f = await setup(),
      held = pending<DocumentRetention>();
    f.review.mockReturnValueOnce(held.promise);
    const reading = f.controller.refresh();
    f.controller.deactivate();
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    expect(f.review.mock.calls[1]?.[3].aborted).toBe(true);
    held.resolve(failed("document_not_found"));
    await reading;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "reviewing",
      failure: null,
      review: f.snapshot,
    });
    f.controller.deactivate();
  });
  it("handles operation allocation failure without losing its review", async () => {
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
    expect(f.change).not.toHaveBeenCalled();
    await f.controller.confirm("Review");
    expect(f.controller.getSnapshot().stage).toBe("saved");
    f.controller.deactivate();
  });
});
