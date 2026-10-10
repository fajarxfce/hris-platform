import { Text } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { leaveStatuses } from "../../domain/entities/leave-request";
import type { useLeaveFilters } from "../controllers/use-leave-filters";
import { leaveMessages } from "../i18n/leave-messages";
import type { leaveRequestsView } from "../models/leave-request-view";
import type { LeaveRequestsState } from "../models/leave-requests-state";

export function LeaveRequestsPage({
  state,
  rows,
  companyName,
  locale,
  filters,
  firstPage,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: LeaveRequestsState;
  rows: ReturnType<typeof leaveRequestsView>;
  companyName: string;
  locale: Locale;
  filters: ReturnType<typeof useLeaveFilters>;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = leaveMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.requests}
        context={companyName}
        actions={
          <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
            {messages(locale).refresh}
          </AppButton>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        <form className="app-resource-filters" aria-label={text.filters} onSubmit={filters.submit}>
          <AppSelect label={text.status} {...filters.status}>
            <option value="">{text.all}</option>
            {leaveStatuses.map((status) => (
              <option key={status} value={status}>
                {text[status]}
              </option>
            ))}
          </AppSelect>
          <AppButton type="submit">{text.apply}</AppButton>
        </form>
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.page &&
          (rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <AppResourceTable
              title={text.requests}
              columns={[
                { id: "employee", label: text.employee },
                { id: "number", label: text.number },
                { id: "type", label: text.type },
                { id: "from", label: text.from },
                { id: "until", label: text.until },
                { id: "days", label: text.days, numeric: true },
                { id: "status", label: text.status },
              ]}
              rows={rows}
              action={{ label: text.view, onOpen }}
            />
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
