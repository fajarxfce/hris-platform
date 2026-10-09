import { lazy, Suspense } from "react";
import { Link, Route, Routes } from "react-router-dom";
import type { AccountId } from "../../core/domain/identifiers";
import { AppLoading } from "../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../core/presentation/components/app-page-header";
import { type Locale, messages } from "../../core/presentation/i18n/messages";
import type {
  CompanyAccess,
  CompanyMembership,
} from "../../features/identity/domain/entities/session";
import { OverviewPage } from "../../features/overview/presentation/pages/overview-page";
import type { ReportingUseCases } from "../../features/reporting/presentation/contracts/reporting-use-cases";

const HeadcountScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/headcount-screen").then((module) => ({
    default: module.HeadcountScreen,
  })),
);

export function PortalRoutes({
  accountId,
  company,
  companies,
  access,
  reporting,
  locale,
}: {
  accountId: AccountId;
  company: CompanyMembership | null;
  companies: readonly CompanyMembership[];
  access: CompanyAccess | null;
  reporting: ReportingUseCases;
  locale: Locale;
}) {
  if (!company || !access) return <OverviewPage company={null} locale={locale} />;
  return (
    <Routes>
      <Route path="/" element={<OverviewPage company={company} locale={locale} />} />
      <Route
        path="/reports/headcount"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <HeadcountScreen
              accountId={accountId}
              access={access}
              loadHeadcount={reporting.loadHeadcount}
              companies={companies}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="*"
        element={
          <>
            <AppPageHeader title={messages(locale).pageNotFound} />
            <Link to="/">{messages(locale).overview}</Link>
          </>
        }
      />
    </Routes>
  );
}
