import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import type { Ref } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { roleTemplateMessages } from "../i18n/role-template-messages";
import type { RoleTemplateState } from "../models/role-template-state";
import type { roleTemplateRevisionView, roleTemplateView } from "../models/role-template-view";
import { RolePermissions } from "../widgets/role-permissions";
export function RoleTemplatePage({
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
  state: RoleTemplateState;
  view: ReturnType<typeof roleTemplateView> | null;
  selected: ReturnType<typeof roleTemplateRevisionView> | null;
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
  const text = roleTemplateMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={state.review?.current.name ?? text.details}
        context={`${companyName} / ${text.title}`}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            {editTo && <Link to={editTo}>{text.edit}</Link>}
            <AppButton
              appearance="subtle"
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
      <div className="app-report-content">
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {view && (
          <>
            <p>{text.assignmentHint}</p>
            <AppPropertyList title={text.latest} items={view.properties} />
            <RolePermissions title={text.permissions} empty={text.none} groups={view.permissions} />
            {selected && (
              <section
                ref={revisionRef}
                tabIndex={-1}
                aria-label={selected.title}
                className="app-report-content"
              >
                <h2>{selected.title}</h2>
                <AppPropertyList title={text.evidence} items={selected.properties} />
                <RolePermissions
                  title={text.permissions}
                  empty={text.none}
                  groups={selected.permissions}
                />
              </section>
            )}
            <AppResourceTable
              title={text.history}
              columns={[
                { id: "revision", label: text.revision, numeric: true },
                { id: "name", label: text.name },
                { id: "status", label: text.status },
                { id: "recorded", label: `${text.recorded} (${timezone})` },
                { id: "reason", label: text.reason },
              ]}
              rows={view.history}
              action={{ label: text.inspect, onOpen: onRevision }}
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
