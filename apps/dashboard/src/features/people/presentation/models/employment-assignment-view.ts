import type { Locale } from "../../../../core/presentation/i18n/messages";
import type {
  EmploymentDetails,
  EmploymentUnitReference,
} from "../../domain/entities/employment-details";
import { employmentMessages } from "../i18n/employment-messages";
import type { EmployeeAssignmentOption } from "./employee-assignment";

export function employmentUnitOption(
  id: string | null,
  reference: EmploymentUnitReference | null,
): EmployeeAssignmentOption | null {
  if (!id) return null;
  return {
    id,
    label: reference ? `${reference.code} · ${reference.name}` : "",
    cells: [],
    ...(!reference
      ? { referenceStatus: "unavailable" as const }
      : !reference.active
        ? { referenceStatus: "inactive" as const }
        : {}),
  };
}
export function employmentAssignmentOptions(details: EmploymentDetails | null) {
  const terms = details?.employee.terms;
  const manager: EmployeeAssignmentOption | null = terms?.managerId
    ? {
        id: terms.managerId,
        label: details?.manager
          ? `${details.manager.employeeNumber} · ${details.manager.legalName}`
          : "",
        cells: [],
        ...(!details?.manager
          ? { referenceStatus: "unavailable" as const }
          : !details.manager.working
            ? { referenceStatus: "notWorking" as const }
            : {}),
      }
    : null;
  return {
    branch: employmentUnitOption(terms?.branchId ?? null, details?.branch ?? null),
    department: employmentUnitOption(terms?.departmentId ?? null, details?.department ?? null),
    position: employmentUnitOption(terms?.positionId ?? null, details?.position ?? null),
    costCenter: employmentUnitOption(terms?.costCenterId ?? null, details?.costCenter ?? null),
    manager,
  };
}
export function employeeAssignmentLabel(option: EmployeeAssignmentOption, locale: Locale): string {
  const text = employmentMessages(locale);
  if (option.referenceStatus === "unavailable") return text.unavailable;
  return option.referenceStatus
    ? `${option.label} (${text[option.referenceStatus]})`
    : option.label;
}
