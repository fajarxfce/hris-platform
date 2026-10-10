import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useClientPolicyVersion } from "../controllers/use-client-policy-version";
import { clientPolicyEditorMessages } from "../i18n/client-policy-editor-messages";
import { clientPolicyMessages } from "../i18n/client-policy-messages";
import type { ClientPolicyState } from "../models/client-policy-state";
import type { ClientPolicyView } from "../models/client-policy-view";

export function ClientPolicyPage({
  state,
  view,
  history,
  locale,
  companyName,
  onRefresh,
  editTo,
}: {
  state: ClientPolicyState;
  view: ClientPolicyView | null;
  history: ReturnType<typeof useClientPolicyVersion>;
  locale: Locale;
  companyName: string;
  onRefresh: () => void;
  editTo: string;
}) {
  const text = clientPolicyMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={`${companyName} / ${text.settings}`}
        actions={
          <>
            {state.stage === "ready" && (
              <Link to={editTo}>{clientPolicyEditorMessages(locale).edit}</Link>
            )}
            <AppButton
              icon={<ArrowClockwise20Regular />}
              onClick={onRefresh}
              disabled={state.stage === "loading"}
            >
              {shared.refresh}
            </AppButton>
          </>
        }
      />
      <form onSubmit={history.apply} noValidate>
        <AppCommandBar label={text.history}>
          <AppTextField
            label={text.version}
            type="number"
            min={0}
            max={9999}
            step={1}
            placeholder="0–9999"
            {...history.input}
            error={history.error}
          />
          <AppButton type="submit" appearance="primary">
            {text.view}
          </AppButton>
          <AppButton onClick={history.latest}>{text.latest}</AppButton>
        </AppCommandBar>
      </form>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {view && (
        <div className="app-report-content">
          <div className="app-report-grid">
            <AppPropertyList title={text.effective} items={view.effective} />
            <AppPropertyList title={text.minimumBuilds} items={view.minimumBuilds} />
          </div>
          <AppResourceTable
            title={text.modules}
            columns={[
              { id: "module", label: text.module },
              { id: "availability", label: text.state },
            ]}
            rows={view.modules}
          />
          {view.selected ? (
            <AppPropertyList title={text.configuration} items={view.selected} />
          ) : (
            <Text role="status">{text.noConfiguration}</Text>
          )}
        </div>
      )}
    </section>
  );
}
