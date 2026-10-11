import { Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { CalendarSaveFeedback } from "../components/calendar-save-feedback";
import { ShiftCatalogControls } from "../components/shift-catalog-controls";
import type { useShiftCatalog } from "../controllers/use-shift-catalog";
import type { useWeeklyScheduleForm } from "../controllers/use-weekly-schedule-form";
import { calendarMessages } from "../i18n/calendar-messages";
import type { CalendarEditorState } from "../models/calendar-editor-state";

export function WeeklySchedulePage(props: {
  state: CalendarEditorState;
  form: ReturnType<typeof useWeeklyScheduleForm>;
  catalog: ReturnType<typeof useShiftCatalog>;
  locale: Locale;
  companyName: string;
  date: string;
  backTo: string;
  onRetry: () => void;
}) {
  const { state, form, locale } = props;
  const text = calendarMessages(locale);
  return (
    <section aria-busy={state.stage === "loading" || state.stage === "saving"}>
      <AppPageHeader
        title={text.weekly}
        context={`${props.companyName} / ${state.review?.employee.legalName ?? text.calendar}`}
        actions={<Link to={props.backTo}>{text.view}</Link>}
      />
      <CalendarSaveFeedback
        state={state}
        locale={locale}
        calendarTo={props.backTo}
        onRetry={props.onRetry}
        onReload={form.refresh}
      />
      {state.review && (
        <>
          <ShiftCatalogControls catalog={props.catalog} locale={locale} disabled={!form.editable} />
          <form className="app-editor-form" onSubmit={form.submit}>
            <AppTextField label={text.effectiveFrom} value={props.date} readOnly type="date" />
            <Text>{text.weeklyPolicy}</Text>
            <Text>{text.snapshots}</Text>
            <div className="app-editor-fields">
              {form.days.map((day) => (
                <AppSelect
                  key={day.day}
                  label={day.label}
                  name={day.day}
                  value={day.value}
                  onChange={day.onChange}
                  onBlur={day.onBlur}
                  disabled={!form.editable}
                >
                  <option value="">{text.OFF}</option>
                  {form.options.map((option) => (
                    <option key={option.key} value={option.key}>
                      {option.label}
                    </option>
                  ))}
                </AppSelect>
              ))}
            </div>
            <AppTextArea
              label={text.reason}
              {...form.reason}
              required
              maxLength={1000}
              readOnly={!form.editable}
            />
            <div className="app-form-actions">
              <AppButton
                type="submit"
                appearance="primary"
                disabled={!form.editable || !form.dirty}
              >
                {text.save}
              </AppButton>
              {(state.stage === "editing" || state.stage === "conflict") && (
                <AppButton onClick={form.refresh}>{text.reload}</AppButton>
              )}
            </div>
          </form>
        </>
      )}
    </section>
  );
}
