import { Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useLeaveBalanceFilters } from "../controllers/use-leave-balance-filters";
import { leaveAdjustmentMessages } from "../i18n/leave-adjustment-messages";
import { leaveBalanceMessages } from "../i18n/leave-balance-messages";
import type { leaveBalanceEmployeeView, leaveBalancesView } from "../models/leave-balance-view";
import type { LeaveBalancesState } from "../models/leave-balances-state";

export function LeaveBalancesPage({
  state,
  rows,
  employee,
  companyName,
  locale,
  filters,
  requestsTo,
  adjustTo,
  firstPage,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: LeaveBalancesState;
  rows: ReturnType<typeof leaveBalancesView>;
  employee: ReturnType<typeof leaveBalanceEmployeeView> | null;
  companyName: string;
  locale: Locale;
  filters: ReturnType<typeof useLeaveBalanceFilters>;
  requestsTo: string;
  adjustTo: string | null;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = leaveBalanceMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <>
            {adjustTo && <Link to={adjustTo}>{leaveAdjustmentMessages(locale).title}</Link>}
            <Link to={requestsTo}>{text.requests}</Link>
            <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content app-leave-details">
        <form className="app-resource-filters" aria-label={text.filters} onSubmit={filters.submit}>
          <AppTextField
            label={text.year}
            inputMode="numeric"
            maxLength={4}
            required
            {...filters.year}
          />
          <AppButton type="submit">{text.apply}</AppButton>
        </form>
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {employee && <AppPropertyList title={text.employee} items={employee} />}
        {state.page &&
          (rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <>
              <AppResourceTable
                title={text.title}
                columns={[
                  { id: "code", label: text.code },
                  { id: "type", label: text.type },
                  { id: "available", label: text.available, numeric: true },
                  { id: "reserved", label: text.reserved, numeric: true },
                  { id: "consumed", label: text.consumed, numeric: true },
                  { id: "status", label: text.status },
                ]}
                rows={rows}
                action={{ label: text.view, onOpen }}
              />
              <Text className="app-muted">{text.labels}</Text>
            </>
          ))}
        <fieldset className="app-pagination" aria-label={text.pages}>
          <AppButton onClick={onFirst} disabled={firstPage || state.stage === "loading"}>
            {text.first}
          </AppButton>
          <AppButton
            onClick={onNext}
            disabled={state.page?.nextCursor == null || state.stage === "loading"}
          >
            {text.next}
          </AppButton>
        </fieldset>
      </div>
    </section>
  );
}
