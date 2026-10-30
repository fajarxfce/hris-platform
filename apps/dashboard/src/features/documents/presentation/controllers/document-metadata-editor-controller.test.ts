import { describe, expect, it, vi } from "vitest";
import {
  documentMetadataFields,
  documentMetadataHeader,
} from "../../../../../tests/fixtures/document-metadata";
import {
  documentCompanyId,
  documentHeaderId,
  documentId,
} from "../../../../../tests/fixtures/documents";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toDocument } from "../../data/mappers/document-mapper";
import type { Document } from "../../domain/entities/document";
import type { DocumentMetadataUseCases } from "../contracts/document-metadata-use-cases";
import { DocumentMetadataEditorController } from "./document-metadata-editor-controller";

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
  let resolve!: (value: Result<T>) => void, reject!: (reason: Error) => void;
  const promise = new Promise<Result<T>>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
async function setup() {
  const selection = toDocument(documentMetadataHeader(), access.companyId);
  const review = vi
    .fn<DocumentMetadataUseCases["review"]["execute"]>()
    .mockResolvedValue(success(selection));
  const receipt = { id: documentHeaderId, version: 3 };
  const save = vi
    .fn<DocumentMetadataUseCases["save"]["execute"]>()
    .mockResolvedValue(success(receipt));
  const identifier = vi.fn(() => documentId(801));
  const controller = new DocumentMetadataEditorController(
    { review: { execute: review }, save: { execute: save } },
    access,
    documentHeaderId,
    identifier,
  );
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
  return { selection, review, receipt, save, identifier, controller };
}

describe("document metadata editor ownership", () => {
  it("owns one frozen command and ignores edits and refresh while its save is pending", async () => {
    const f = await setup(),
      held = pending<MutationReceipt>(),
      fields = { ...documentMetadataFields };
    f.save.mockReturnValueOnce(held.promise);
    const request = f.controller.save(fields);
    fields.identityNumber = "Changed";
    await f.controller.save(fields);
    await f.controller.refresh();
    await f.controller.retry();
    expect(f.save).toHaveBeenCalledTimes(1);
    expect(f.review).toHaveBeenCalledTimes(1);
    expect(f.identifier).toHaveBeenCalledTimes(1);
    const args = f.save.mock.calls[0];
    expect(args?.[3].identityNumber).toBe(documentMetadataFields.identityNumber);
    expect(Object.isFrozen(args?.[2].metadata)).toBe(true);
    expect(Object.isFrozen(args?.[3])).toBe(true);
    held.resolve(success(f.receipt));
    await request;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      selection: null,
      operationId: null,
      receipt: f.receipt,
    });
    f.controller.deactivate();
  });
  it.each(["stale_version", "mfa_required", "access_denied", "document_archived"])(
    "pins an uncertain request through later %s",
    async (code) => {
      const f = await setup();
      f.save
        .mockResolvedValueOnce(failed("network_unavailable"))
        .mockResolvedValueOnce(failed(code));
      await f.controller.save(documentMetadataFields);
      await f.controller.refresh();
      await f.controller.save({ ...documentMetadataFields, reason: "Changed" });
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        operationId: documentId(801),
        failure: { code },
      });
      if (code === "mfa_required" || code === "access_denied")
        expect(f.controller.getSnapshot().selection).toBeNull();
      await f.controller.retry();
      expect(f.controller.getSnapshot().stage).toBe("saved");
      for (const call of f.save.mock.calls) {
        expect(call.slice(0, 4)).toEqual(f.save.mock.calls[0]?.slice(0, 4));
        expect(call[2]).toBe(f.save.mock.calls[0]?.[2]);
        expect(call[3]).toBe(f.save.mock.calls[0]?.[3]);
      }
      expect(f.identifier).toHaveBeenCalledTimes(1);
      f.controller.deactivate();
    },
  );
  it("requires an explicit fresh review after a definite conflict", async () => {
    const f = await setup();
    f.save.mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(documentMetadataFields);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "conflict", operationId: null });
    await f.controller.save(documentMetadataFields);
    expect(f.save).toHaveBeenCalledTimes(1);
    f.review.mockResolvedValueOnce(
      success(toDocument(documentMetadataHeader(3), access.companyId)),
    );
    f.identifier.mockReturnValueOnce(documentId(802));
    await f.controller.refresh();
    await f.controller.save(documentMetadataFields);
    expect(f.save.mock.calls[1]?.[1]).toBe(documentId(802));
    expect(f.save.mock.calls[1]?.[2].version).toBe(5);
    f.controller.deactivate();
  });
  it("hides private details throughout denied refresh and failed recovery", async () => {
    const f = await setup(),
      held = pending<Document>();
    f.save.mockResolvedValueOnce(failed("access_denied"));
    await f.controller.save(documentMetadataFields);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", selection: null });
    f.review.mockReturnValueOnce(held.promise);
    const request = f.controller.refresh();
    expect(f.controller.getSnapshot().selection).toBeNull();
    held.resolve(failed("network_unavailable"));
    await request;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", selection: null });
    f.controller.deactivate();
  });
  it.each(["success", "failure", "exception"])(
    "discards late %s after disposal",
    async (completion) => {
      const f = await setup(),
        held = pending<MutationReceipt>();
      f.save.mockReturnValueOnce(held.promise);
      const request = f.controller.save(documentMetadataFields);
      const signal = f.save.mock.calls[0]?.[4];
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      if (completion === "exception") held.reject(new Error("Fixture transport stopped"));
      else
        held.resolve(completion === "success" ? success(f.receipt) : failed("network_unavailable"));
      await request;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "loading",
        selection: null,
        operationId: null,
        receipt: null,
      });
    },
  );
});
