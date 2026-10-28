import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { integrationConfigurationMessages } from "../i18n/integration-configuration-messages";
import type { IntegrationConfigurationState } from "../models/integration-configuration-state";
import type { integrationConfigurationView } from "../models/integration-configuration-view";

export function IntegrationConfigurationPage({
  state,
  view,
  locale,
  visible,
  onRefresh,
}: {
  state: IntegrationConfigurationState;
  view: ReturnType<typeof integrationConfigurationView> | null;
  locale: Locale;
  visible: boolean;
  onRefresh: () => void;
}) {
  const text = integrationConfigurationMessages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={messages(locale).navigationPlatform}
        actions={
          <AppButton
            appearance="subtle"
            icon={<ArrowClockwise20Regular />}
            aria-label={messages(locale).refresh}
            disabled={!visible || state.stage === "loading"}
            onClick={onRefresh}
          />
        }
      />
      <p>{text.summary}</p>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {view && (
        <>
          <p>
            {text.observedAt}: {view.observedAt}
          </p>
          <AppResourceTable
            title={text.integrations}
            columns={[
              { id: "integration", label: text.integration },
              { id: "configuration", label: text.configuration },
            ]}
            rows={view.rows}
            minWidth={300}
          />
        </>
      )}
    </section>
  );
}
