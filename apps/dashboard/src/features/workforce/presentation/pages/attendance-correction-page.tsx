import { Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { AttendanceSaveFeedback } from "../components/attendance-save-feedback";
import type { useAttendanceCorrectionForm } from "../controllers/use-attendance-correction-form";
import { attendanceMessages } from "../i18n/attendance-messages";
import type { AttendanceEditorState } from "../models/attendance-editor-state";

export function AttendanceCorrectionPage(props: {
  state: AttendanceEditorState;
  form: ReturnType<typeof useAttendanceCorrectionForm>;
  locale: Locale;
  companyName: string;
  timezone: string;
  date: string;
  backTo: string;
  onRetry: () => void;
}) {
  const { state, form, locale } = props,
    text = attendanceMessages(locale);
  return (
    <section aria-busy={state.stage === "loading" || state.stage === "saving"}>
      <AppPageHeader
        title={text.correct}
        context={`${props.companyName} / ${state.employee?.legalName ?? text.title}`}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AttendanceSaveFeedback
        state={state}
        locale={locale}
        backTo={props.backTo}
        onRetry={props.onRetry}
        onReload={form.refresh}
      />
      {state.day && (
        <form className="app-editor-form" onSubmit={form.submit}>
          <AppTextField label={text.date} value={props.date} type="date" readOnly />
          <Text>{text.correctionHint}</Text>
          <Text>
            {text.timezone}: {props.timezone}
          </Text>
          <AppCheckbox
            label={text.absence}
            checked={form.absence.checked}
            onChange={(_, data) => form.absence.onChange(data.checked === true)}
            disabled={!form.editable}
          />
          <AppTextField
            label={text.clockIn}
            type="datetime-local"
            step={60}
            {...form.clockIn}
            disabled={!form.editable || form.absence.checked}
            required={!form.absence.checked}
          />
          <AppTextField
            label={text.clockOut}
            type="datetime-local"
            step={60}
            {...form.clockOut}
            disabled={!form.editable || form.absence.checked}
            required={!form.absence.checked}
          />
          <AppTextField
            label={text.breakMinutes}
            type="number"
            min={0}
            max={1439}
            step={1}
            {...form.breakMinutes}
            disabled={!form.editable || form.absence.checked}
          />
          <AppTextArea
            label={text.reason}
            {...form.reason}
            required
            maxLength={1000}
            readOnly={!form.editable}
          />
          {form.error && <p role="alert">{form.error}</p>}
          <div className="app-form-actions">
            <AppButton type="submit" appearance="primary" disabled={!form.editable || !form.dirty}>
              {text.recordCorrection}
            </AppButton>
            {(state.stage === "editing" || state.stage === "conflict") && (
              <AppButton onClick={form.refresh}>{text.reload}</AppButton>
            )}
          </div>
        </form>
      )}
    </section>
  );
}
