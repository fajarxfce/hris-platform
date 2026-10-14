import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentReview } from "../../domain/entities/payroll-payment-review";
import { payrollPaymentEvidenceUnavailable } from "../../domain/policies/payroll-payment-failure-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollPaymentDownload,
  type PayrollPaymentDownloadState,
} from "../models/payroll-payment-download-state";
export class PayrollPaymentDownloadController {
  #active = false;
  #pending: AbortController | null = null;
  #state = initialPayrollPaymentDownload;
  #listeners = new Set<() => void>();
  constructor(
    private readonly download: PayrollUseCases["downloadPayment"],
    private readonly access: CompanyAccess,
    private readonly review: PayrollPaymentReview,
  ) {}
  getSnapshot = (): PayrollPaymentDownloadState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    this.#active = true;
  };
  deactivate = (): void => {
    this.#active = false;
    this.cancel();
  };
  cancel = (): void => {
    this.#pending?.abort();
    this.#pending = null;
    this.publish(initialPayrollPaymentDownload);
  };
  open = async (): Promise<void> => {
    if (
      !this.#active ||
      this.#pending ||
      this.#state.stage === "unavailable" ||
      !this.review.availableActions.includes("EXPORT")
    )
      return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "downloading", failure: null });
    try {
      const result = await this.download.execute(this.access, this.review, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "started", failure: null }
          : {
              stage:
                payrollPaymentEvidenceUnavailable(result.failure) ||
                [
                  "payroll_payment_batch_not_found",
                  "payroll_payment_instructions_unavailable",
                ].includes(result.failure.code)
                  ? "unavailable"
                  : "failed",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "failed",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: PayrollPaymentDownloadState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
