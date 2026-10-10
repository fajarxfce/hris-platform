import {
  MessageBar,
  MessageBarBody,
  Text,
  useRestoreFocusTarget,
} from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { organizationUnitKinds } from "../../domain/entities/organization-unit";
import type { useOrganizationEditorForm } from "../controllers/use-organization-editor-form";
import { organizationMessages } from "../i18n/organization-messages";
import type { OrganizationEditorState } from "../models/organization-editor-state";

export function OrganizationEditorPage({
  state,
  form,
  creating,
  companyName,
  locale,
  backTo,
  detailTo,
  onRetrySave,
}: {
  state: OrganizationEditorState;
  form: ReturnType<typeof useOrganizationEditorForm>;
  creating: boolean;
  companyName: string;
  locale: Locale;
  backTo: string;
  detailTo: string;
  onRetrySave: () => void;
}) {
  const text = organizationMessages(locale);
  const restoreFocus = useRestoreFocusTarget();
  return (
    <section aria-busy={state.stage === "loading" || state.stage === "saving"}>
      <AppPageHeader
        title={creating ? text.create : text.edit}
        context={`${companyName} / ${text.title}`}
        actions={
          <Link {...restoreFocus} to={backTo}>
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
            <AppButton onClick={onRetrySave}>{text.retrySave}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.saved}{" "}
            <Link {...restoreFocus} to={detailTo}>
              {text.view}
            </Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage !== "loading" && state.stage !== "unavailable" && (
        <form className="app-editor-form" onSubmit={form.submit}>
          <div className="app-editor-fields">
            <AppTextField
              label={text.code}
              {...form.code}
              required
              maxLength={32}
              readOnly={!form.editable}
            />
            <AppTextField
              label={text.name}
              {...form.name}
              required
              maxLength={200}
              readOnly={!form.editable}
            />
            <AppSelect label={text.kind} {...form.kind} disabled={!creating || !form.editable}>
              {organizationUnitKinds.map((kind) => (
                <option key={kind} value={kind}>
                  {text[kind]}
                </option>
              ))}
            </AppSelect>
            <AppSelect label={text.status} {...form.active} disabled={!form.editable}>
              <option value="true">{text.active}</option>
              <option value="false">{text.inactive}</option>
            </AppSelect>
            {form.kind.value === "BRANCH" && (
              <AppTextField
                label={text.timezone}
                {...form.timezone}
                required
                maxLength={128}
                hint="Asia/Jakarta"
                readOnly={!form.editable}
              />
            )}
          </div>
          <section className="app-editor-parent" aria-label={text.parent}>
            <Text weight="semibold">{text.parent}</Text>
            <Text>{form.parent ? `${form.parent.name} (${form.parent.code})` : text.none}</Text>
            <div className="app-form-actions">
              <AppButton
                {...restoreFocus}
                disabled={!form.editable}
                onClick={() => form.chooseParent(true)}
              >
                {text.chooseParent}
              </AppButton>
              {form.parent && (
                <AppButton disabled={!form.editable} onClick={form.clearParent}>
                  {text.clearParent}
                </AppButton>
              )}
            </div>
          </section>
          {state.stage === "saving" && <AppLoading label={text.saving} />}
          <div className="app-form-actions">
            <AppButton type="submit" appearance="primary" disabled={!form.editable || !form.dirty}>
              {text.save}
            </AppButton>
            {(state.stage === "editing" || state.stage === "conflict") && !creating && (
              <AppButton {...restoreFocus} onClick={form.refresh}>
                {text.reload}
              </AppButton>
            )}
          </div>
        </form>
      )}
    </section>
  );
}
