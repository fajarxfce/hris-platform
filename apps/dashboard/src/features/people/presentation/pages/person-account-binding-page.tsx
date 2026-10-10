import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { usePersonAccountBindingForm } from "../controllers/use-person-account-binding-form";
import { personAccountBindingMessages } from "../i18n/person-account-binding-messages";
import type { PersonAccountBindingState } from "../models/person-account-binding-state";

export function PersonAccountBindingPage({
  state,
  form,
  rows,
  locale,
  companyName,
  backTo,
  onSelect,
  onFirst,
  onNext,
  onRetry,
}: {
  state: PersonAccountBindingState;
  form: ReturnType<typeof usePersonAccountBindingForm>;
  rows: readonly Readonly<{ id: string; actionLabel: string; cells: readonly string[] }>[];
  locale: Locale;
  companyName: string;
  backTo: string;
  onSelect: (id: string) => void;
  onFirst: () => void;
  onNext: () => void;
  onRetry: () => void;
}) {
  const text = personAccountBindingMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content"
      aria-busy={
        state.stage === "loading" || state.stage === "submitting" || state.loadingCandidates
      }
    >
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <Link {...restore} to={backTo}>
            {text.back}
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
            <AppButton onClick={onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "bound" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.bound}{" "}
            <Link {...restore} to={backTo}>
              {text.view}
            </Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.profile && (
        <>
          <AppPropertyList
            title={state.profile.legalName}
            items={[{ label: text.profileVersion, value: String(state.profile.version) }]}
          />
          {state.stage === "editing" && (
            <>
              <p>{text.notice}</p>
              {state.loadingCandidates ? (
                <AppLoading label={messages(locale).loading} />
              ) : rows.length === 0 ? (
                <p role="status">{text.empty}</p>
              ) : (
                <AppResourceTable
                  title={text.candidates}
                  columns={[
                    { id: "name", label: text.name },
                    { id: "email", label: text.email },
                  ]}
                  rows={rows}
                  action={{ label: text.select, onOpen: onSelect }}
                />
              )}
              <fieldset className="app-pagination" aria-label={text.pages}>
                <AppButton
                  onClick={onFirst}
                  disabled={state.candidateAfter === null || state.loadingCandidates}
                >
                  {text.first}
                </AppButton>
                <AppButton
                  onClick={onNext}
                  disabled={state.candidateNext === null || state.loadingCandidates}
                >
                  {text.next}
                </AppButton>
              </fieldset>
            </>
          )}
          {state.selected && (
            <AppPropertyList
              title={text.selected}
              items={[
                { label: text.name, value: state.selected.displayName },
                { label: text.email, value: state.selected.email },
                { label: text.identifier, value: state.selected.id },
              ]}
            />
          )}
          {state.stage !== "blocked" && state.stage !== "bound" && (
            <form className="app-editor-form" onSubmit={form.submit} noValidate>
              <AppTextArea
                label={text.reason}
                {...form.reason}
                maxLength={1000}
                required
                readOnly={!form.editable}
                resize="vertical"
              />
              {state.stage === "submitting" && <AppLoading label={text.binding} />}
              <div className="app-form-actions">
                <AppButton
                  appearance="primary"
                  type="submit"
                  disabled={!form.editable || state.selected === null}
                >
                  {text.bind}
                </AppButton>
                {(state.stage === "editing" || state.stage === "conflict") && (
                  <AppButton {...restore} onClick={form.refresh}>
                    {text.reload}
                  </AppButton>
                )}
              </div>
            </form>
          )}
        </>
      )}
    </section>
  );
}
