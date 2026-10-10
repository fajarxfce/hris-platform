import { useRestoreFocusTarget } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { employeeCreationMessages } from "../i18n/employee-creation-messages";
import type {
  EmployeeAssignmentKind,
  EmployeeAssignmentOption,
} from "../models/employee-assignment";
import { employeeAssignmentLabel } from "../models/employment-assignment-view";

export function EmployeeAssignmentFields({
  assignments,
  editable,
  canChooseOrganization,
  canChooseManager,
  locale,
  onChoose,
  onClear,
}: {
  assignments: readonly Readonly<{
    kind: EmployeeAssignmentKind;
    value: EmployeeAssignmentOption | null;
  }>[];
  editable: boolean;
  canChooseOrganization: boolean;
  canChooseManager: boolean;
  locale: Locale;
  onChoose: (kind: EmployeeAssignmentKind) => void;
  onClear: (kind: EmployeeAssignmentKind) => void;
}) {
  const text = employeeCreationMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <div className="app-editor-fields">
      {assignments.map((assignment) => (
        <section
          key={assignment.kind}
          className="app-assignment"
          aria-label={text[assignment.kind]}
        >
          <strong>{text[assignment.kind]}</strong>
          <span>
            {assignment.value ? employeeAssignmentLabel(assignment.value, locale) : text.unassigned}
          </span>
          <div className="app-form-actions">
            <AppButton
              {...restore}
              aria-label={`${text.choose} ${text[assignment.kind]}`}
              disabled={
                !editable ||
                !(assignment.kind === "MANAGER" ? canChooseManager : canChooseOrganization)
              }
              onClick={() => onChoose(assignment.kind)}
            >
              {text.choose}
            </AppButton>
            {assignment.value && (
              <AppButton
                {...restore}
                aria-label={`${text.clear} ${text[assignment.kind]}`}
                disabled={!editable}
                onClick={() => onClear(assignment.kind)}
              >
                {text.clear}
              </AppButton>
            )}
          </div>
          {!(assignment.kind === "MANAGER" ? canChooseManager : canChooseOrganization) && (
            <small>{text.accessHint}</small>
          )}
        </section>
      ))}
    </div>
  );
}
