import { describe, expect, it, vi } from "vitest";
import {
  retentionPolicy,
  retentionPolicyId,
} from "../../../../../tests/fixtures/document-retention";
import { documentCompanyId, documentId } from "../../../../../tests/fixtures/documents";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toDocumentRetentionPolicy } from "../../data/mappers/document-retention-mapper";
import type { DocumentRetentionPolicySelection } from "../../domain/entities/document-retention-policy";
import type { DocumentRetentionUseCases } from "../contracts/document-retention-use-cases";
import { DocumentRetentionEditorController } from "./document-retention-editor-controller";

const access = { companyId: documentCompanyId as CompanyId, permissions: ["documents.retention"] };
function pending<T>() {
  let resolve!: (value: Result<T>) => void, reject!: (reason: Error) => void;
  const promise = new Promise<Result<T>>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
async function setup(configured = true) {
  const selection: DocumentRetentionPolicySelection = {
    companyId: access.companyId,
    classification: "PERSONAL",
    policy: configured ? toDocumentRetentionPolicy(retentionPolicy(), access.companyId) : null,
  };
  const review = vi
    .fn<DocumentRetentionUseCases["reviewPolicy"]["execute"]>()
    .mockResolvedValue(success(selection));
  const receipt = { id: retentionPolicyId, version: configured ? 1 : 0 };
  const save = vi
    .fn<DocumentRetentionUseCases["savePolicy"]["execute"]>()
    .mockResolvedValue(success(receipt));
  const identifier = vi.fn(() => documentId(80));
  const controller = new DocumentRetentionEditorController(
    { reviewPolicy: { execute: review }, savePolicy: { execute: save } },
    access,
    "PERSONAL",
    identifier,
  );
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
  return { controller, review, save, identifier, receipt, selection };
}

describe("retention policy editor ownership", () => {
  it.each([true, false])("owns one immutable command for configured=%s", async (configured) => {
    const f = await setup(configured),
      held = pending<MutationReceipt>();
    const fields = { retentionDays: "730", reason: "Reviewed duration" };
    f.save.mockReturnValueOnce(held.promise);
    f.identifier.mockReturnValueOnce(documentId(80)).mockReturnValueOnce(retentionPolicyId);
    f.controller.activate();
    const first = f.controller.save(fields);
    fields.retentionDays = "1";
    await f.controller.save({ retentionDays: "2", reason: "Replacement" });
    await f.controller.retry();
    await f.controller.refresh();
    expect(f.save).toHaveBeenCalledTimes(1);
    expect(f.review).toHaveBeenCalledTimes(1);
    expect(f.identifier).toHaveBeenCalledTimes(configured ? 1 : 2);
    const call = f.save.mock.calls[0];
    expect(call?.slice(1, 5)).toEqual([
      documentId(80),
      f.selection,
      retentionPolicyId,
      { retentionDays: "730", reason: "Reviewed duration" },
    ]);
    expect(call?.[2]).not.toBe(f.selection);
    expect(Object.isFrozen(call?.[2])).toBe(true);
    expect(Object.isFrozen(call?.[4])).toBe(true);
    if (configured) expect(Object.isFrozen(call?.[2].policy)).toBe(true);
    held.resolve(success(f.receipt));
    await first;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      selection: null,
      operationId: null,
      receipt: f.receipt,
    });
    f.controller.deactivate();
  });

  it.each(["stale_version", "mfa_required", "access_denied"])(
    "pins an uncertain command after a later %s rejection",
    async (code) => {
      const f = await setup();
      f.save
        .mockResolvedValueOnce(failed("network_unavailable"))
        .mockResolvedValueOnce(failed(code));
      await f.controller.save({ retentionDays: "730", reason: "Original review" });
      await f.controller.refresh();
      await f.controller.save({ retentionDays: "1", reason: "Changed reason" });
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        operationId: documentId(80),
        failure: { code },
      });
      if (code !== "stale_version") expect(f.controller.getSnapshot().selection).toBeNull();
      await f.controller.retry();
      expect(f.controller.getSnapshot().stage).toBe("saved");
      expect(f.review).toHaveBeenCalledTimes(1);
      expect(f.identifier).toHaveBeenCalledTimes(1);
      for (const call of f.save.mock.calls) {
        expect(call.slice(0, 5)).toEqual(f.save.mock.calls[0]?.slice(0, 5));
        expect(call[2]).toBe(f.save.mock.calls[0]?.[2]);
        expect(call[4]).toBe(f.save.mock.calls[0]?.[4]);
      }
      f.controller.deactivate();
    },
  );

  it("requires fresh review after a definite conflict before another operation", async () => {
    const f = await setup();
    f.save.mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save({ retentionDays: "30", reason: "Old version" });
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "conflict", operationId: null });
    await f.controller.save({ retentionDays: "30", reason: "Ignored" });
    await f.controller.retry();
    expect(f.save).toHaveBeenCalledTimes(1);
    f.review.mockResolvedValueOnce(
      success({
        ...f.selection,
        policy: toDocumentRetentionPolicy(retentionPolicy(3), access.companyId),
      }),
    );
    f.identifier.mockReturnValueOnce(documentId(81));
    await f.controller.refresh();
    await f.controller.save({ retentionDays: "45", reason: "Current review" });
    expect(f.save.mock.calls[1]?.[1]).toBe(documentId(81));
    expect(f.save.mock.calls[1]?.[2].policy?.version).toBe(3);
    expect(f.save.mock.calls[1]?.[3]).toBe(retentionPolicyId);
    f.controller.deactivate();
  });

  it("hides denied metadata throughout pending and failed recovery", async () => {
    const f = await setup(),
      held = pending<DocumentRetentionPolicySelection>();
    f.save.mockResolvedValueOnce(failed("access_denied"));
    await f.controller.save({ retentionDays: "365", reason: "Review" });
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", selection: null });
    f.review.mockReturnValueOnce(held.promise);
    const retry = f.controller.refresh();
    expect(f.controller.getSnapshot().selection).toBeNull();
    held.resolve(failed("network_unavailable"));
    await retry;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", selection: null });
    await f.controller.refresh();
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "editing", selection: f.selection });
    expect(f.save).toHaveBeenCalledTimes(1);
    f.controller.deactivate();
  });

  it.each(["result", "failure", "exception"])(
    "ignores a late %s after cancellation during submission",
    async (completion) => {
      const f = await setup(),
        held = pending<MutationReceipt>();
      f.save.mockReturnValueOnce(held.promise);
      const submission = f.controller.save({ retentionDays: "365", reason: "Review" });
      const listener = vi.fn(),
        unsubscribe = f.controller.subscribe(listener);
      unsubscribe();
      f.controller.deactivate();
      expect(f.save.mock.calls[0]?.[5].aborted).toBe(true);
      if (completion === "exception") held.reject(new Error("Private technical failure"));
      else held.resolve(completion === "result" ? success(f.receipt) : failed("access_denied"));
      await submission;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "loading",
        receipt: null,
        selection: null,
        failure: null,
      });
      expect(listener).not.toHaveBeenCalled();
      await f.controller.retry();
      expect(f.save).toHaveBeenCalledTimes(1);
    },
  );

  it("cancels replaced reads and rejects their late result after reactivation", async () => {
    const f = await setup(),
      held = pending<DocumentRetentionPolicySelection>();
    f.review.mockReturnValueOnce(held.promise);
    const reading = f.controller.refresh();
    f.controller.deactivate();
    expect(f.review.mock.calls[1]?.[2].aborted).toBe(true);
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    held.resolve(failed("access_denied"));
    await reading;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "editing",
      selection: f.selection,
      failure: null,
    });
    f.controller.deactivate();
  });

  it("keeps the review editable when an identifier cannot be allocated", async () => {
    const f = await setup();
    f.identifier.mockImplementationOnce(() => {
      throw new Error("Private random source error");
    });
    await f.controller.save({ retentionDays: "365", reason: "Review" });
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "editing",
      operationId: null,
      failure: { code: "unexpected_error" },
    });
    expect(f.save).not.toHaveBeenCalled();
    await f.controller.save({ retentionDays: "365", reason: "Review" });
    expect(f.controller.getSnapshot().stage).toBe("saved");
    f.controller.deactivate();
  });
});
