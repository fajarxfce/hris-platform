import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { jobMessages } from "../i18n/job-messages";
import type { JobListState } from "../models/job-list-state";
import type { jobListView } from "../models/job-view";

export function JobsPage({
  state,
  rows,
  companyName,
  companyWide,
  locale,
  firstPage,
  onRefresh,
  onFirstPage,
  onOlderPage,
  onOpenJob,
}: {
  state: JobListState;
  rows: ReturnType<typeof jobListView>;
  companyName: string;
  companyWide: boolean;
  locale: Locale;
  firstPage: boolean;
  onRefresh: () => void;
  onFirstPage: () => void;
  onOlderPage: () => void;
  onOpenJob: (id: string) => void;
}) {
  const text = jobMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={`${companyName} / ${text.administration}`}
        actions={
          <AppButton
            icon={<ArrowClockwise20Regular />}
            onClick={onRefresh}
            disabled={state.stage === "loading"}
          >
            {shared.refresh}
          </AppButton>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      <div className="app-report-content">
        {state.page &&
          (rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <AppResourceTable
              title={companyWide ? text.all : text.own}
              columns={[
                { id: "kind", label: text.kind },
                { id: "status", label: text.status },
                { id: "progress", label: text.progress, numeric: true },
                { id: "created", label: text.created },
              ]}
              rows={rows}
              action={{ label: text.view, onOpen: onOpenJob }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.page}>
          <AppButton onClick={onFirstPage} disabled={firstPage || state.stage === "loading"}>
            {text.first}
          </AppButton>
          <AppButton
            onClick={onOlderPage}
            disabled={!state.page?.next || state.stage === "loading"}
          >
            {text.older}
          </AppButton>
        </fieldset>
      </div>
    </section>
  );
}
