import type { Failure } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  PayrollDocumentLanguage,
  PayrollPayslipScope,
} from "../../domain/entities/payroll-payslip";
import { payrollPayslipAccessLost } from "../../domain/policies/payroll-payslip-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollPayslipState,
  type PayrollPayslipState,
} from "../models/payroll-payslip-state";

export class PayrollPayslipController {
  #state = initialPayrollPayslipState;
  #active = false;
  #detail: AbortController | null = null;
  #payments: AbortController | null = null;
  #download: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly payroll: Pick<
      PayrollUseCases,
      "loadPayslip" | "loadPayslipPayments" | "downloadPayslip"
    >,
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly scope: PayrollPayslipScope,
  ) {}
  getSnapshot = (): PayrollPayslipState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    void this.refresh();
  };
  deactivate = (): void => {
    this.#active = false;
    this.#detail?.abort();
    this.#payments?.abort();
    this.#download?.abort();
    this.#detail = null;
    this.#payments = null;
    this.#download = null;
    this.publish(initialPayrollPayslipState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#detail?.abort();
    this.#payments?.abort();
    this.#download?.abort();
    this.#payments = null;
    this.#download = null;
    const pending = new AbortController();
    this.#detail = pending;
    this.publish({ ...initialPayrollPayslipState, documentLanguage: this.#state.documentLanguage });
    try {
      const result = await this.payroll.loadPayslip.execute(
        this.access,
        this.id,
        this.scope,
        pending.signal,
      );
      if (pending.signal.aborted || this.#detail !== pending) return;
      if (!result.ok) {
        this.reject(result.failure);
        return;
      }
      this.publish({ ...this.#state, stage: "ready", payslip: result.value });
      void this.refreshPayments();
    } catch {
      if (!pending.signal.aborted && this.#detail === pending)
        this.reject({ code: "unexpected_error", fields: {}, parameters: {} });
    } finally {
      if (this.#detail === pending) this.#detail = null;
    }
  };
  refreshPayments = async (): Promise<void> => {
    if (!this.#active || this.#state.stage !== "ready") return;
    this.#payments?.abort();
    const pending = new AbortController();
    this.#payments = pending;
    this.publish({ ...this.#state, paymentsLoading: true, paymentFailure: null });
    try {
      const result = await this.payroll.loadPayslipPayments.execute(
        this.access,
        this.id,
        this.scope,
        pending.signal,
      );
      if (pending.signal.aborted || this.#payments !== pending) return;
      if (!result.ok && payrollPayslipAccessLost(result.failure)) {
        this.reject(result.failure);
        return;
      }
      this.publish({
        ...this.#state,
        paymentsLoading: false,
        payments: result.ok ? result.value : null,
        paymentFailure: result.ok ? null : result.failure,
      });
    } catch {
      if (!pending.signal.aborted && this.#payments === pending)
        this.publish({
          ...this.#state,
          paymentsLoading: false,
          payments: null,
          paymentFailure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#payments === pending) this.#payments = null;
    }
  };
  selectDocumentLanguage = (language: PayrollDocumentLanguage): void => {
    if (!this.#active || this.#download || (language !== "ID" && language !== "EN")) return;
    this.publish({
      ...this.#state,
      documentLanguage: language,
      download: "idle",
      downloadFailure: null,
    });
  };
  download = async (): Promise<void> => {
    const payslip = this.#state.payslip;
    if (!this.#active || this.#state.stage !== "ready" || !payslip || this.#download) return;
    const pending = new AbortController();
    this.#download = pending;
    this.publish({ ...this.#state, download: "saving", downloadFailure: null });
    try {
      const result = await this.payroll.downloadPayslip.execute(
        this.access,
        this.id,
        this.scope,
        payslip.summary.version,
        this.#state.documentLanguage,
        pending.signal,
      );
      if (pending.signal.aborted || this.#download !== pending) return;
      if (!result.ok && payrollPayslipAccessLost(result.failure)) {
        this.reject(result.failure);
        return;
      }
      this.publish({
        ...this.#state,
        download: result.ok ? "saved" : "failed",
        downloadFailure: result.ok ? null : result.failure,
      });
    } catch {
      if (!pending.signal.aborted && this.#download === pending)
        this.publish({
          ...this.#state,
          download: "failed",
          downloadFailure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#download === pending) this.#download = null;
    }
  };
  private reject(failure: Failure): void {
    this.#detail?.abort();
    this.#payments?.abort();
    this.#download?.abort();
    this.#detail = null;
    this.#payments = null;
    this.#download = null;
    this.publish({
      ...initialPayrollPayslipState,
      stage: "unavailable",
      failure,
      documentLanguage: this.#state.documentLanguage,
    });
  }
  private publish(state: PayrollPayslipState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
