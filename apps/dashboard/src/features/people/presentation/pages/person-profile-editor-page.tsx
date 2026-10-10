import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { usePersonProfileForm } from "../controllers/use-person-profile-form";
import { personProfileMessages, profileFieldError } from "../i18n/person-profile-messages";
import type { PersonProfileState } from "../models/person-profile-state";

export function PersonProfileEditorPage({
  state,
  form,
  companyName,
  locale,
  backTo,
  onRetrySave,
}: {
  state: PersonProfileState;
  form: ReturnType<typeof usePersonProfileForm>;
  companyName: string;
  locale: Locale;
  backTo: string;
  onRetrySave: () => void;
}) {
  const text = personProfileMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section aria-busy={state.stage === "loading" || state.stage === "saving"}>
      <AppPageHeader
        title={text.edit}
        context={`${companyName} / ${text.title}`}
        actions={
          <Link {...restore} to={backTo}>
            {text.backToProfile}
          </Link>
        }
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
            <AppButton onClick={onRetrySave}>{text.retrySave}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.saved}{" "}
            <Link {...restore} to={backTo}>
              {text.view}
            </Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.profile && (
        <form className="app-editor-form" onSubmit={form.submit} noValidate>
          <div className="app-editor-fields">
            <AppTextField
              label={text.legalName}
              {...form.legalName}
              required
              maxLength={200}
              readOnly={!form.editable}
              error={profileFieldError(state.failure?.fields.legalName, locale)}
            />
            <AppTextField
              label={text.birthDate}
              {...form.birthDate}
              type="date"
              readOnly={!form.editable}
              error={profileFieldError(state.failure?.fields.birthDate, locale)}
            />
            <AppTextField
              label={text.nationality}
              {...form.nationality}
              required
              maxLength={2}
              hint={text.countryHint}
              readOnly={!form.editable}
              error={profileFieldError(state.failure?.fields.nationality, locale)}
            />
            <AppTextField
              label={text.email}
              {...form.email}
              type="email"
              maxLength={254}
              readOnly={!form.editable}
              error={profileFieldError(state.failure?.fields.email, locale)}
            />
          </div>
          <AppTextArea
            label={text.reason}
            {...form.reason}
            required
            maxLength={1000}
            resize="vertical"
            readOnly={!form.editable}
          />
          {state.stage === "saving" && <AppLoading label={text.saving} />}
          <div className="app-form-actions">
            <AppButton type="submit" appearance="primary" disabled={!form.editable || !form.dirty}>
              {text.save}
            </AppButton>
            {(state.stage === "editing" || state.stage === "conflict") && (
              <AppButton {...restore} onClick={form.refresh}>
                {text.reload}
              </AppButton>
            )}
          </div>
        </form>
      )}
    </section>
  );
}
