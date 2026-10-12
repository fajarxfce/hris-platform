import type { DocumentEmployeeSearch } from "../entities/document-employee";

export const canBrowseDocuments = (permissions: readonly string[]): boolean =>
  permissions.includes("documents.read") && permissions.includes("people.profile.read");

export const validDocumentEmployeeSearch = (search: DocumentEmployeeSearch): boolean =>
  search.query.length <= 120 &&
  !/[\p{Cc}]/u.test(search.query) &&
  (search.after === null || /^[A-Z0-9][A-Z0-9_-]{1,31}$/u.test(search.after));

export const validDocumentRevisionCursor = (after: string | null): boolean =>
  after === null || (/^[1-9]\d{0,2}$/u.test(after) && Number(after) <= 101);
