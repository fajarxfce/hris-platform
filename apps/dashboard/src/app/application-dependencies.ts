import type { AdministrationUseCases } from "../features/administration/presentation/contracts/administration-use-cases";
import type { ApprovalsUseCases } from "../features/approvals/presentation/contracts/approvals-use-cases";
import type { IdentityController } from "../features/identity/presentation/controllers/identity-controller";
import type { JobsUseCases } from "../features/jobs/presentation/contracts/jobs-use-cases";
import type { LeaveUseCases } from "../features/leave/presentation/contracts/leave-use-cases";
import type { LifecycleUseCases } from "../features/lifecycle/presentation/contracts/lifecycle-use-cases";
import type { OrganizationUseCases } from "../features/organization/presentation/contracts/organization-use-cases";
import type { PeopleUseCases } from "../features/people/presentation/contracts/people-use-cases";
import type { ReportingUseCases } from "../features/reporting/presentation/contracts/reporting-use-cases";

export type ApplicationDependencies = Readonly<{
  clientBuild: number;
  identity: IdentityController;
  reporting: ReportingUseCases;
  administration: AdministrationUseCases;
  approvals: ApprovalsUseCases;
  jobs: JobsUseCases;
  leave: LeaveUseCases;
  organization: OrganizationUseCases;
  people: PeopleUseCases;
  lifecycle: LifecycleUseCases;
  nextIdentifier: () => string;
}>;
