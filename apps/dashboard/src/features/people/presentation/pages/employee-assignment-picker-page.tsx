import { Text } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useEmployeeAssignmentPicker } from "../controllers/use-employee-assignment-picker";
import { employeeCreationMessages } from "../i18n/employee-creation-messages";
import { peopleMessages } from "../i18n/people-messages";
import type { EmployeeAssignmentKind } from "../models/employee-assignment";

export function EmployeeAssignmentPickerPage({
  kind,
  picker,
  locale,
  onSelect,
  onClose,
}: {
  kind: EmployeeAssignmentKind;
  picker: ReturnType<typeof useEmployeeAssignmentPicker>;
  locale: Locale;
  onSelect: (id: string) => void;
  onClose: () => void;
}) {
  const text = employeeCreationMessages(locale);
  const people = peopleMessages(locale);
  return (
    <AppDetailsPanel
      open
      title={`${text.choose} ${text[kind]}`}
      closeLabel={messages(locale).close}
      onClose={onClose}
    >
      <div className="app-form" aria-busy={picker.loading}>
        {kind === "MANAGER" && <Text>{text.managerDate}</Text>}
        <form className="app-form" onSubmit={picker.apply} noValidate>
          <AppTextField
            label={kind === "MANAGER" ? text.searchManager : text.search}
            {...picker.query}
            maxLength={120}
          />
          <AppButton type="submit" appearance="primary">
            {people.apply}
          </AppButton>
        </form>
        <AppFailure failure={picker.failure} locale={locale} />
        {picker.failure && <AppButton onClick={picker.refresh}>{messages(locale).retry}</AppButton>}
        {picker.loading && <AppLoading label={messages(locale).loading} />}
        {!picker.loading &&
          !picker.failure &&
          (picker.options.length > 0 ? (
            <AppResourceTable
              title={text.results}
              columns={[
                { id: "code", label: kind === "MANAGER" ? people.number : text.code },
                { id: "name", label: text.name },
              ]}
              rows={picker.options.map((option) => ({
                id: option.id,
                actionLabel: option.label,
                cells: option.cells,
              }))}
              action={{ label: text.select, onOpen: onSelect }}
            />
          ) : (
            <Text role="status">{text.empty}</Text>
          ))}
        <div className="app-pagination">
          <AppButton onClick={picker.first} disabled={picker.firstPage || picker.loading}>
            {people.first}
          </AppButton>
          <AppButton onClick={picker.next} disabled={!picker.nextCursor || picker.loading}>
            {people.next}
          </AppButton>
        </div>
      </div>
    </AppDetailsPanel>
  );
}
