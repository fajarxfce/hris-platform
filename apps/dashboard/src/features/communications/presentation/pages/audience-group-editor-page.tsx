import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useAudienceGroupForm } from "../controllers/use-audience-group-form";
import { audienceGroupFieldError, audienceGroupMessages } from "../i18n/audience-group-messages";
import type { AudienceGroupEditorState } from "../models/audience-group-editor-state";

export function AudienceGroupEditorPage(props: {
  state: AudienceGroupEditorState;
  form: ReturnType<typeof useAudienceGroupForm>;
  members: ReactNode;
  creating: boolean;
  locale: Locale;
  companyName: string;
  backTo: string;
  savedTo: string | null;
  onRetry: () => void;
}) {
  const text = audienceGroupMessages(props.locale);
  const { state, form } = props;
  return (
    <section
      className="app-report-content app-record-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={props.creating ? text.create : text.edit}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppFailure failure={state.failure} locale={props.locale} />
      {state.stage === "loading" && <AppLoading label={messages(props.locale).loading} />}
      {state.stage === "unavailable" && (
        <AppButton onClick={form.refresh}>{messages(props.locale).retry}</AppButton>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" layout="multiline" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {state.operationId}
            </div>
            <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.saved} {props.savedTo && <Link to={props.savedTo}>{text.viewSaved}</Link>}
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage !== "loading" && state.stage !== "unavailable" && state.stage !== "saved" && (
        <>
          {state.group && (
            <p>
              {text.basedOn}: {state.group.version}
            </p>
          )}
          {state.stage === "conflict" && (
            <MessageBar intent="warning" role="status">
              <MessageBarBody>{text.conflict}</MessageBarBody>
            </MessageBar>
          )}
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <AppTextField
              label={text.name}
              {...form.name}
              maxLength={120}
              required
              readOnly={!form.editable}
              error={audienceGroupFieldError(state.failure?.fields.name, props.locale)}
            />
            <AppCheckbox
              label={text.active}
              checked={form.active.value}
              disabled={!form.editable}
              ref={form.active.ref}
              onBlur={form.active.onBlur}
              onChange={(_, data) => form.active.onChange(data.checked === true)}
            />
            <p>{text.eligibility}</p>
            {props.members}
            {state.failure?.fields.employmentIds && (
              <p role="alert">
                {audienceGroupFieldError(state.failure.fields.employmentIds, props.locale)}
              </p>
            )}
            <AppTextArea
              label={text.reason}
              {...form.reason}
              maxLength={1000}
              required
              readOnly={!form.editable}
              error={audienceGroupFieldError(state.failure?.fields.reason, props.locale)}
            />
            <div className="app-form-actions">
              <AppButton type="submit" appearance="primary" disabled={!form.editable}>
                {state.stage === "saving" ? text.saving : text.save}
              </AppButton>
              {!props.creating && (
                <AppButton
                  type="button"
                  disabled={state.stage === "saving" || state.stage === "unconfirmed"}
                  onClick={form.refresh}
                >
                  {text.review}
                </AppButton>
              )}
            </div>
          </form>
        </>
      )}
    </section>
  );
}
