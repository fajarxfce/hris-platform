import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppMetricCard } from "../../../../core/presentation/components/app-metric-card";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useHeadcountFilters } from "../controllers/use-headcount-filters";
import { reportingMessages } from "../i18n/reporting-messages";
import type { HeadcountState } from "../models/headcount-state";
import type { HeadcountView } from "../models/headcount-view";

export function HeadcountPage({
  state,
  view,
  filters,
  companyName,
  locale,
  onRefresh,
}: {
  state: HeadcountState;
  view: HeadcountView | null;
  filters: ReturnType<typeof useHeadcountFilters>;
  companyName: string;
  locale: Locale;
  onRefresh: () => void;
}) {
  const text = reportingMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.headcount}
        context={`${companyName} / ${shared.reports}`}
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
      <form onSubmit={filters.apply} noValidate>
        <AppCommandBar label={text.filters}>
          <AppTextField
            label={text.asOf}
            type="date"
            {...filters.date}
            error={filters.error}
            min="1900-01-01"
            max="2100-12-31"
          />
          <AppButton type="submit" appearance="primary">
            {text.apply}
          </AppButton>
        </AppCommandBar>
      </form>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {view && (
        <div className="app-report-content">
          <div className="app-metric-grid">
            <AppMetricCard label={text.employments} value={view.employments} />
            <AppMetricCard label={text.persons} value={view.persons} />
          </div>
          <div className="app-report-grid">
            <AppPropertyList title={text.statuses} items={view.statuses} />
            <AppPropertyList title={text.contracts} items={view.contracts} />
          </div>
          <Text className="app-muted">{text.definition}</Text>
        </div>
      )}
    </section>
  );
}
