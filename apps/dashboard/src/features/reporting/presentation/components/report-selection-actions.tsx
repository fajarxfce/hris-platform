import { Link } from "react-router-dom";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { reportExportMessages } from "../i18n/report-export-messages";
import { savedReportFilterMessages } from "../i18n/saved-report-filter-messages";
import { exportAppliedReportPath } from "../models/report-export-route";
export function ReportSelectionActions({
  savedFilterTo,
  locale,
}: {
  savedFilterTo: string;
  locale: Locale;
}) {
  const exportTo = exportAppliedReportPath(savedFilterTo);
  return (
    <>
      <Link to={savedFilterTo}>{savedReportFilterMessages(locale).saveApplied}</Link>
      {exportTo && <Link to={exportTo}>{reportExportMessages(locale).create}</Link>}
    </>
  );
}
