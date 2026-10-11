import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { employmentTransferMessages } from "../i18n/employment-transfer-messages";
import { peopleMessages } from "../i18n/people-messages";
import type { EmploymentTransfersState } from "../models/employment-transfers-state";
import type { employmentTransfersView } from "../models/employment-transfers-view";

export function EmploymentTransfersPanel({
  state,
  rows,
  locale,
  onRefresh,
}: {
  state: EmploymentTransfersState;
  rows: ReturnType<typeof employmentTransfersView>;
  locale: Locale;
  onRefresh: () => void;
}) {
  const text = employmentTransferMessages(locale);
  const shared = messages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <div className="app-filter-actions">
        <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
          {shared.refresh}
        </AppButton>
      </div>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {state.items &&
        (rows.length === 0 ? (
          <p role="status">{text.empty}</p>
        ) : (
          <AppResourceTable
            title={text.history}
            rows={rows}
            columns={[
              { id: "direction", label: text.direction },
              { id: "company", label: text.company },
              { id: "date", label: text.date },
              { id: "reason", label: peopleMessages(locale).reason },
            ]}
          />
        ))}
    </section>
  );
}
