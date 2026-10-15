import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  PayrollDocumentLanguage,
  PayrollPayslipScope,
  PayrollPayslipSearch,
} from "../../domain/entities/payroll-payslip";
import type { PayrollPayslipRepository } from "../../domain/repositories/payroll-payslip-repository";
import type { PayrollPayslipDataSource } from "../datasources/payroll-payslip-data-source";
import {
  toPayrollPaymentProgress,
  toPayrollPayslip,
  toPayrollPayslipPage,
  toPayrollPayslipPdf,
} from "../mappers/payroll-payslip-mapper";

export class RemotePayrollPayslipRepository implements PayrollPayslipRepository {
  constructor(private readonly source: PayrollPayslipDataSource) {}
  list(company: CompanyId, search: PayrollPayslipSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollPayslipPage(await this.source.list(company, search, signal), company, search),
    );
  }
  get(company: CompanyId, id: string, scope: PayrollPayslipScope, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollPayslip(await this.source.get(company, id, scope, signal), company, id),
    );
  }
  payments(company: CompanyId, id: string, scope: PayrollPayslipScope, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollPaymentProgress(await this.source.payments(company, id, scope, signal), company, id),
    );
  }
  async pdf(
    company: CompanyId,
    id: string,
    scope: PayrollPayslipScope,
    revision: number,
    language: PayrollDocumentLanguage,
    signal: AbortSignal,
  ) {
    const acquired: ArrayBuffer[] = [];
    try {
      const result = await safeHttpCall(signal, async () => {
        const raw = await this.source.pdf(company, id, scope, revision, language, signal);
        acquired.push(...raw.parts);
        return toPayrollPayslipPdf(raw);
      });
      if (result.ok) acquired.length = 0;
      return result;
    } finally {
      for (const part of acquired) new Uint8Array(part).fill(0);
    }
  }
}
