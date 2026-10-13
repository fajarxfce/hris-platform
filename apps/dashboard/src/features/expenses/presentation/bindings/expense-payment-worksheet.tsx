import { useEffect, useMemo, useSyncExternalStore } from "react";
import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentReview } from "../../domain/entities/expense-payment-review";
import { ExpensePaymentDownload } from "../components/expense-payment-download";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { ExpensePaymentDownloadController } from "../controllers/expense-payment-download-controller";
export function ExpensePaymentWorksheet(props: {
  download: ExpenseUseCases["downloadPayment"];
  access: CompanyAccess;
  review: ExpensePaymentReview;
  locale: Locale;
  onUnavailable: (review: ExpensePaymentReview, failure: Failure) => void;
}) {
  const controller = useMemo(
    () => new ExpensePaymentDownloadController(props.download, props.access, props.review),
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
    <ExpensePaymentDownload
      state={state}
      locale={props.locale}
      onDownload={controller.open}
      onCancel={controller.cancel}
    />
  );
}
