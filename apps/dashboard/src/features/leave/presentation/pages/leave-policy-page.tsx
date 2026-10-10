import type { Ref } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { leavePolicyEditorMessages } from "../i18n/leave-policy-editor-messages";
import { leavePolicyMessages } from "../i18n/leave-policy-messages";
import type { LeavePolicyState } from "../models/leave-policy-state";
import type { leavePolicyRevisionView, leavePolicyView } from "../models/leave-policy-view";

export function LeavePolicyPage({
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
  onLatest,
  onOlder,
  onRevision,
}: {
  state: LeavePolicyState;
  view: ReturnType<typeof leavePolicyView> | null;
  selected: ReturnType<typeof leavePolicyRevisionView> | null;
  revisionRef: Ref<HTMLElement>;
  companyName: string;
  locale: Locale;
  timezone: string;
  backTo: string;
  editTo: string | null;
  firstHistory: boolean;
  onRefresh: () => void;
  onLatest: () => void;
  onOlder: () => void;
  onRevision: (id: string) => void;
}) {
  const text = leavePolicyMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.details}
        context={companyName}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            {editTo && <Link to={editTo}>{leavePolicyEditorMessages(locale).edit}</Link>}
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
            <AppPropertyList title={text.latest} items={view.properties} />
            <AppPropertyList title={text.terms} items={view.terms} />
            {selected ? (
              <section
                ref={revisionRef}
                tabIndex={-1}
                aria-label={selected.title}
                className="app-report-content"
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
              <AppButton onClick={onLatest} disabled={firstHistory || state.stage === "loading"}>
                {text.latestHistory}
              </AppButton>
              <AppButton
                onClick={onOlder}
                disabled={state.review?.history.nextCursor == null || state.stage === "loading"}
              >
                {text.older}
              </AppButton>
            </fieldset>
          </>
        )}
      </div>
    </section>
  );
}
