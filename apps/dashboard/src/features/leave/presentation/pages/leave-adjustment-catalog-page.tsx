import { Text } from "@fluentui/react-components";
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
import { leavePolicyMessages } from "../i18n/leave-policy-messages";
import type { LeaveAdjustmentCatalogState } from "../models/leave-adjustment-catalog-state";
import type { leaveBalanceEmployeeView } from "../models/leave-balance-view";
import type { leavePoliciesView } from "../models/leave-policy-view";

export function LeaveAdjustmentCatalogPage({
  state,
  rows,
  employee,
  companyName,
  locale,
  backTo,
  firstPage,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: LeaveAdjustmentCatalogState;
  rows: ReturnType<typeof leavePoliciesView>;
  employee: ReturnType<typeof leaveBalanceEmployeeView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = leaveAdjustmentMessages(locale);
  const balance = leaveBalanceMessages(locale);
  const policy = leavePolicyMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.choose}
        context={companyName}
        actions={
          <>
            <Link to={backTo}>{balance.back}</Link>
            <AppButton onClick={onRefresh} disabled={state.stage === "loading"}>
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content app-leave-details">
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {employee && <AppPropertyList title={balance.employee} items={employee} />}
        {state.catalog && (
          <>
            <Text className="app-muted">{text.catalogHint}</Text>
            {rows.length === 0 ? (
              <Text role="status">{text.empty}</Text>
            ) : (
              <AppResourceTable
                title={text.catalog}
                columns={[
                  { id: "code", label: policy.code },
                  { id: "name", label: policy.name },
                  { id: "from", label: policy.from },
                  { id: "active", label: policy.active },
                  { id: "version", label: policy.version },
                ]}
                rows={rows}
                action={{ label: text.review, onOpen }}
              />
            )}
          </>
        )}
        <fieldset className="app-pagination" aria-label={text.catalog}>
          <AppButton onClick={onFirst} disabled={firstPage || state.stage === "loading"}>
            {balance.first}
          </AppButton>
          <AppButton
            onClick={onNext}
            disabled={state.catalog?.policies.nextCursor == null || state.stage === "loading"}
          >
            {balance.next}
          </AppButton>
        </fieldset>
      </div>
    </section>
  );
}
