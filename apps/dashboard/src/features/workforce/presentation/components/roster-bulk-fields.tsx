import { AppMultiSelect } from "../../../../core/presentation/components/app-multi-select";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { weekdays } from "../../domain/entities/schedule-assignment";
import type { useRosterBulkForm } from "../controllers/use-roster-bulk-form";
import { calendarMessages } from "../i18n/calendar-messages";
import { rosterBulkMessages } from "../i18n/roster-bulk-messages";

export function RosterBulkFields(props: {
  form: ReturnType<typeof useRosterBulkForm>;
  formId: string;
  locale: Locale;
}) {
  const { form, locale } = props,
    text = rosterBulkMessages(locale),
    calendar = calendarMessages(locale);
  return (
    <form
      id={props.formId}
      className="app-editor-form app-roster-bulk-fields"
      onSubmit={form.prepare}
      noValidate
    >
      <fieldset className="app-editor-section">
        <legend>{text.selection}</legend>
        <div className="app-roster-bulk-date-fields">
          <AppTextField
            label={text.from}
            type="date"
            min="1900-01-01"
            max="9999-12-31"
            required
            {...form.from}
            error={form.invalidRange ? text.invalidRange : undefined}
          />
          <AppTextField
            label={text.until}
            type="date"
            min="1900-01-01"
            max="9999-12-31"
            required
            {...form.until}
          />
          <AppSelect label={text.mode} {...form.mode}>
            <option value="DAY_OFF">{text.DAY_OFF}</option>
            <option value="SHIFT">{text.SHIFT}</option>
            <option value="CALENDAR">{text.CALENDAR}</option>
          </AppSelect>
        </div>
        <AppMultiSelect
          label={text.weekdays}
          value={form.weekdays}
          options={weekdays.map((value) => ({ value, label: calendar[value] }))}
          displayValue={form.weekdays.map((value) => calendar[value]).join(", ")}
          placeholder={text.weekdays}
          onChange={form.changeWeekdays}
          error={form.invalidDays ? text.invalidDays : undefined}
        />
      </fieldset>
    </form>
  );
}
