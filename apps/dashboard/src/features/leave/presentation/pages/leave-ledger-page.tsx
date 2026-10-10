import { Text } from "@fluentui/react-components";
import type { Ref } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { leaveAdjustmentMessages } from "../i18n/leave-adjustment-messages";
import { leaveBalanceMessages } from "../i18n/leave-balance-messages";
import type {
  leaveBalanceEmployeeView,
  leaveLedgerView,
  leaveMovementView,
} from "../models/leave-balance-view";
import type { LeaveLedgerState } from "../models/leave-ledger-state";

export function LeaveLedgerPage({
  state,
  view,
  employee,
  selection,
  selectionRef,
  relatedTo,
  companyName,
  timezone,
  locale,
  backTo,
  adjustTo,
  firstPage,
  onRefresh,
  onFirst,
  onOlder,
  onSelect,
}: {
  state: LeaveLedgerState;
  view: ReturnType<typeof leaveLedgerView> | null;
  employee: ReturnType<typeof leaveBalanceEmployeeView> | null;
  selection: ReturnType<typeof leaveMovementView> | null;
  selectionRef: Ref<HTMLElement>;
  relatedTo: string | null;
  companyName: string;
  timezone: string;
  locale: Locale;
  backTo: string;
  adjustTo: string | null;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onOlder: () => void;
  onSelect: (id: string) => void;
}) {
  const text = leaveBalanceMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.ledger}
        context={companyName}
        actions={
          <>
            {adjustTo && <Link to={adjustTo}>{leaveAdjustmentMessages(locale).title}</Link>}
            <Link to={backTo}>{text.back}</Link>
            <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content app-leave-details">
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {employee && <AppPropertyList title={text.employee} items={employee} />}
        {view && (
          <>
            <AppPropertyList title={text.summary} items={view.properties} />
            <Text className="app-muted">{text.labels}</Text>
            {view.entries.length === 0 ? (
              <Text role="status">{text.noEntries}</Text>
            ) : (
              <AppResourceTable
                title={`${text.entries} (${timezone})`}
                columns={[
                  { id: "date", label: text.date },
                  { id: "kind", label: text.kind },
                  { id: "available", label: text.availableDelta, numeric: true },
                  { id: "reserved", label: text.reservedDelta, numeric: true },
                  { id: "consumed", label: text.consumedDelta, numeric: true },
                ]}
                rows={view.entries}
                action={{ label: text.viewMovement, onOpen: onSelect }}
              />
            )}
            <fieldset className="app-pagination" aria-label={text.entries}>
              <AppButton onClick={onFirst} disabled={firstPage}>
                {text.latest}
              </AppButton>
              <AppButton onClick={onOlder} disabled={state.ledger?.nextCursor == null}>
                {text.older}
              </AppButton>
            </fieldset>
            {selection && (
              <section tabIndex={-1} ref={selectionRef} aria-label={text.selection}>
                <AppPropertyList title={text.movement} items={selection} />
                {relatedTo && <Link to={relatedTo}>{text.related}</Link>}
              </section>
            )}
          </>
        )}
      </div>
    </section>
  );
}
