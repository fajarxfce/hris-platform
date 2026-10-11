import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppConfirmationDialog } from "../../../../core/presentation/components/app-confirmation-dialog";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { AnnouncementCommandKind } from "../../domain/entities/announcement-command";
import type { useAnnouncementCommandForm } from "../controllers/use-announcement-command-form";
import { announcementPublicationMessages } from "../i18n/announcement-publication-messages";
import type { AnnouncementCommandState } from "../models/announcement-command-state";
import type { announcementPreviewView } from "../models/announcement-publication-view";

export function AnnouncementCommandPage(props: {
  state: AnnouncementCommandState;
  form: ReturnType<typeof useAnnouncementCommandForm>;
  previewProperties: ReturnType<typeof announcementPreviewView>;
  audience: ReactNode;
  kind: AnnouncementCommandKind;
  locale: Locale;
  companyName: string;
  timezone: string;
  backTo: string;
  savedTo: string | null;
  onRetry: () => void;
  onConfirm: () => void;
  onDismiss: () => void;
}) {
  const text = announcementPublicationMessages(props.locale);
  const { state, form } = props;
  return (
    <section
      className="app-report-content app-record-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text[props.kind]}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.title}</Link>}
      />
      <AppFailure failure={state.failure} locale={props.locale} />
      {state.stage === "loading" && <AppLoading label={messages(props.locale).loading} />}
      {state.stage === "unavailable" && (
        <AppButton onClick={form.refresh}>{messages(props.locale).retry}</AppButton>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" role="status" layout="multiline">
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
            {props.kind === "PUBLISH"
              ? text.acceptedPublish
              : props.kind === "ARCHIVE"
                ? text.acceptedArchive
                : text.acceptedReturn}{" "}
            {props.savedTo && <Link to={props.savedTo}>{text.view}</Link>}
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage !== "loading" &&
        state.stage !== "unavailable" &&
        state.stage !== "saved" &&
        state.review && (
          <>
            <h2>{state.review.announcement.title}</h2>
            <p className="app-announcement-body">{state.review.announcement.body}</p>
            <p>
              {text.basedOn}: {state.review.announcement.version}
            </p>
            {state.preview && (
              <>
                <AppPropertyList title={text.audience} items={props.previewProperties} />
                <p>{text.previewNotice}</p>
                {props.audience}
              </>
            )}
            {state.stage === "conflict" && (
              <MessageBar intent="warning" role="status">
                <MessageBarBody>{text.conflict}</MessageBarBody>
              </MessageBar>
            )}
            <form className="app-editor-form" onSubmit={form.submit} noValidate>
              {props.kind === "PUBLISH" && (
                <>
                  <AppCheckbox
                    label={text.schedule}
                    checked={form.scheduled.value}
                    ref={form.scheduled.ref}
                    onBlur={form.scheduled.onBlur}
                    disabled={!form.editable}
                    onChange={(_, data) => form.scheduled.onChange(data.checked === true)}
                  />
                  {form.scheduled.value && (
                    <>
                      <AppTextField
                        label={`${text.scheduledFor} · ${props.timezone}`}
                        type="datetime-local"
                        step="1"
                        {...form.localTime}
                        required
                        readOnly={!form.editable}
                        error={
                          form.localTimeError || state.failure?.fields.scheduledFor
                            ? text.invalidTime
                            : undefined
                        }
                      />
                      <p>{text.scheduleHelp}</p>
                    </>
                  )}
                </>
              )}
              <AppTextArea
                label={text.reason}
                {...form.reason}
                maxLength={1000}
                required
                readOnly={!form.editable}
                error={state.failure?.fields.reason ? text.invalid : undefined}
              />
              <div className="app-form-actions">
                <AppConfirmationDialog
                  open={state.stage === "confirming"}
                  title={text[props.kind]}
                  message={
                    props.kind === "PUBLISH"
                      ? text.confirmPublish
                      : props.kind === "ARCHIVE"
                        ? text.confirmArchive
                        : text.confirmReturn
                  }
                  confirmLabel={text.confirm}
                  dismissLabel={text.cancel}
                  busy={false}
                  onConfirm={props.onConfirm}
                  onDismiss={props.onDismiss}
                  trigger={
                    <AppButton type="submit" appearance="primary" disabled={!form.editable}>
                      {text.continue}
                    </AppButton>
                  }
                />
                <AppButton
                  type="button"
                  disabled={state.stage === "saving" || state.stage === "unconfirmed"}
                  onClick={form.refresh}
                >
                  {text.review}
                </AppButton>
              </div>
            </form>
          </>
        )}
    </section>
  );
}
