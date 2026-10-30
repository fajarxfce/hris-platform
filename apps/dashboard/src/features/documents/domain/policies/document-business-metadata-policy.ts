import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  type DocumentBusinessMetadata,
  type DocumentMetadataFields,
  documentCategories,
} from "../entities/document-metadata";

export function prepareDocumentBusinessMetadata(
  fields: Omit<DocumentMetadataFields, "reason">,
): Result<DocumentBusinessMetadata> {
  const identityNumber = fields.identityNumber.trim() || null;
  const issuingCountry = fields.issuingCountry.trim().toUpperCase() || null;
  const issuedOn = fields.issuedOn.trim() || null;
  const validUntil = fields.validUntil.trim() || null;
  if (
    !documentCategories.includes(fields.category) ||
    (identityNumber !== null && (identityNumber.length > 120 || /\p{Cc}/u.test(identityNumber))) ||
    (issuingCountry !== null && !/^[A-Z]{2}$/u.test(issuingCountry)) ||
    [issuedOn, validUntil].some(
      (value) =>
        value !== null && (!isCalendarDate(value) || value < "1900-01-01" || value > "2200-12-31"),
    ) ||
    (issuedOn !== null && validUntil !== null && validUntil < issuedOn)
  )
    return failed("invalid_document_metadata");
  return success(
    Object.freeze({
      category: fields.category,
      identityNumber,
      issuingCountry,
      issuedOn,
      validUntil,
    }),
  );
}
export function sameDocumentMetadata(
  a: DocumentBusinessMetadata,
  b: DocumentBusinessMetadata,
): boolean {
  return (
    a.category === b.category &&
    a.identityNumber === b.identityNumber &&
    a.issuingCountry === b.issuingCountry &&
    a.issuedOn === b.issuedOn &&
    a.validUntil === b.validUntil
  );
}
