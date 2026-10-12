import { describe, expect, it, vi } from "vitest";
import {
  documentCompanyId,
  documentEmployeeId,
  documentId,
  documentRevision,
} from "../../../../../tests/fixtures/documents";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toDocumentRevision } from "../../data/mappers/document-revision-mapper";
import type { DocumentRevision } from "../../domain/entities/document-revision";
import type {
  DocumentUploadFile,
  DocumentUploadReview,
} from "../../domain/entities/document-upload";
import type { DocumentsUseCases } from "../contracts/documents-use-cases";
import { DocumentUploadController } from "./document-upload-controller";

const access = {
  companyId: documentCompanyId as CompanyId,
  permissions: [
    "documents.read",
    "people.profile.read",
    "documents.manage",
    "people.profile.manage",
  ],
};
const review: DocumentUploadReview = {
  kind: "new",
  employee: {
    id: documentEmployeeId,
    companyId: access.companyId,
    employeeNumber: "EMP-001",
    name: "Employee",
    status: "ACTIVE",
    version: 0,
  },
};
const file: DocumentUploadFile = {
  reference: {
    id: "selected-file",
    name: "record.pdf",
    byteLength: 17,
    mediaType: "application/pdf",
  },
  mediaType: "application/pdf",
  sha256: "a".repeat(64),
};
const fields = {
  title: "Employee record",
  classification: "PERSONAL" as const,
  reason: "Signed record",
};
function deferred<T>() {
  let resolve!: (value: T) => void;
  return {
    promise: new Promise<T>((done) => {
      resolve = done;
    }),
    resolve: (value: T) => resolve(value),
  };
}
async function setup() {
  let next = 100;
  const actions = {
    reviewUpload: {
      execute: vi
        .fn<DocumentsUseCases["reviewUpload"]["execute"]>()
        .mockResolvedValue(success(review)),
    },
    selectUploadFile: {
      execute: vi
        .fn<DocumentsUseCases["selectUploadFile"]["execute"]>()
        .mockResolvedValue(success(file)),
    },
    releaseUploadFile: { execute: vi.fn<DocumentsUseCases["releaseUploadFile"]["execute"]>() },
    uploadFile: {
      execute: vi
        .fn<DocumentsUseCases["uploadFile"]["execute"]>()
        .mockResolvedValue(failed("connection_failed")),
    },
  };
  const identifier = vi.fn(() => documentId(next++));
  const controller = new DocumentUploadController(
    actions,
    access,
    { kind: "new", employmentId: documentEmployeeId },
    identifier,
  );
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
  return { actions, identifier, controller };
}

