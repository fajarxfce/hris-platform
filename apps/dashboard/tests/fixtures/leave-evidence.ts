import { createHash } from "node:crypto";

/** Fictional bytes used only by owned browser and data-boundary fixtures. */
export function leaveEvidence() {
  const bytes = Buffer.from("%PDF-1.7\nFictional leave evidence.\n%%EOF\n");
  const attachment = {
    documentId: "b8000000-0000-4000-8000-000000000001",
    revisionId: "b9000000-0000-4000-8000-000000000001",
    fileName: "leave-evidence.pdf",
    mediaType: "application/pdf",
    size: bytes.byteLength,
    sha256: createHash("sha256").update(bytes).digest("hex"),
  };
  return { bytes, attachment, etag: `"${attachment.revisionId}-${attachment.sha256}"` };
}
