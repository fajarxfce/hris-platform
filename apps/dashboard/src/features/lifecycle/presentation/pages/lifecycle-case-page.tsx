import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTabs } from "../../../../core/presentation/components/app-tabs";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { lifecycleCaseMessages } from "../i18n/lifecycle-case-messages";
import type { LifecycleCaseState } from "../models/lifecycle-case-state";
import type { lifecycleCaseView } from "../models/lifecycle-case-view";

export function LifecycleCasePage({
  state,
  view,
  companyName,
  locale,
  backTo,
  tab,
  tabId,
  history,
  onTab,
  onRefresh,
  onOpenTask,
}: {
  state: LifecycleCaseState;
  view: ReturnType<typeof lifecycleCaseView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  tab: string;
  tabId: string;
  history: ReactNode;
  onTab: (tab: string) => void;
  onRefresh: () => void;
  onOpenTask: (key: string) => void;
}) {
  const text = lifecycleCaseMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={state.case?.employee.name ?? text.details}
        context={`${companyName} / ${text.title}`}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            <AppButton
              icon={<ArrowClockwise20Regular />}
              disabled={state.stage === "loading"}
              onClick={onRefresh}
            >
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {view && (
        <div className="app-report-content">
          <AppTabs
            id={tabId}
            label={text.details}
            tabs={[
              { id: "overview", label: text.overview },
              { id: "history", label: text.history },
            ]}
            selected={tab}
            onSelected={onTab}
          />
          <div
            className="app-report-content"
            id={`${tabId}-panel`}
            role="tabpanel"
            aria-labelledby={`${tabId}-tab-${tab}`}
          >
            {tab === "overview" ? (
              <>
                <AppPropertyList title={text.overview} items={view.properties} />
                <AppResourceTable
                  title={text.tasks}
                  columns={[
                    { id: "task", label: text.task },
                    { id: "status", label: text.status },
                    { id: "required", label: text.requirement },
                    { id: "due", label: text.dueDate },
                  ]}
                  rows={view.tasks}
                  action={{ label: text.viewTask, onOpen: onOpenTask }}
                />
              </>
            ) : (
              history
            )}
          </div>
        </div>
      )}
    </section>
  );
}
