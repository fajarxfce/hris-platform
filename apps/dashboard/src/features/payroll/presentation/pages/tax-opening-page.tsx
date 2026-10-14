import { ArrowSync20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTabs } from "../../../../core/presentation/components/app-tabs";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useTaxOpeningYear } from "../controllers/use-tax-opening-year";
import { taxOpeningMessages } from "../i18n/tax-opening-messages";
import type { TaxOpeningState } from "../models/tax-opening-state";
import type { taxOpeningReviewView, taxOpeningView } from "../models/tax-opening-view";
import { TaxOpeningProperties } from "./tax-opening-properties";
export function TaxOpeningPage({
  state,
  view,
  review,
  selected,
  filter,
  locale,
  companyName,
  backTo,
  editTo,
  verifyTo,
  tab,
  first,
  onRefresh,
  onFirst,
  onNext,
  onRevision,
  onClose,
  onTab,
}: {
  state: TaxOpeningState;
  view: ReturnType<typeof taxOpeningView> | null;
  review: ReturnType<typeof taxOpeningReviewView> | null;
  selected: ReturnType<typeof taxOpeningView> | null;
  filter: ReturnType<typeof useTaxOpeningYear>;
  locale: Locale;
  companyName: string;
  backTo: string;
  editTo: string | null;
  verifyTo: string | null;
  tab: string;
  first: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onRevision: (id: string) => void;
  onClose: () => void;
  onTab: (id: string) => void;
}) {
  const text = taxOpeningMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={state.review?.employee.name ?? text.details}
        context={`${companyName} / ${text.title}`}
        actions={<Link to={backTo}>{text.back}</Link>}
      />
      {state.review && (
        <p className="app-resource-identity">
          {text.employeeNumber}: {state.review.employee.employeeNumber}
        </p>
      )}
      <AppCommandBar label={text.details}>
        {editTo && <Link to={editTo}>{state.review?.current ? text.edit : text.prepare}</Link>}
        {verifyTo && <Link to={verifyTo}>{text.verify}</Link>}
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          onClick={onRefresh}
          disabled={state.stage === "loading"}
        >
          {messages(locale).refresh}
        </AppButton>
      </AppCommandBar>
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        <form className="app-resource-filters" onSubmit={filter.submit}>
          <AppTextField
            label={text.year}
            {...filter.field}
            type="number"
            min="2024"
            max="2100"
            required
          />
          <AppButton type="submit">{text.apply}</AppButton>
        </form>
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.stage === "ready" && review && (
          <>
            {review.notice && <p role="status">{review.notice}</p>}
            <AppTabs
              id="tax-opening"
              label={text.details}
              selected={tab}
              onSelected={onTab}
              tabs={[
                { id: "overview", label: text.overview },
                { id: "history", label: text.history },
              ]}
            />
            <div role="tabpanel" id="tax-opening-panel" aria-labelledby={`tax-opening-tab-${tab}`}>
              {tab === "overview" ? (
                view ? (
                  <TaxOpeningProperties view={view} locale={locale} />
                ) : (
                  <p role="status">{text.noTaxOpening}</p>
                )
              ) : (
                <>
                  <AppResourceTable
                    title={text.history}
                    minWidth={760}
                    columns={[
                      { id: "version", label: text.revision, numeric: true },
                      { id: "status", label: text.status },
                      { id: "recorded", label: text.recorded },
                      { id: "reason", label: text.reason },
                    ]}
                    rows={review.history}
                    action={{ label: text.inspect, onOpen: onRevision }}
                  />
                  <fieldset className="app-pagination" aria-label={text.historyPages}>
                    <AppButton onClick={onFirst} disabled={first}>
                      {text.first}
                    </AppButton>
                    <AppButton onClick={onNext} disabled={!state.review?.history.nextCursor}>
                      {text.next}
                    </AppButton>
                  </fieldset>
                </>
              )}
            </div>
          </>
        )}
      </div>
      <AppDetailsPanel
        open={state.inspecting || selected !== null}
        title={selected?.title ?? text.revision}
        closeLabel={text.close}
        onClose={onClose}
      >
        {state.inspecting && <AppLoading label={messages(locale).loading} />}
        {selected && <TaxOpeningProperties view={selected} locale={locale} />}
      </AppDetailsPanel>
    </section>
  );
}
