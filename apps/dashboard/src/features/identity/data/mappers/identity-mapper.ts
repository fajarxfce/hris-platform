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

export const toSession = (dto: SessionDto): Session => ({
  account: { ...dto.account, id: dto.account.id as AccountId },
  companies: dto.companies.map((company) => ({ ...company, id: company.id as CompanyId })),
  permissions: dto.permissions,
  assurance: dto.assurance,
});

export const toCompanyAccess = (dto: CompanyAccessDto): CompanyAccess => ({
  companyId: dto.companyId as CompanyId,
  permissions: dto.permissions,
});
export const toIdentityProviders = (dto: IdentityProvidersDto): readonly IdentityProvider[] => dto;
export const toMfaEnrollment = (dto: MfaEnrollmentDto): MfaEnrollment => ({
  ...dto,
  operationId: dto.operationId as OperationId,
});
export const toMfaVerification = (dto: MfaVerificationDto): MfaVerification => dto;
