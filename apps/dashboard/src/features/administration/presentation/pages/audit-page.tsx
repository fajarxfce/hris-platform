import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
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
import type { useAuditFilters } from "../controllers/use-audit-filters";
import { administrationMessages } from "../i18n/administration-messages";
import type { AuditState } from "../models/audit-state";
import type { AuditEventView, AuditPageView } from "../models/audit-view";

export function AuditPage({
  state,
  view,
  event,
  filters,
  companyName,
  locale,
  hasCursor,
  onRefresh,
  onReset,
  onFirst,
  onOlder,
  onOpen,
  onClose,
}: {
  state: AuditState;
  view: AuditPageView | null;
  event: AuditEventView | null;
  filters: ReturnType<typeof useAuditFilters>;
  companyName: string;
  locale: Locale;
  hasCursor: boolean;
  onRefresh: () => void;
  onReset: () => void;
  onFirst: () => void;
  onOlder: () => void;
  onOpen: (id: string) => void;
  onClose: () => void;
}) {
  const text = administrationMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.audit}
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
      <form onSubmit={filters.apply} noValidate>
        <AppCommandBar label={text.filters}>
          <div className="app-audit-filters">
            <AppTextField label={text.from} type="datetime-local" step="1" {...filters.from} />
            <AppTextField label={text.until} type="datetime-local" step="1" {...filters.until} />
            <AppTextField label={text.action} maxLength={100} {...filters.action} />
            <AppTextField label={text.resourceType} maxLength={80} {...filters.resourceType} />
            <AppTextField label={text.actor} maxLength={36} {...filters.actorId} />
            <AppTextField label={text.resourceId} maxLength={36} {...filters.resourceId} />
          </div>
          <div className="app-filter-actions">
            <Text className="app-muted">
              {text.defaultWindow} {text.rangeHint}
            </Text>
            <AppButton type="submit" appearance="primary">
              {text.apply}
            </AppButton>
            <AppButton onClick={onReset}>{text.reset}</AppButton>
          </div>
          {filters.error && <p role="alert">{filters.error}</p>}
        </AppCommandBar>
      </form>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {view && (
        <div className="app-report-content">
          <Text className="app-muted">{view.window}</Text>
          {view.rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <AppResourceTable
              title={text.events}
              columns={[
                { id: "time", label: text.time },
                { id: "action", label: text.action },
                { id: "resource", label: text.resource },
                { id: "actor", label: text.actor },
              ]}
              rows={view.rows}
              action={{ label: text.details, onOpen }}
            />
          )}
          <fieldset className="app-pagination">
            <legend className="app-visually-hidden">{text.pagination}</legend>
            <AppButton onClick={onFirst} disabled={!hasCursor}>
              {text.first}
            </AppButton>
            <AppButton onClick={onOlder} disabled={state.page?.nextCursor == null}>
              {text.older}
            </AppButton>
          </fieldset>
        </div>
      )}
      <AppDetailsPanel
        open={event !== null}
        title={text.event}
        closeLabel={text.close}
        onClose={onClose}
      >
        {event && <AppPropertyList title={text.metadata} items={event} />}
      </AppDetailsPanel>
    </section>
  );
}
