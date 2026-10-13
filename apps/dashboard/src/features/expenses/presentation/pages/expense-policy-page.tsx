import type { Ref } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { expensePolicyEditorMessages } from "../i18n/expense-policy-editor-messages";
import { expensePolicyMessages } from "../i18n/expense-policy-messages";
import type { ExpensePolicyState } from "../models/expense-policy-state";
import type { expensePolicyRevisionView, expensePolicyView } from "../models/expense-policy-view";

export function ExpensePolicyPage({
  state,
  view,
  selected,
  revisionRef,
  companyName,
  locale,
  timezone,
  backTo,
  editTo,
  firstHistory,
  onRefresh,
  onFirst,
  onNext,
  onRevision,
}: {
  state: ExpensePolicyState;
  view: ReturnType<typeof expensePolicyView> | null;
  selected: ReturnType<typeof expensePolicyRevisionView> | null;
  revisionRef: Ref<HTMLElement>;
  companyName: string;
  locale: Locale;
  timezone: string;
  backTo: string;
  editTo: string | null;
  firstHistory: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onRevision: (id: string) => void;
}) {
  const text = expensePolicyMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.details}
        context={companyName}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            {editTo && <Link to={editTo}>{expensePolicyEditorMessages(locale).edit}</Link>}
            <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {view && (
          <>
            <p className="app-muted">{text.selection}</p>
            <AppPropertyList title={text.latest} items={view.properties} />
            <AppPropertyList title={text.terms} items={view.terms} />
            {selected ? (
              <section
                ref={revisionRef}
                tabIndex={-1}
                aria-label={selected.title}
                className="app-report-content app-expense-policy-revision"
              >
                <h2>{selected.title}</h2>
                <AppPropertyList title={text.terms} items={selected.terms} />
                <AppPropertyList title={text.evidence} items={selected.evidence} />
              </section>
            ) : (
              <p>{text.choose}</p>
            )}
            <AppResourceTable
              title={text.history}
              minWidth={900}
              columns={[
                { id: "version", label: text.revision, numeric: true },
                { id: "from", label: text.from },
                { id: "active", label: text.active },
                { id: "recorded", label: `${text.recorded} (${timezone})` },
                { id: "actor", label: text.actor },
                { id: "reason", label: text.reason },
              ]}
              rows={view.history}
              action={{ label: text.view, onOpen: onRevision }}
            />
            <fieldset className="app-pagination" aria-label={text.historyPages}>
              <AppButton onClick={onFirst} disabled={firstHistory || state.stage === "loading"}>
                {text.first}
              </AppButton>
              <AppButton
                onClick={onNext}
                disabled={state.review?.history.nextCursor == null || state.stage === "loading"}
              >
                {text.next}
              </AppButton>
            </fieldset>
          </>
        )}
      </div>
    </section>
  );
}
