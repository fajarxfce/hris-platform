import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { ArrowClockwise20Regular, Attach20Regular, Pause20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppProgress } from "../../../../core/presentation/components/app-progress";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { DocumentUploadTarget } from "../../domain/entities/document-upload";
import type { useDocumentUploadForm } from "../controllers/use-document-upload-form";
import { documentMessages } from "../i18n/document-messages";
import { documentUploadMessages } from "../i18n/document-upload-messages";
import type { DocumentUploadState } from "../models/document-upload-state";
import type { documentUploadView } from "../models/document-upload-view";

export function DocumentUploadPage(props: {
  state: DocumentUploadState;
  form: ReturnType<typeof useDocumentUploadForm>;
  view: ReturnType<typeof documentUploadView>;
  kind: DocumentUploadTarget["kind"];
  companyName: string;
  locale: Locale;
  backTo: string;
  onSelect: () => void;
  onPause: () => void;
  onRetry: () => void;
}) {
  const { state, form, view } = props,
    text = documentUploadMessages(props.locale),
    documents = documentMessages(props.locale);
  return (
    <section
      className="app-document-upload"
      aria-busy={
        state.stage === "loading" || state.stage === "selecting" || state.stage === "uploading"
      }
    >
      <AppPageHeader
        title={text[props.kind]}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={props.locale} />
        {state.stage === "loading" && <AppLoading label={messages(props.locale).loading} />}
        {state.stage === "unavailable" && (
          <AppButton onClick={form.refresh}>{text.reload}</AppButton>
        )}
        {state.stage === "paused" && (
          <MessageBar intent="warning" layout="multiline" role="status">
            <MessageBarBody>
              {text.paused} <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
            </MessageBarBody>
          </MessageBar>
        )}
        {state.stage === "complete" && (
          <MessageBar intent="success" layout="multiline" role="status">
            <MessageBarBody>
              {view.completedMessage}
              <div className="app-form-actions">
                {view.validateTo && <Link to={view.validateTo}>{text.validate}</Link>}
                {view.revisionTo && <Link to={view.revisionTo}>{text.view}</Link>}
              </div>
            </MessageBarBody>
          </MessageBar>
        )}
        {view.progress && <AppProgress {...view.progress} />}
        {state.stage === "uploading" && (
          <div className="app-form-actions">
            <AppLoading label={text.uploading} />
            <AppButton appearance="subtle" icon={<Pause20Regular />} onClick={props.onPause}>
              {text.pause}
            </AppButton>
          </div>
        )}
        {state.review && state.stage !== "complete" && (
          <>
            <AppPropertyList title={text.review} items={view.properties} />
            <form className="app-editor-form" onSubmit={form.submit} noValidate>
              {props.kind === "new" && (
                <>
                  <AppTextField
                    label={text.title}
                    {...form.title}
                    maxLength={160}
                    required
                    readOnly={!form.editable}
                  />
                  <AppSelect
                    label={text.classification}
                    {...form.classification}
                    disabled={!form.editable}
                  >
                    <option value="PERSONAL">{documents.PERSONAL}</option>
                    <option value="HR_ONLY">{documents.HR_ONLY}</option>
                    <option value="RECEIPT">{documents.RECEIPT}</option>
                  </AppSelect>
                </>
              )}
              {props.kind !== "resume" && (
                <AppTextField
                  label={text.reason}
                  {...form.reason}
                  maxLength={1000}
                  required
                  readOnly={!form.editable}
                />
              )}
              <fieldset className="app-document-file">
                <legend>{text.file}</legend>
                <p>{props.kind === "resume" ? text.resumeHint : text.allowed}</p>
                {state.stage === "selecting" && <AppLoading label={text.checking} />}
                <div className="app-form-actions">
                  <AppButton
                    appearance="subtle"
                    icon={<Attach20Regular />}
                    onClick={props.onSelect}
                    disabled={!form.editable}
                  >
                    {view.selectedFile ? text.change : text.choose}
                  </AppButton>
                  {view.selectedFile && (
                    <span className="app-document-file-name">{view.selectedFile}</span>
                  )}
                </div>
              </fieldset>
              <div className="app-form-actions">
                <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                  {props.kind === "resume" ? text.continue : text.submit}
                </AppButton>
                {(state.stage === "editing" || state.stage === "conflict") && (
                  <AppButton
                    appearance="subtle"
                    icon={<ArrowClockwise20Regular />}
                    onClick={form.refresh}
                  >
                    {text.reload}
                  </AppButton>
                )}
              </div>
            </form>
          </>
        )}
      </div>
    </section>
  );
}
