import type { AccountId, CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MfaEnrollment, MfaVerification } from "../../domain/entities/mfa";
import type { CompanyAccess, Session } from "../../domain/entities/session";
import type { IdentityProvider } from "../../domain/entities/sign-in";
import type {
  CompanyAccessDto,
  IdentityProvidersDto,
  MfaEnrollmentDto,
  MfaVerificationDto,
  SessionDto,
} from "../models/identity-dto";

export const toSession = (dto: SessionDto): Session =>
  Object.freeze({
    account: Object.freeze({ ...dto.account, id: dto.account.id.toLowerCase() as AccountId }),
    companies: Object.freeze(
      dto.companies.map((company) =>
        Object.freeze({ ...company, id: company.id.toLowerCase() as CompanyId }),
      ),
    ),
    permissions: Object.freeze([...dto.permissions]),
    assurance: Object.freeze({ ...dto.assurance }),
  });

export const toCompanyAccess = (dto: CompanyAccessDto): CompanyAccess =>
  Object.freeze({
    companyId: dto.companyId.toLowerCase() as CompanyId,
    permissions: Object.freeze([...dto.permissions]),
  });
export const toIdentityProviders = (dto: IdentityProvidersDto): readonly IdentityProvider[] => dto;
export const toMfaEnrollment = (dto: MfaEnrollmentDto): MfaEnrollment => ({
  ...dto,
  operationId: dto.operationId as OperationId,
});
export const toMfaVerification = (dto: MfaVerificationDto): MfaVerification => dto;
