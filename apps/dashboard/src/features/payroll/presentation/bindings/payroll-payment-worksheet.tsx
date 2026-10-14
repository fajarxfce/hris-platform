import { useEffect, useMemo, useSyncExternalStore } from "react";
import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentReview } from "../../domain/entities/payroll-payment-review";
import { PayrollPaymentDownload } from "../components/payroll-payment-download";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollPaymentDownloadController } from "../controllers/payroll-payment-download-controller";
export function PayrollPaymentWorksheet(props: {
  download: PayrollUseCases["downloadPayment"];
  access: CompanyAccess;
  review: PayrollPaymentReview;
  locale: Locale;
  onUnavailable: (review: PayrollPaymentReview, failure: Failure) => void;
}) {
  const controller = useMemo(
    () => new PayrollPaymentDownloadController(props.download, props.access, props.review),
    [props.download, props.access, props.review],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useEffect(() => {
    if (state.stage === "unavailable" && state.failure)
      props.onUnavailable(props.review, state.failure);
  }, [state.stage, state.failure, props.onUnavailable, props.review]);
  return (
    <PayrollPaymentDownload
      state={state}
      locale={props.locale}
      onDownload={controller.open}
      onCancel={controller.cancel}
    />
  );
}
