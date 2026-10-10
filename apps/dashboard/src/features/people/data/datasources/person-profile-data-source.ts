import type {
  PersonAccountBindingDto,
  PersonProfileChangeDto,
  PersonProfileDto,
  PersonProfileHistoryDto,
  PersonProfileReceiptDto,
} from "../models/person-profile-dto";

export interface PersonProfileDataSource {
  bindAccount(
    companyId: string,
    employeeId: string,
    operation: string,
    input: PersonAccountBindingDto,
    signal: AbortSignal,
  ): Promise<PersonProfileReceiptDto>;
  get(companyId: string, employeeId: string, signal: AbortSignal): Promise<PersonProfileDto>;
  history(
    companyId: string,
    employeeId: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<PersonProfileHistoryDto>;
  save(
    companyId: string,
    employeeId: string,
    operation: string,
    change: PersonProfileChangeDto,
    signal: AbortSignal,
  ): Promise<PersonProfileReceiptDto>;
}
