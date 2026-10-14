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
import { payrollPolicyMessages } from "../i18n/payroll-policy-messages";
import type { PayrollPolicyState } from "../models/payroll-policy-state";
import type {
  payrollPolicyEvidenceView,
  payrollPolicyHistoryView,
  payrollPolicyView,
} from "../models/payroll-policy-view";
import { PayrollPolicyProperties } from "./payroll-policy-properties";

export function PayrollPolicyPage({
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
  firstHistory,
  onRefresh,
  onFirst,
  onNext,
  onRevision,
  onInspect,
  onClose,
}: {
  state: PayrollPolicyState;
  effective: ReturnType<typeof payrollPolicyView> | null;
  selected: ReturnType<typeof payrollPolicyView> | null;
  evidence: ReturnType<typeof payrollPolicyEvidenceView> | null;
  history: ReturnType<typeof payrollPolicyHistoryView>;
  filter: ReturnType<typeof usePayrollPolicyMonth>;
  locale: Locale;
  companyName: string;
  timezone: string;
  editTo: string | null;
  firstHistory: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onRevision: (id: string) => void;
  onInspect: () => void;
  onClose: () => void;
}) {
  const text = payrollPolicyMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader title={text.title} context={companyName} />
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
                {text.latest}: {state.review.current.version} · {state.review.current.effectiveFrom}{" "}
                – {state.review.current.effectiveUntil}
              </p>
            )}
            {effective ? (
              <PayrollPolicyProperties view={effective} title={text.effective} locale={locale} />
            ) : (
              <p role="status">{state.review?.current ? text.noEffective : text.noPolicy}</p>
            )}
            {state.review?.current && (
              <>
                <AppResourceTable
                  title={text.history}
                  minWidth={820}
                  columns={[
                    { id: "version", label: text.revision, numeric: true },
                    { id: "from", label: text.from },
                    { id: "until", label: text.until },
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
          <PayrollPolicyProperties view={selected} title={selected.title} locale={locale} />
        )}
        {evidence && <AppPropertyList title={text.evidence} items={evidence} />}
      </AppDetailsPanel>
    </section>
  );
}
