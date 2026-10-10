import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { lifecycleMessages } from "../i18n/lifecycle-messages";
import type { LifecycleTemplateState } from "../models/lifecycle-template-state";
import type { lifecycleTemplateView } from "../models/lifecycle-template-view";

export function LifecycleTemplatePage({
  state,
  view,
  companyName,
  locale,
  backTo,
  editTo,
  onRefresh,
}: {
  state: LifecycleTemplateState;
  view: ReturnType<typeof lifecycleTemplateView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  editTo: string | null;
  onRefresh: () => void;
}) {
  const text = lifecycleMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={state.template?.name ?? text.details}
        context={`${companyName} / ${text.title}`}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            {editTo && <Link to={editTo}>{text.edit}</Link>}
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
          <AppPropertyList title={text.overview} items={view.properties} />
          <AppResourceTable
            title={text.tasks}
            columns={[
              { id: "key", label: text.key },
              { id: "title", label: text.taskTitle },
              { id: "required", label: text.required },
              { id: "dueDays", label: text.dueDays, numeric: true },
            ]}
            rows={view.tasks}
          />
        </div>
      )}
    </section>
  );
}
