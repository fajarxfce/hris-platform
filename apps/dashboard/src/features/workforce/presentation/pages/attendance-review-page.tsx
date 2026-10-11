import { Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { AttendanceSaveFeedback } from "../components/attendance-save-feedback";
import type { useAttendanceReviewForm } from "../controllers/use-attendance-review-form";
import { attendanceMessages } from "../i18n/attendance-messages";
import type { AttendanceEditorState } from "../models/attendance-editor-state";
import type { attendanceEntryView } from "../models/attendance-view";

export function AttendanceReviewPage(props: {
  state: AttendanceEditorState;
  form: ReturnType<typeof useAttendanceReviewForm>;
  view: ReturnType<typeof attendanceEntryView> | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  onRetry: () => void;
}) {
  const { state, form, locale } = props,
    text = attendanceMessages(locale);
  return (
    <section aria-busy={state.stage === "loading" || state.stage === "saving"}>
      <AppPageHeader
        title={text.review}
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
      {props.view && (
        <div className="app-report-content">
          <AppPropertyList
            title={`${props.view.kind} / ${props.view.status}`}
            items={props.view.properties}
          />
          <form className="app-editor-form" onSubmit={form.submit}>
            <Text>{text.independent}</Text>
            <AppSelect label={text.decision} {...form.decision} disabled={!form.editable}>
              <option value="ACCEPT">{text.ACCEPT}</option>
              <option value="REJECT">{text.REJECT}</option>
            </AppSelect>
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
                {text.recordReview}
              </AppButton>
              {(state.stage === "editing" || state.stage === "conflict") && (
                <AppButton onClick={form.refresh}>{text.reload}</AppButton>
              )}
            </div>
          </form>
        </div>
      )}
    </section>
  );
}
