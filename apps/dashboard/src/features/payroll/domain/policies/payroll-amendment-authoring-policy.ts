import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { PayrollAmendmentSource, PayrollAmendmentTerms } from "../entities/payroll-amendment";
import type {
  PayrollAmendmentPreparation,
  PayrollAmendmentSave,
} from "../entities/payroll-amendment-preparation";
import { normalizeCompensationTerms, validateCompensationTerms } from "./compensation-validation";
import { normalizePayrollInputTerms, validatePayrollInputTerms } from "./payroll-input-validation";

export const canAuthorPayrollAmendments = (permissions: readonly string[]): boolean =>
  permissions.includes("payroll.amend");
export function sameAmendmentSources(
  first: readonly PayrollAmendmentSource[],
  second: readonly PayrollAmendmentSource[],
): boolean {
  return (
    first.length === second.length &&
    first.every((row) =>
      second.some(
        (other) => row.assessmentId === other.assessmentId && row.revision === other.revision,
      ),
    )
  );
}
export function normalizeAmendmentTerms(terms: PayrollAmendmentTerms): PayrollAmendmentTerms {
  return Object.freeze({
    sourceVersions: Object.freeze(
      terms.sourceVersions
        .slice(0, 13)
        .map((row) =>
          Object.freeze({ assessmentId: row.assessmentId.toLowerCase(), revision: row.revision }),
        ),
    ),
    corrections: Object.freeze(
      terms.corrections.slice(0, 13).map((row) =>
        Object.freeze({
          assessmentId: row.assessmentId.toLowerCase(),
          compensation: row.compensation ? normalizeCompensationTerms(row.compensation) : null,
          input: row.input ? normalizePayrollInputTerms(row.input) : null,
          workAdjustmentId: row.workAdjustmentId?.toLowerCase() ?? null,
        }),
      ),
    ),
  });
}
export function validateAmendmentTerms(
  first: string,
  terms: PayrollAmendmentTerms,
): Failure | null {
  if (
    !isUuid(first) ||
    terms.sourceVersions.length < 1 ||
    terms.sourceVersions.length > 12 ||
    terms.corrections.length < 1 ||
    terms.corrections.length > 12 ||
    new Set(terms.sourceVersions.map((row) => row.assessmentId)).size !==
      terms.sourceVersions.length ||
    new Set(terms.corrections.map((row) => row.assessmentId)).size !== terms.corrections.length ||
    terms.sourceVersions.some(
      (row) => !isUuid(row.assessmentId) || !Number.isSafeInteger(row.revision) || row.revision < 0,
    ) ||
    !terms.corrections.some((row) => row.assessmentId === first) ||
    terms.corrections.some(
      (row) =>
        (row.compensation === null && row.input === null && row.workAdjustmentId === null) ||
        (row.workAdjustmentId !== null && !isUuid(row.workAdjustmentId)) ||
        !terms.sourceVersions.some((source) => source.assessmentId === row.assessmentId),
    )
  )
    return { code: "invalid_payroll_amendment_corrections", fields: {}, parameters: {} };
  return null;
}
export function validateAmendmentProposal(
  preparation: PayrollAmendmentPreparation,
  terms: PayrollAmendmentTerms,
): Failure | null {
  const structural = validateAmendmentTerms(preparation.firstAssessmentId, terms);
  if (structural) return structural;
  if (!sameAmendmentSources(preparation.sourceVersions, terms.sourceVersions))
    return { code: "payroll_amendment_sources_changed", fields: {}, parameters: {} };
  const first = preparation.assessments.find(
    (row) => row.source.assessmentId === preparation.firstAssessmentId,
  );
  for (const [index, correction] of terms.corrections.entries()) {
    const source = preparation.assessments.find(
      (row) => row.source.assessmentId === correction.assessmentId,
    );
    if (!first || !source || source.month < first.month)
      return { code: "invalid_payroll_amendment_corrections", fields: {}, parameters: {} };
    if (
      correction.workAdjustmentId !== null &&
      correction.workAdjustmentId !== source.availableWorkAdjustment?.id
    )
      return {
        code: "stale_workforce_version",
        fields: {},
        parameters: { assessmentId: correction.assessmentId },
      };
    if (correction.compensation) {
      const tax = correction.compensation.tax,
        original = source.compensation.tax;
      if (
        tax.residency !== original.residency ||
        tax.ptkp !== original.ptkp ||
        tax.residenceCountry !== original.residenceCountry ||
        tax.subjectiveFrom !== original.subjectiveFrom ||
        tax.subjectiveUntil !== original.subjectiveUntil ||
        tax.verifiedOn !== original.verifiedOn ||
        tax.verificationReference !== original.verificationReference
      )
        return {
          code: "payroll_amendment_tax_evidence_changed",
          fields: {},
          parameters: { taxMonth: source.month },
        };
    }
    for (const [kind, failure] of [
      [
        "compensation",
        correction.compensation
          ? validateCompensationTerms(source.month, correction.compensation)
          : null,
      ],
      [
        "input",
        correction.input ? validatePayrollInputTerms(source.month, correction.input) : null,
      ],
    ] as const) {
      if (failure)
        return {
          ...failure,
          fields: Object.fromEntries(
            Object.entries(failure.fields).map(([key, code]) => [
              `corrections[${index}].${kind}.${key.replace(/^terms\./u, "")}`,
              code,
            ]),
          ),
          parameters: { ...failure.parameters, taxMonth: source.month },
        };
    }
  }
  return null;
}
export function validateAmendmentSave(input: PayrollAmendmentSave): Failure | null {
  if (!input.reason.trim() || input.reason.length > 1000 || /[\p{Cc}]/u.test(input.reason))
    return { code: "invalid_payroll_amendment", fields: { reason: "invalid" }, parameters: {} };
  if (
    input.expectedVersion !== null &&
    (!Number.isInteger(input.expectedVersion) ||
      input.expectedVersion < 0 ||
      input.expectedVersion > 998)
  )
    return { code: "invalid_version", fields: {}, parameters: {} };
  return validateAmendmentTerms(input.firstAssessmentId, input);
}
export const amendmentAccessLost = (failure: Failure): boolean =>
  [
    "access_denied",
    "company_access_denied",
    "company_not_found",
    "payroll_assessment_not_found",
    "payroll_amendment_not_found",
    "payroll_amendment_self_preparation_forbidden",
    "authentication_required",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
    "session_revoked",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
  ].includes(failure.code);
export const amendmentContextChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "stale_workforce_version",
    "payroll_amendment_sources_changed",
    "payroll_amendment_not_editable",
    "payroll_amendment_basis_changed",
    "payroll_amendment_sources_invalid",
    "payroll_amendment_calculation_limit",
    "payroll_amendment_revision_limit",
  ].includes(failure.code);
export const amendmentCommandRejected = (failure: Failure): boolean =>
  amendmentAccessLost(failure) ||
  amendmentContextChanged(failure) ||
  [
    "recent_authentication_required",
    "csrf_invalid",
    "company_required",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
    "invalid_payroll_amendment",
    "invalid_payroll_amendment_corrections",
    "payroll_amendment_tax_evidence_changed",
    "invalid_version",
    "invalid_decimal_amount",
    "invalid_compensation",
    "invalid_compensation_amount",
    "invalid_payroll_input",
    "payroll_amount_limit",
    "data_conflict",
  ].includes(failure.code);