describe("upload page ownership", () => {
  it("serializes confirmation and retains an immutable file and payload after ambiguous delivery", async () => {
    const f = await setup(),
      held = deferred<Result<DocumentRevision>>();
    try {
      await f.controller.selectFile();
      f.actions.uploadFile.execute.mockReturnValueOnce(held.promise);
      const mutable = { ...fields };
      const sent = f.controller.confirm(mutable);
      mutable.title = "Edited afterward";
      await f.controller.confirm(fields);
      await f.controller.selectFile();
      await f.controller.refresh();
      expect(f.actions.uploadFile.execute).toHaveBeenCalledTimes(1);
      expect(f.actions.reviewUpload.execute).toHaveBeenCalledTimes(1);
      held.resolve(failed("connection_failed"));
      await sent;
      const original = f.actions.uploadFile.execute.mock.calls[0]?.[1];
      expect(original?.start?.input.title).toBe(fields.title);
      expect(f.controller.getSnapshot().stage).toBe("paused");
      await f.controller.retry();
      expect(f.actions.uploadFile.execute.mock.calls[1]?.[1]).toBe(original);
      expect(f.identifier).toHaveBeenCalledTimes(4);
    } finally {
      f.controller.deactivate();
    }
    expect(f.actions.releaseUploadFile.execute).toHaveBeenCalledExactlyOnceWith(file.reference.id);
  });
  it("retains a previously uncertain command through access loss and failed recovery", async () => {
    const f = await setup();
    try {
      await f.controller.selectFile();
      await f.controller.confirm(fields);
      const original = f.actions.uploadFile.execute.mock.calls[0]?.[1];
      f.actions.uploadFile.execute.mockResolvedValueOnce(failed("mfa_required"));
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "paused",
        review: null,
        file: null,
      });
      await f.controller.refresh();
      f.actions.uploadFile.execute.mockResolvedValueOnce(failed("stale_version"));
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "paused",
        review: null,
        file: null,
      });
      expect(f.actions.uploadFile.execute.mock.calls.every(([, plan]) => plan === original)).toBe(
        true,
      );
      expect(f.actions.reviewUpload.execute).toHaveBeenCalledTimes(1);
      expect(f.actions.releaseUploadFile.execute).not.toHaveBeenCalled();
    } finally {
      f.controller.deactivate();
    }
  });
  it("a first rejected metadata command allows explicit fresh review, but a known metadata receipt stays pinned", async () => {
    const f = await setup();
    try {
      await f.controller.selectFile();
      f.actions.uploadFile.execute.mockResolvedValueOnce(failed("stale_version"));
      await f.controller.confirm(fields);
      expect(f.controller.getSnapshot().stage).toBe("conflict");
      await f.controller.refresh();
      await f.controller.selectFile();
      f.actions.uploadFile.execute.mockImplementationOnce(async (_access, _plan, progress) => {
        progress(0);
        return failed("mfa_required");
      });
      await f.controller.confirm(fields);
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "paused",
        uploadedBytes: 0,
        review: null,
      });
      await f.controller.refresh();
      expect(f.actions.reviewUpload.execute).toHaveBeenCalledTimes(2);
    } finally {
      f.controller.deactivate();
    }
  });
  it("pause aborts the current operation and ignores its late progress and completion", async () => {
    const f = await setup(),
      held = deferred<Result<DocumentRevision>>();
    try {
      await f.controller.selectFile();
      f.actions.uploadFile.execute.mockReturnValueOnce(held.promise);
      const sent = f.controller.confirm(fields);
      const call = f.actions.uploadFile.execute.mock.calls[0];
      if (!call) throw new Error("Missing submitted operation");
      call[2](5);
      f.controller.pause();
      expect(call[3].aborted).toBe(true);
      call[2](17);
      held.resolve(success(toDocumentRevision(documentRevision(), access.companyId)));
      await sent;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "paused",
        uploadedBytes: 5,
        completion: null,
      });
      await f.controller.retry();
      expect(f.actions.uploadFile.execute.mock.calls[1]?.[1]).toBe(call[1]);
    } finally {
      f.controller.deactivate();
    }
  });
  it("workspace suspension cancels delivery and requires explicit retry after equivalent access returns", async () => {
    const f = await setup(),
      held = deferred<Result<DocumentRevision>>();
    try {
      await f.controller.selectFile();
      f.actions.uploadFile.execute.mockReturnValueOnce(held.promise);
      const sent = f.controller.confirm(fields);
      const active = f.actions.uploadFile.execute.mock.calls[0]?.[3];
      f.controller.setVisible(false);
      expect(active?.aborted).toBe(true);
      await f.controller.retry();
      f.controller.setVisible(true);
      expect(f.actions.uploadFile.execute).toHaveBeenCalledTimes(1);
      held.resolve(failed("connection_failed"));
      await sent;
      await f.controller.retry();
      expect(f.actions.uploadFile.execute).toHaveBeenCalledTimes(2);
      expect(f.actions.releaseUploadFile.execute).not.toHaveBeenCalled();
    } finally {
      f.controller.deactivate();
    }
  });
  it("departing the scope releases both the selected file and any late picker result", async () => {
    const f = await setup(),
      held = deferred<Result<DocumentUploadFile | null>>();
    await f.controller.selectFile();
    f.actions.selectUploadFile.execute.mockReturnValueOnce(held.promise);
    const select = f.controller.selectFile();
    f.controller.deactivate();
    expect(f.actions.selectUploadFile.execute.mock.calls[1]?.[2].aborted).toBe(true);
    held.resolve(success({ ...file, reference: { ...file.reference, id: "late-file" } }));
    await select;
    expect(f.controller.getSnapshot()).toMatchObject({
      review: null,
      file: null,
      completion: null,
    });
    expect(f.actions.releaseUploadFile.execute.mock.calls).toEqual([
      [file.reference.id],
      ["late-file"],
    ]);
    await f.controller.confirm(fields);
    expect(f.actions.uploadFile.execute).not.toHaveBeenCalled();
  });
  it("selection cancellation preserves the previous file and successful replacement releases it", async () => {
    const f = await setup();
    try {
      await f.controller.selectFile();
      f.actions.selectUploadFile.execute.mockResolvedValueOnce(success(null));
      await f.controller.selectFile();
      expect(f.controller.getSnapshot().file?.name).toBe(file.reference.name);
      expect(f.actions.releaseUploadFile.execute).not.toHaveBeenCalled();
      f.actions.selectUploadFile.execute.mockResolvedValueOnce(
        success({
          ...file,
          reference: { ...file.reference, id: "replacement", name: "other.pdf" },
        }),
      );
      await f.controller.selectFile();
      expect(f.actions.releaseUploadFile.execute).toHaveBeenCalledExactlyOnceWith(
        file.reference.id,
      );
      expect(f.controller.getSnapshot().file?.name).toBe("other.pdf");
    } finally {
      f.controller.deactivate();
    }
    expect(f.actions.releaseUploadFile.execute.mock.calls).toEqual([
      [file.reference.id],
      ["replacement"],
    ]);
  });
  it("successful delivery releases file ownership once and cannot start another command on the page", async () => {
    const f = await setup();
    await f.controller.selectFile();
    f.actions.uploadFile.execute.mockResolvedValueOnce(
      success(toDocumentRevision(documentRevision(), access.companyId)),
    );
    await f.controller.confirm(fields);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "complete", file: null });
    await f.controller.confirm(fields);
    await f.controller.retry();
    await f.controller.selectFile();
    expect(f.actions.uploadFile.execute).toHaveBeenCalledTimes(1);
    f.controller.deactivate();
    expect(f.actions.releaseUploadFile.execute).toHaveBeenCalledExactlyOnceWith(file.reference.id);
  });
  it("stops before transport on invalid fields or identifier failure", async () => {
    const f = await setup();
    try {
      await f.controller.confirm(fields);
      expect(f.controller.getSnapshot().failure?.code).toBe("document_file_required");
      await f.controller.selectFile();
      await f.controller.confirm({ ...fields, reason: " " });
      expect(f.controller.getSnapshot().stage).toBe("editing");
      f.identifier.mockImplementationOnce(() => {
        throw new Error("PRIVATE random source failure");
      });
      await f.controller.confirm(fields);
      expect(f.controller.getSnapshot().failure?.code).toBe("unexpected_error");
      expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
      expect(f.actions.uploadFile.execute).not.toHaveBeenCalled();
    } finally {
      f.controller.deactivate();
    }
  });
  it("late failed reviews cannot overwrite a replacement scope", async () => {
    const f = await setup(),
      held = deferred<Result<DocumentUploadReview>>();
    f.actions.reviewUpload.execute.mockReturnValueOnce(held.promise);
    const refresh = f.controller.refresh();
    f.controller.deactivate();
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    held.resolve(failed("company_access_denied"));
    await refresh;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "editing", review, failure: null });
    f.controller.deactivate();
  });
});
