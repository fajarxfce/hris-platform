import { Search20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useOvertimePlanningEmployees } from "../controllers/use-overtime-planning-employees";
import { overtimePlanningMessages } from "../i18n/overtime-planning-messages";
import type { overtimePlanningEmployeeRows } from "../models/overtime-planning-view";

export function OvertimePlanningEmployeesPage(props: {
  picker: ReturnType<typeof useOvertimePlanningEmployees>;
  rows: ReturnType<typeof overtimePlanningEmployeeRows>;
  workDate: string;
  locale: Locale;
  onSelect: (id: string) => void;
  onClose: () => void;
}) {
  const { picker, locale } = props,
    text = overtimePlanningMessages(locale);
  return (
    <AppDetailsPanel
      open
      title={text.chooseEmployee}
      closeLabel={text.close}
      onClose={props.onClose}
    >
      <div className="app-report-content" aria-busy={picker.loading}>
        <p className="app-muted">
          {text.workDate}: {props.workDate}
        </p>
        <form className="app-form" onSubmit={picker.apply} noValidate>
          <AppTextField
            label={text.searchEmployees}
            contentBefore={<Search20Regular />}
            {...picker.query}
            maxLength={120}
          />
          <AppButton type="submit" appearance="primary">
            {text.search}
          </AppButton>
        </form>
        <AppFailure failure={picker.failure} locale={locale} />
        {picker.failure && <AppButton onClick={picker.refresh}>{messages(locale).retry}</AppButton>}
        {picker.loading && <AppLoading label={messages(locale).loading} />}
        {!picker.loading &&
          !picker.failure &&
          (props.rows.length > 0 ? (
            <AppResourceTable
              title={text.employees}
              columns={[
                { id: "employee", label: text.employee },
                { id: "status", label: text.status },
              ]}
              rows={props.rows}
              minWidth={300}
              action={{ label: text.select, onOpen: props.onSelect }}
            />
          ) : (
            <p role="status">{text.empty}</p>
          ))}
        <fieldset className="app-pagination" aria-label={text.pages}>
          <AppButton
            appearance="subtle"
            onClick={picker.first}
            disabled={picker.firstPage || picker.loading}
          >
            {text.first}
          </AppButton>
          <AppButton
            appearance="subtle"
            onClick={picker.next}
            disabled={!picker.nextCursor || picker.loading}
          >
            {text.next}
          </AppButton>
        </fieldset>
      </div>
    </AppDetailsPanel>
  );
}
