import type {
  DocumentDto,
  DocumentEmployeeDirectoryDto,
  DocumentRevisionDto,
} from "../../src/features/documents/data/models/document-dto";

export const documentId = (value: number): string =>
  `80000000-0000-4000-8000-${String(value).padStart(12, "0")}`;
export const documentCompanyId = documentId(1);
export const documentEmployeeId = documentId(11);
export const documentHeaderId = documentId(21);
export const documentRevisionId = documentId(31);
export const documentBytes = () => Uint8Array.from(new TextEncoder().encode("%PDF-1.4\n%%EOF\n"));
export function documentEmployeeDirectory(): DocumentEmployeeDirectoryDto {
  return {
    companyId: documentCompanyId,
    asOf: "2026-10-12",
    timezone: "Asia/Jakarta",
    query: "",
    employees: {
      items: [
        {
          id: documentEmployeeId,
          employeeNumber: "EMP-001",
          name: "North employee",
          status: "ACTIVE",
          version: 0,
        },
      ],
      nextCursor: null,
    },
  };
}
export function documentHeader(): DocumentDto {
  return {
    id: documentHeaderId,
    employmentId: documentEmployeeId,
    title: "Employment certificate",
    classification: "PERSONAL",
    revisionCount: 1,
    version: 2,
    createdBy: documentId(51),
    createdAt: "2026-10-01T00:00:00Z",
    currentRevisionId: documentRevisionId,
  };
}
export function documentRevision(): DocumentRevisionDto {
  return {
    id: documentRevisionId,
    documentId: documentHeaderId,
    number: 1,
    fileName: "certificate.pdf",
    mediaType: "application/pdf",
    size: documentBytes().byteLength,
    sha256: "14bcd090baf31edba64e9cbd8cdfc15f943344aa72cb3675ad8e91bfcbce03ad",
    status: "READY",
    uploadedBytes: documentBytes().byteLength,
    chunkSize: 1024 * 1024,
    version: 3,
    createdBy: documentId(51),
    createdAt: "2026-10-01T00:00:00Z",
    expiresAt: "2026-10-02T00:00:00Z",
    reason: "Employee submission",
    validationJobId: documentId(41),
    validationAttempts: 1,
    failureCode: null,
    validatedAt: "2026-10-01T00:01:00Z",
    availableActions: [],
  };
}
