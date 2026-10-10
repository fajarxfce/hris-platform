import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { organizationMessages } from "../i18n/organization-messages";
import type { OrganizationUnitState } from "../models/organization-unit-state";
import type { organizationUnitView } from "../models/organization-view";

export function OrganizationUnitPage({
  state,
  view,
  companyName,
  locale,
  backTo,
  parentTo,
  editTo,
  onRefresh,
}: {
  state: OrganizationUnitState;
  view: {
    unit: ReturnType<typeof organizationUnitView>;
    parent: ReturnType<typeof organizationUnitView> | null;
  } | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  parentTo: string | null;
  editTo: string | null;
  onRefresh: () => void;
}) {
  const text = organizationMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={state.details?.unit.name ?? text.details}
        context={`${companyName} / ${text.title}`}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            {editTo && <Link to={editTo}>{text.edit}</Link>}
            <AppButton
              icon={<ArrowClockwise20Regular />}
              disabled={state.stage === "loading"}
              onClick={onRefresh}
            >
              {shared.refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {view && (
        <div className="app-report-content">
          <AppPropertyList title={text.overview} items={view.unit} />
          {view.parent ? (
            <>
              <AppPropertyList title={text.parent} items={view.parent} />
              {parentTo && <Link to={parentTo}>{text.openParent}</Link>}
            </>
          ) : (
            <Text role="status">{text.noParent}</Text>
          )}
        </div>
      )}
    </section>
  );
}
