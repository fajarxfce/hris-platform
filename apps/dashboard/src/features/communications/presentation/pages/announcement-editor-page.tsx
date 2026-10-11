import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { AudienceKind } from "../../domain/entities/announcement";
import type { useAnnouncementForm } from "../controllers/use-announcement-form";
import {
  announcementEditorMessages,
  announcementFieldError,
} from "../i18n/announcement-editor-messages";
import { announcementMessages } from "../i18n/announcement-messages";
import type { AnnouncementEditorState } from "../models/announcement-editor-state";

export function AnnouncementEditorPage(props: {
  state: AnnouncementEditorState;
  form: ReturnType<typeof useAnnouncementForm>;
  selection: ReactNode;
  creating: boolean;
  companyName: string;
  locale: Locale;
  backTo: string;
  savedTo: string | null;
  onRetry: () => void;
}) {
  const text = announcementEditorMessages(props.locale);
  const read = announcementMessages(props.locale);
  const { state, form } = props;
  return (
    <section
      className="app-report-content app-announcement-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={props.creating ? text.create : text.edit}
        context={props.companyName}
        actions={<Link to={props.backTo}>{read.back}</Link>}
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
        <MessageBar intent="success" layout="multiline" role="status">
          <MessageBarBody>
            {text.saved} {props.savedTo && <Link to={props.savedTo}>{text.view}</Link>}
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage !== "loading" && state.stage !== "unavailable" && state.stage !== "saved" && (
        <>
          {state.announcement && (
            <p>
              {text.basedOn}: {state.announcement.version}
            </p>
          )}
          {state.stage === "conflict" && (
            <MessageBar intent="warning" role="status">
              <MessageBarBody>{text.conflict}</MessageBarBody>
            </MessageBar>
          )}
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <AppTextField
              label={text.title}
              {...form.title}
              maxLength={200}
              required
              readOnly={!form.editable}
              error={announcementFieldError(state.failure?.fields.title, props.locale)}
            />
            <AppTextArea
              label={text.body}
              {...form.body}
              maxLength={16000}
              rows={8}
              resize="vertical"
              required
              readOnly={!form.editable}
              error={announcementFieldError(state.failure?.fields.body, props.locale)}
            />
            <AppSelect
              label={text.audience}
              {...form.audienceKind}
              disabled={!form.editable}
              onChange={(_, data) => form.changeKind(data.value as AudienceKind)}
              error={announcementFieldError(
                state.failure?.fields["audience.targetIds"] ??
                  state.failure?.fields["audience.kind"],
                props.locale,
              )}
            >
              <option value="COMPANY">{read.COMPANY}</option>
              <option value="BRANCH">{read.BRANCH}</option>
              <option value="DEPARTMENT">{read.DEPARTMENT}</option>
              <option value="GROUP">{read.GROUP}</option>
            </AppSelect>
            {props.selection}
            <AppCheckbox
              label={text.acknowledgement}
              checked={form.acknowledgementRequired.value}
              disabled={!form.editable}
              ref={form.acknowledgementRequired.ref}
              onBlur={form.acknowledgementRequired.onBlur}
              onChange={(_, data) => form.acknowledgementRequired.onChange(data.checked === true)}
            />
            <AppTextArea
              label={text.reason}
              {...form.reason}
              maxLength={1000}
              required
              readOnly={!form.editable}
              error={announcementFieldError(state.failure?.fields.reason, props.locale)}
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
