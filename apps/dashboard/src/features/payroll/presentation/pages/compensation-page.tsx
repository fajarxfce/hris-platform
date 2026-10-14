import { ArrowSync20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { usePayrollPolicyMonth } from "../controllers/use-payroll-policy-month";
import { compensationMessages } from "../i18n/compensation-messages";
import type { CompensationState } from "../models/compensation-state";
import type {
  compensationEvidenceView,
  compensationHistoryView,
  compensationView,
} from "../models/compensation-view";
import { CompensationProperties } from "./compensation-properties";

export function CompensationPage({
  state,
  effective,
  selected,
  evidence,
  history,
  filter,
  locale,
  companyName,
  timezone,
  editTo,
  backTo,
  firstHistory,
  onRefresh,
  onFirst,
  onNext,
  onRevision,
  onInspect,
  onClose,
}: {
  state: CompensationState;
  effective: ReturnType<typeof compensationView> | null;
  selected: ReturnType<typeof compensationView> | null;
  evidence: ReturnType<typeof compensationEvidenceView> | null;
  history: ReturnType<typeof compensationHistoryView>;
  filter: ReturnType<typeof usePayrollPolicyMonth>;
  locale: Locale;
  companyName: string;
  timezone: string;
  editTo: string | null;
  backTo: string;
  firstHistory: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onRevision: (id: string) => void;
  onInspect: () => void;
  onClose: () => void;
}) {
  const text = compensationMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={state.review?.employee.name ?? text.title}
        context={`${companyName} / ${text.title}`}
        actions={<Link to={backTo}>{text.back}</Link>}
      />
      {state.review && (
        <p className="app-resource-identity">
          {text.employeeNumber}: {state.review.employee.employeeNumber}
        </p>
      )}
      <AppCommandBar label={text.title}>
        {editTo && <Link to={editTo}>{state.review?.current ? text.edit : text.configure}</Link>}
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          onClick={onRefresh}
          disabled={state.stage === "loading"}
        >
          {messages(locale).refresh}
        </AppButton>
        {state.review?.current && (
          <AppButton appearance="subtle" onClick={onInspect}>
            {text.inspect}
          </AppButton>
        )}
      </AppCommandBar>
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        <form className="app-resource-filters" onSubmit={filter.submit}>
          <AppTextField
            label={text.month}
            {...filter.field}
            type="month"
            min="2024-01"
            max="2100-12"
            required
          />
          <AppButton type="submit">{text.apply}</AppButton>
        </form>
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.stage === "ready" && (
          <>
            {state.review?.current && (
              <p className="app-muted">
                {text.latest}: {state.review.current.version} · {
                  state.review.current.effectiveFrom
                }{" "}
              </p>
            )}
            {effective ? (
              <CompensationProperties view={effective} title={text.effective} locale={locale} />
            ) : (
              <p role="status">{state.review?.current ? text.noEffective : text.noCompensation}</p>
            )}
            {state.review?.current && (
              <>
                <AppResourceTable
                  title={text.history}
                  minWidth={820}
                  columns={[
                    { id: "version", label: text.revision, numeric: true },
                    { id: "from", label: text.from },
                    { id: "salary", label: text.basicSalary, numeric: true },
                    { id: "recorded", label: `${text.recorded} (${timezone})` },
                    { id: "reason", label: text.reason },
                  ]}
                  rows={history}
                  action={{ label: text.inspectRevision, onOpen: onRevision }}
                />
                <fieldset className="app-pagination" aria-label={text.historyPages}>
                  <AppButton onClick={onFirst} disabled={firstHistory}>
                    {text.first}
                  </AppButton>
                  <AppButton onClick={onNext} disabled={state.review.history.nextCursor === null}>
                    {text.next}
                  </AppButton>
                </fieldset>
              </>
            )}
          </>
        )}
      </div>
      <AppDetailsPanel
        open={selected !== null}
        title={selected?.title ?? text.revision}
        closeLabel={text.close}
        onClose={onClose}
      >
        {selected && (
          <CompensationProperties
            view={selected}
            title={selected.title}
            locale={locale}
            showHeading={false}
          />
        )}
        {evidence && <AppPropertyList title={text.evidence} items={evidence} />}
      </AppDetailsPanel>
    </section>
  );
}
