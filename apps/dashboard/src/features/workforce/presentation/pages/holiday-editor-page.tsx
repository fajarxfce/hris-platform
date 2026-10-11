import { MessageBar, MessageBarBody, Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useHolidayForm } from "../controllers/use-holiday-form";
import { holidayMessages } from "../i18n/holiday-messages";
import type { HolidayEditorState } from "../models/holiday-editor-state";

export function HolidayEditorPage(props: {
  state: HolidayEditorState;
  form: ReturnType<typeof useHolidayForm>;
  creating: boolean;
  companyName: string;
  locale: Locale;
  backTo: string;
  savedTo: string | null;
  onRetry: () => void;
}) {
  const { state, form, locale } = props;
  const text = holidayMessages(locale);
  return (
    <section aria-busy={state.stage === "loading" || state.stage === "saving"}>
      <AppPageHeader
        title={props.creating ? text.create : text.edit}
        context={`${props.companyName} / ${text.title}`}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "unavailable" && (
        <AppButton onClick={form.refresh}>{messages(locale).retry}</AppButton>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {state.operationId}
            </div>
            <AppButton onClick={props.onRetry}>{text.retrySave}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.saved} {props.savedTo && <Link to={props.savedTo}>{text.view}</Link>}
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage !== "loading" && state.stage !== "unavailable" && (
        <form className="app-editor-form" onSubmit={form.submit}>
          <Text>{text.calendarPolicy}</Text>
          <div className="app-editor-fields">
            <AppTextField
              label={text.date}
              {...form.workDate}
              type="date"
              min="0001-01-01"
              max="9999-12-31"
              required
              readOnly={!form.editable}
            />
            <AppTextField
              label={text.name}
              {...form.name}
              maxLength={200}
              required
              readOnly={!form.editable}
            />
            <AppSelect label={text.status} {...form.active} disabled={!form.editable}>
              <option value="true">{text.active}</option>
              <option value="false">{text.inactive}</option>
            </AppSelect>
          </div>
          <AppTextArea
            label={text.reason}
            {...form.reason}
            maxLength={1000}
            required
            readOnly={!form.editable}
          />
          {state.stage === "saving" && <AppLoading label={text.saving} />}
          <div className="app-form-actions">
            <AppButton type="submit" appearance="primary" disabled={!form.editable || !form.dirty}>
              {text.save}
            </AppButton>
            {(state.stage === "editing" || state.stage === "conflict") && !props.creating && (
              <AppButton onClick={form.refresh}>{text.reload}</AppButton>
            )}
          </div>
        </form>
      )}
    </section>
  );
}
