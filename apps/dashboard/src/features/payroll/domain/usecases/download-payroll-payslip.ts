import type { FileRepository } from "../../../../core/domain/files/file-repository";
import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollDocumentLanguage, PayrollPayslipScope } from "../entities/payroll-payslip";
import {
  canReadPayrollPayslips,
  validPayrollPayslipScope,
} from "../policies/payroll-payslip-policy";
import type { PayrollPayslipRepository } from "../repositories/payroll-payslip-repository";

export class DownloadPayrollPayslip {
  constructor(
    private readonly payslips: PayrollPayslipRepository,
    private readonly files: Pick<FileRepository, "downloadBinary">,
  ) {}
  async execute(
    access: CompanyAccess,
    id: string,
    scope: PayrollPayslipScope,
    language: PayrollDocumentLanguage,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadPayrollPayslips(access.permissions)) return failed("access_denied");
    if (!isUuid(id) || !validPayrollPayslipScope(scope)) return failed("payroll_payslip_not_found");
    if (language !== "ID" && language !== "EN") return failed("invalid_document_language");
    const content = await this.payslips.pdf(
      access.companyId,
      id.toLowerCase(),
      scope,
      language,
      signal,
    );
    if (!content.ok) {
      signal.throwIfAborted();
      return content;
    }
    try {
      signal.throwIfAborted();
      return await this.files.downloadBinary(
        Object.freeze({
          ...content.value,
          name: `payslip-${id.toLowerCase()}-${language.toLowerCase()}.pdf`,
          mediaType: "application/pdf",
        }),
        signal,
      );
    } finally {
      for (const part of content.value.parts) new Uint8Array(part).fill(0);
    }
  }
}
