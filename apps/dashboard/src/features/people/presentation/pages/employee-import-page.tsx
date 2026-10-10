import { Text } from "@fluentui/react-components";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTabs } from "../../../../core/presentation/components/app-tabs";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { employeeImportMessages } from "../i18n/employee-import-messages";
import type { EmployeeImportState } from "../models/employee-import-state";
import type { employeeImportView } from "../models/employee-import-view";

export function EmployeeImportPage({
  state,
  view,
  companyName,
  locale,
  backTo,
  actions,
  tab,
  onTab,
  onRefresh,
  results,
}: {
  state: EmployeeImportState;
  view: ReturnType<typeof employeeImportView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  actions: readonly { action: string; label: string; to: string }[];
  tab: "overview" | "rows" | "attempts";
  onTab: (tab: string) => void;
  onRefresh: () => void;
  results: ReactNode;
}) {
  const text = employeeImportMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={state.summary?.batch.fileName ?? text.details}
        context={`${companyName} / ${text.title}`}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            {actions.map((action) => (
              <Link key={action.action} to={action.to}>
                {action.label}
              </Link>
            ))}
            {(tab === "overview" || !state.summary) && (
              <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
                {messages(locale).refresh}
              </AppButton>
            )}
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {view && (
        <>
          <AppTabs
            id="employee-import"
            label={text.sections}
            tabs={[
              { id: "overview", label: text.overview },
              { id: "rows", label: text.rows },
              { id: "attempts", label: text.attempts },
            ]}
            selected={tab}
            onSelected={onTab}
          />
          <div
            className="app-report-content app-tab-panel"
            id="employee-import-panel"
            role="tabpanel"
            aria-labelledby={`employee-import-tab-${tab}`}
          >
            {tab === "overview" ? (
              <>
                <AppPropertyList title={text.overview} items={view.properties} />
                <AppPropertyList title={text.rows} items={view.counts} />
                <Text className="app-muted">{text.progressNote}</Text>
              </>
            ) : (
              results
            )}
          </div>
        </>
      )}
    </section>
  );
}
